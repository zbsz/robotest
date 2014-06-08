package org.scalatest

import org.robolectric._
import org.robolectric.bytecode._
import org.robolectric.res._
import org.robolectric.internal.{RoboTestUniverse, ParallelUniverseInterface}

import org.robolectric.util.Util

import java.net.URL
import org.apache.maven.artifact.ant.{RemoteRepository, DependenciesTask}
import org.apache.tools.ant.Project
import org.apache.maven.model.Dependency
import org.fest.reflect.core.Reflection._
import android.os.Build
import org.robolectric.annotation.Config

/**
 * Enables Robolectric shadow implementations for Android stubs.
 *
 * This works similarly to RobolectricTestRunner class:
 * - prepares Robolectric class loader (uses some magic copied from RobolectricTestRunner)
 * - loads current suite in Robolectric loader
 * - executes tests in shadowed suite
 *
 * XXX: this implementation doesn't support AndroidManifest and resources, so is only usable fo minimal set of tests
 */
trait RobolectricSuite extends SuiteMixin { self: Suite =>

  def useInstrumentation(name: String): Option[Boolean] = None

  abstract override def run(testName: Option[String], args: Args): Status =
    new RoboSuiteRunner(useInstrumentation).run(this.getClass, testName, args)

  def runShadow(testName: Option[String], args: Args): Status = super.run(testName, args)
}

class RoboSuiteRunner(shouldAcquire: String => Option[Boolean]) { runner =>

  val sdkConfig = new SdkConfig(Build.VERSION_CODES.JELLY_BEAN_MR2)
  val urls = MavenCentral.getLocalArtifactUrls(sdkConfig.getSdkClasspathDependencies: _*).values.toArray
  val shadowMap = new ShadowMap.Builder().build()

  val classHandler = new ShadowWrangler(shadowMap, sdkConfig)
  val classLoader = {
    val setup = new Setup() {
      override def shouldAcquire(name: String): Boolean = {
        name != classOf[RoboSuiteRunner].getName &&
          !name.startsWith("org.scala") &&
          runner.shouldAcquire(name).getOrElse(super.shouldAcquire(name))
      }
    }
    setup.getClassesToDelegateFromRcl.add(classOf[RoboSuiteRunner].getName)
    new AsmInstrumentingClassLoader(setup, urls: _*)
  }

  val sdkEnvironment = {
    val env = new SdkEnvironment(sdkConfig, classLoader)
    env.setCurrentClassHandler(classHandler)

    val className = classOf[RobolectricInternals].getName
    val robolectricInternalsClass = classLoader.loadClass(className)
    val field = robolectricInternalsClass.getDeclaredField("classHandler")
    field.setAccessible(true)
    field.set(null, classHandler)

    val sdkVersion = Build.VERSION_CODES.JELLY_BEAN_MR2
    val versionClass = env.bootstrappedClass(classOf[Build.VERSION])
    staticField("SDK_INT").ofType(classOf[Int]).in(versionClass).set(sdkVersion)

    env
  }

  def run(suiteClass: Class[_ <: RobolectricSuite], testName: Option[String], args: Args): Status = {
    val shadowSuite = classLoader.loadClass(suiteClass.getName).newInstance.asInstanceOf[RobolectricSuite]
    setupAndroidEnvironmentForTest(args)
    shadowSuite.runShadow(testName, args)
  }

  def setupAndroidEnvironmentForTest(args: Args) = {
    val resourceLoader = {
      val url = MavenCentral.getLocalArtifactUrl(sdkConfig.getSystemResourceDependency)
      val systemResFs = Fs.fromJar(url)
      val resourceExtractor = new ResourceExtractor(classLoader)
      val resourcePath = new ResourcePath(resourceExtractor.getProcessedRFile, resourceExtractor.getPackageName, systemResFs.join("res"), systemResFs.join("assets"))
      new PackageResourceLoader(resourcePath, resourceExtractor)
    }

    val parallelUniverse = {
      val universe = classLoader.loadClass(classOf[RoboTestUniverse].getName).asInstanceOf[Class[ParallelUniverseInterface]].newInstance()
      universe.setSdkConfig(sdkConfig)
      universe
    }

    parallelUniverse.resetStaticState()

    val testLifecycle = classLoader.loadClass(classOf[DefaultTestLifecycle].getName).newInstance.asInstanceOf[TestLifecycle[_]]
    val strictI18n = Option(System.getProperty("robolectric.strictI18n")).exists(_.toBoolean)

    parallelUniverse.setUpApplicationState(null, testLifecycle, strictI18n, resourceLoader, null, new Config.Implementation(1, "", "", "", 1, Array()))
  }

  object MavenCentral {
    private final val project = new Project

    def getLocalArtifactUrls(dependencies: Dependency*): Map[String, URL] = {
      val dependenciesTask = new DependenciesTask
      val sonatypeRepository = new RemoteRepository
      sonatypeRepository.setUrl("https://oss.sonatype.org/content/groups/public/")
      sonatypeRepository.setId("sonatype")
      dependenciesTask.addConfiguredRemoteRepository(sonatypeRepository)
      dependenciesTask.setProject(project)
      for (dependency <- dependencies) {
        dependenciesTask.addDependency(dependency)
      }
      dependenciesTask.execute()
      import scala.collection.JavaConverters._
      project.getProperties.entrySet().asScala.map(e => e.getKey.asInstanceOf[String] -> Util.url(e.getValue.asInstanceOf[String])).toMap
    }

    def getLocalArtifactUrl(dependency: Dependency): URL =
      getLocalArtifactUrls(dependency)(dependency.getGroupId + ":" + dependency.getArtifactId + ":" + dependency.getType)
  }
}
