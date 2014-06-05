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
 * - executes test in shadowed suite
 *
 * XXX: this implementation doesn't support AndroidManifest and resources, so is only usable fo minimal set of tests
 * XXX: whole suite is loaded on robolectric class loader, this can cause some undesired effects, when our testcode is replaced with robo shadows (for example httpClient implementation)
 */
trait RobolectricTests extends SuiteMixin { self: Suite =>
  import RobolectricTests._

  lazy val shadowSuite = classLoader.loadClass(this.getClass.getName).newInstance.asInstanceOf[RobolectricTests]

  abstract override def runTest(testName: String, args: Args): Status = shadowSuite.runShadowTest(testName, args)

  def runShadowTest(testName: String, args: Args): Status = {
    setupAndroidEnvironmentForTest(args)
    super.runTest(testName, args)
  }

  def setupAndroidEnvironmentForTest(args: Args) = {

    parallelUniverse.resetStaticState()

    val testLifecycle = classLoader.loadClass(classOf[DefaultTestLifecycle].getName).newInstance.asInstanceOf[TestLifecycle[_]]
    val strictI18n = Option(System.getProperty("robolectric.strictI18n")).exists(_.toBoolean)

    val sdkVersion = Build.VERSION_CODES.JELLY_BEAN_MR2
    val versionClass = sdkEnvironment.bootstrappedClass(classOf[Build.VERSION])
    staticField("SDK_INT").ofType(classOf[Int]).in(versionClass).set(sdkVersion)

    parallelUniverse.setUpApplicationState(null, testLifecycle, strictI18n, resourceLoader, null, new Config.Implementation(1, "", "", "", 1, Array()))
  }
}

object RobolectricTests {
  val sdkConfig = new SdkConfig(Build.VERSION_CODES.JELLY_BEAN_MR2)
  lazy val urls = MavenCentral.getLocalArtifactUrls(sdkConfig.getSdkClasspathDependencies: _*).values.toArray

  lazy val shadowMap = new ShadowMap.Builder().build()
  lazy val classHandler = new ShadowWrangler(shadowMap, sdkConfig)
  lazy val classLoader = {
    val setup = new Setup() {
      override def shouldAcquire(name: String): Boolean = {
        !name.startsWith("org.scala") && super.shouldAcquire(name)
      }
    }
    setup.getClassesToDelegateFromRcl.add(classOf[RobolectricTests].getName)
    new AsmInstrumentingClassLoader(setup, urls: _*)
  }

  lazy val sdkEnvironment = {
    val env = new SdkEnvironment(sdkConfig, classLoader)
    env.setCurrentClassHandler(classHandler)

    val className = classOf[RobolectricInternals].getName
    val robolectricInternalsClass = classLoader.loadClass(className)
    val field = robolectricInternalsClass.getDeclaredField("classHandler")
    field.setAccessible(true)
    field.set(null, classHandler)

    env
  }

  lazy val resourceLoader = {
    val url = MavenCentral.getLocalArtifactUrl(sdkConfig.getSystemResourceDependency)
    val systemResFs = Fs.fromJar(url)
    val resourceExtractor = new ResourceExtractor(classLoader)
    val resourcePath = new ResourcePath(resourceExtractor.getProcessedRFile, resourceExtractor.getPackageName, systemResFs.join("res"), systemResFs.join("assets"))
    new PackageResourceLoader(resourcePath, resourceExtractor)
  }

  lazy val parallelUniverse = {
    val universe = sdkEnvironment.getRobolectricClassLoader.loadClass(classOf[RoboTestUniverse].getName).asInstanceOf[Class[ParallelUniverseInterface]].newInstance()
    universe.setSdkConfig(sdkConfig)
    universe
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

