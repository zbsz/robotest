package org.scalatest

import java.io.File
import java.lang.reflect.{Field, Modifier}
import java.util.Properties

import android.os.Build
import org.robolectric._
import org.robolectric.annotation.Config
import org.robolectric.annotation.Config.Implementation
import org.robolectric.internal._
import org.robolectric.internal.bytecode._
import org.robolectric.internal.dependency._
import org.robolectric.manifest.AndroidManifest
import org.robolectric.res._
import org.robolectric.util.ReflectionHelpers
import scala.collection.JavaConverters._
import scala.util.Try

/**
 * Enables Robolectric shadow implementations for Android stubs.
 *
 * This works similarly to RobolectricTestRunner class:
 * - prepares Robolectric class loader (uses some magic copied from RobolectricTestRunner)
 * - loads current suite in Robolectric loader
 * - executes tests in shadowed suite
 */
trait RobolectricSuite extends SuiteMixin { self: Suite =>

  def useInstrumentation(name: String): Option[Boolean] = None

  def robolectricShadows: Seq[Class[_]] = Nil

  lazy val runner = new RoboSuiteRunner(this.getClass, useInstrumentation, robolectricShadows)

  abstract override def run(testName: Option[String], args: Args): Status = runner.run(testName, args)

  def runShadow(testName: Option[String], args: Args): Status = super.run(testName, args)
}

class RoboSuiteRunner(suiteClass: Class[_ <: RobolectricSuite], shouldAcquire: String => Option[Boolean], shadows: Seq[Class[_]] = Nil) { runner =>

  val configProperties: Properties =
    Option(suiteClass.getClassLoader.getResourceAsStream("org.robolectric.Config.properties")) .map { resourceAsStream =>
      val properties = new Properties
      properties.load(resourceAsStream)
      properties
    } .orNull

  val config: Config =
    Seq(// TODO: This method moved to RobolectricTestRunner and is private. AnnotationUtil.defaultsFor(classOf[Config]),
      Config.Implementation.fromProperties(configProperties),
      suiteClass.getAnnotation(classOf[Config])
    ) reduceLeft { (config, opt) =>
      Option(opt).fold(config)(new Implementation(config, _))
    }

  val appManifest: AndroidManifest = {

    def libraryDirs(baseDir: FsFile) = config.libraries().map(baseDir.join(_)).toSeq

    def createAppManifest(manifestFile: FsFile, resDir: FsFile, assetsDir: FsFile): AndroidManifest = {
      if (manifestFile.exists) {
        val manifest = new AndroidManifest(manifestFile, resDir, assetsDir)
        manifest.setPackageName(System.getProperty("android.package"))
        if (config.libraries().nonEmpty) {
          manifest.setLibraryDirectories(libraryDirs(manifestFile.getParent).asJava)
        }
        manifest
      } else {
        System.out.print("WARNING: No manifest file found at " + manifestFile.getPath + ".")
        System.out.println("Falling back to the Android OS resources only.")
        System.out.println("To remove this warning, annotate your test class with @Config(manifest=Config.NONE).")
        null
      }
    }

    if (config.manifest == Config.NONE) null
    else {
      val manifestProperty = System.getProperty("android.manifest")
      val resourcesProperty = System.getProperty("android.resources")
      val assetsProperty = System.getProperty("android.assets")
      val defaultManifest = config.manifest == Config.DEFAULT
      if (defaultManifest && manifestProperty != null) {
        createAppManifest(Fs.fileFromPath(manifestProperty), Fs.fileFromPath(resourcesProperty), Fs.fileFromPath(assetsProperty))
      } else {
        val manifestFile = Fs.currentDirectory().join(if (defaultManifest) AndroidManifest.DEFAULT_MANIFEST_NAME else config.manifest)
        val baseDir = manifestFile.getParent
        createAppManifest(manifestFile, baseDir.join(config.resourceDir), baseDir.join(Config.DEFAULT_ASSET_FOLDER))
      }
    }
  }

  val sdkVersion = {
    if (config.sdk.size > 0 && config.sdk()(0) != -1) config.sdk()(0)
    else Option(appManifest).fold(Build.VERSION_CODES.JELLY_BEAN_MR2)(_.getTargetSdkVersion)
  }

  val sdkConfig = new SdkConfig(sdkVersion)
  val shadowMap = {
    val builder = new ShadowMap.Builder()
    shadows foreach builder.addShadowClass
    builder.build()
  }

  val jarResolver =
    if (System.getProperty("robolectric.offline") != "true") new MavenDependencyResolver
    else new LocalDependencyResolver(new File(System.getProperty("robolectric.dependency.dir", ".")))

  val classHandler = new ShadowWrangler(shadowMap)
  val classLoader = {
    val setup = InstrumentationConfiguration.newBuilder()
      .doNotAquireClass(classOf[RoboSuiteRunner].getName)
      .build()
    /* TODO: Not sure how to handle this
    new InstrumentationConfiguration() {
      override def shouldAcquire(name: String): Boolean = {
        name !=  &&
          !name.startsWith("org.scala") &&
          runner.shouldAcquire(name).getOrElse(super.shouldAcquire(name))
      }
    }
    */
    val urls = jarResolver.getLocalArtifactUrls(sdkConfig.getSdkClasspathDependencies :_*)
    new InstrumentingClassLoader(setup, urls: _*)
  }

  val sdkEnvironment = {
    val env = new SdkEnvironment(sdkConfig, classLoader)
    // TODO: Method does not exist anymore env.setCurrentClassHandler(classHandler)

    val className = classOf[RobolectricInternals].getName
    val robolectricInternalsClass = ReflectionHelpers.loadClass(classLoader, className)
    ReflectionHelpers.setStaticField(robolectricInternalsClass, "classHandler", classHandler)

    val versionClass = env.bootstrappedClass(classOf[Build.VERSION])
    val sdk_int = versionClass.getDeclaredField("SDK_INT")
    sdk_int.setAccessible(true)
    val modifiers = classOf[Field].getDeclaredField("modifiers")
    modifiers.setAccessible(true)
    modifiers.setInt(sdk_int, sdk_int.getModifiers & ~Modifier.FINAL)
    sdk_int.setInt(null, sdkVersion)

    env
  }

  val systemResourceLoader = sdkEnvironment.getSystemResourceLoader(jarResolver)

  val appResourceLoader = Option(appManifest) map { manifest =>
    val appAndLibraryResourceLoaders = manifest.getIncludedResourcePaths.asScala.map(new PackageResourceLoader(_))
    val overlayResourceLoader = new OverlayResourceLoader(manifest.getPackageName, appAndLibraryResourceLoaders.asJava)
    val resourceLoaders = Map(
      "android" -> systemResourceLoader,
      appManifest.getPackageName -> overlayResourceLoader
    )
    new RoutingResourceLoader(resourceLoaders.asJava)
  }

  val resourceLoader = appResourceLoader.getOrElse(systemResourceLoader)

  val parallelUniverse = {
    val universe = classLoader.loadClass(classOf[RoboTestUniverse].getName)
      .asSubclass(classOf[ParallelUniverseInterface])
      .getConstructor(classOf[RoboSuiteRunner])
      .newInstance(this)
    universe.setSdkConfig(sdkConfig)
    universe
  }

  def run(testName: Option[String], args: Args): Status = {
    Thread.currentThread.setContextClassLoader(sdkEnvironment.getRobolectricClassLoader)

    parallelUniverse.resetStaticState(config)
    val testLifecycle = classLoader.loadClass(classOf[DefaultTestLifecycle].getName).newInstance.asInstanceOf[TestLifecycle[_]]
    /* TODO: Where to set this now: val strictI18n = Option(System.getProperty("robolectric.strictI18n")).exists(_.toBoolean) */
    parallelUniverse.setUpApplicationState(null, testLifecycle, systemResourceLoader, appManifest, config)

    try {
      Try(resourceLoader.getRawValue(null)) // force resources loading
      val shadowSuite = classLoader.loadClass(suiteClass.getName).newInstance.asInstanceOf[RobolectricSuite]
      shadowSuite.runShadow(testName, args)
    } finally {
      parallelUniverse.tearDownApplication()
      parallelUniverse.resetStaticState(config)
      Thread.currentThread.setContextClassLoader(classOf[RobolectricSuite].getClassLoader)
    }
  }
}

object RoboSuiteRunner {
  // TODO: This should be covered by line 113? InstrumentationConfiguration.CLASSES_TO_ALWAYS_DELEGATE.add(classOf[RoboSuiteRunner].getName)
}
