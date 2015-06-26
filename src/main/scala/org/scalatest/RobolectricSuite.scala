package org.scalatest

import java.io.File
import java.lang.annotation.Annotation
import java.lang.reflect
import java.security.SecureRandom
import java.util
import java.util.Properties

import android.os.Build
import org.robolectric._
import org.robolectric.annotation.Config
import org.robolectric.internal._
import org.robolectric.internal.bytecode._
import org.robolectric.internal.dependency._
import org.robolectric.manifest.AndroidManifest
import org.robolectric.res._
import org.robolectric.util.{Logger, ReflectionHelpers}

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

  val skipInstrumentation: Seq[Class[_]] = Nil
  val skipInstrumentationPkg: Seq[String] = Nil
  val aarsDir = Fs.currentDirectory().join("target", "aars")

  def robolectricShadows: Seq[Class[_]] = Nil

  lazy val runner = new RoboSuiteRunner(this)

  abstract override def run(testName: Option[String], args: Args): Status = runner.run(testName, args)

  def runShadow(testName: Option[String], args: Args): Status = super.run(testName, args)
}

class RoboSuiteRunner(suite: RobolectricSuite) { runner =>

  val suiteClass = suite.getClass
  val skipInstrumentation = suite.skipInstrumentation
  val skipInstrumentationPkg = suite.skipInstrumentationPkg
  val shadows = suite.robolectricShadows
  val aarsDir = suite.aarsDir

  lazy val config: Config = {

    val configProperties: Properties =
      Option(suiteClass.getClassLoader.getResourceAsStream("robolectric.properties")) .map { resourceAsStream =>
        try {
          val properties = new Properties
          properties.load(resourceAsStream)
          properties
        } finally { resourceAsStream.close() }
      } .orNull

    def classConfigs(cls: Class[_], acc: List[Config] = Nil): List[Config] = cls match {
      case null => acc
      case _ => classConfigs(cls.getSuperclass, cls.getAnnotation(classOf[Config]) :: acc)
    }

    (RoboSuiteRunner.defaultsFor(classOf[Config])
      :: Config.Implementation.fromProperties(configProperties)
      :: classConfigs(suiteClass)
    )reduceLeft { (config, opt) =>
      Option(opt).fold(config)(new Config.Implementation(config, _))
    }
  }

  lazy val appManifest: AndroidManifest = {
    def libraryDirs(baseDir: FsFile) = config.libraries().map(baseDir.join(_)).toSeq

    def createAppManifest(manifestFile: FsFile, resDir: FsFile, assetsDir: FsFile): AndroidManifest = {
      if (manifestFile.exists) {
        val manifest = new AndroidManifest(manifestFile, resDir, assetsDir) {
          override def findLibraries(): util.List[FsFile] = {
            val aars = if (aarsDir.isDirectory) aarsDir.listFiles().filter(d => d.isDirectory && d.listFiles().nonEmpty).toList else Nil
            (aars ++ super.findLibraries().asScala).distinct.asJava
          }
        }
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
        val manifestFile = {
          val file = Fs.currentDirectory().join(if (defaultManifest) AndroidManifest.DEFAULT_MANIFEST_NAME else config.manifest)
          if (file.exists() || !defaultManifest) file
          else Fs.currentDirectory().join("src", "main", AndroidManifest.DEFAULT_MANIFEST_NAME)
        }
        val baseDir = manifestFile.getParent
        createAppManifest(manifestFile, baseDir.join(config.resourceDir), baseDir.join(Config.DEFAULT_ASSET_FOLDER))
      }
    }
  }

  lazy val sdkVersion = {
    require(config.sdk.length <= 1, s"RobolectricSuite does not support multiple values for @Config.sdk")
    if (config.sdk.nonEmpty && config.sdk.head != -1) config.sdk.head
    else Option(appManifest).fold(SdkConfig.FALLBACK_SDK_VERSION)(_.getTargetSdkVersion)
  }

  lazy val jarResolver =
    if (System.getProperty("robolectric.offline") != "true") {
      val cacheDir = new File(new File(System.getProperty("java.io.tmpdir")), "robolectric")
      cacheDir.mkdirs
      if (cacheDir.exists) {
        Logger.info("Dependency cache location: %s", cacheDir.getAbsolutePath)
        new CachedDependencyResolver(new MavenDependencyResolver, cacheDir, 60 * 60 * 24 * 1000)
      } else new MavenDependencyResolver
    } else new LocalDependencyResolver(new File(System.getProperty("robolectric.dependency.dir", ".")))

  lazy val classLoaderConfig = {
    val builder = InstrumentationConfiguration.newBuilder()
    builder.doNotAquireClass(classOf[RoboSuiteRunner].getName)
    builder.doNotAquirePackage("org.scalatest")
    builder.doNotAquirePackage("org.scalactic")
    skipInstrumentation foreach { cls => builder.doNotAquireClass(cls.getName) }
    skipInstrumentationPkg foreach builder.doNotAquirePackage
    builder.build()
  }

  lazy val sdkEnvironment = {
    val env = new InstrumentingClassLoaderFactory(classLoaderConfig, jarResolver).getSdkEnvironment(new SdkConfig(sdkVersion))

    val shadowMap = {
      val builder = new ShadowMap.Builder()
      config.shadows() foreach builder.addShadowClass
      shadows foreach builder.addShadowClass
      builder.build()
    }

    val classHandler = new ShadowWrangler(shadowMap)

    val robolectricInternalsClass = ReflectionHelpers.loadClass(env.getRobolectricClassLoader, classOf[RobolectricInternals].getName)
    ReflectionHelpers.setStaticField(robolectricInternalsClass, "classHandler", classHandler)
    ReflectionHelpers.setStaticField(env.bootstrappedClass(classOf[Build.VERSION]), "SDK_INT", sdkVersion)

    env
  }
  
  lazy val systemResourceLoader = sdkEnvironment.getSystemResourceLoader(jarResolver)

  lazy val appResourceLoader = Option(appManifest) map { manifest =>
    val appAndLibraryResourceLoaders = manifest.getIncludedResourcePaths.asScala.map(new PackageResourceLoader(_))
    val overlayResourceLoader = new OverlayResourceLoader(manifest.getPackageName, appAndLibraryResourceLoaders.asJava)
    val resourceLoaders = Map(
      "android" -> systemResourceLoader,
      manifest.getPackageName -> overlayResourceLoader
    )
    new RoutingResourceLoader(resourceLoaders.asJava)
  }
  
  lazy val classLoader = sdkEnvironment.getRobolectricClassLoader

  lazy val resourceLoader = appResourceLoader.getOrElse(systemResourceLoader)

  lazy val parallelUniverse =
    sdkEnvironment.getRobolectricClassLoader
      .loadClass(classOf[RoboTestUniverse].getName)
      .asSubclass(classOf[ParallelUniverseInterface])
      .getConstructor(classOf[ResourceLoader])
      .newInstance(resourceLoader)

  def run(testName: Option[String], args: Args): Status = {
    Thread.currentThread.setContextClassLoader(sdkEnvironment.getRobolectricClassLoader)

    parallelUniverse.resetStaticState(config)
    parallelUniverse.setSdkConfig(sdkEnvironment.getSdkConfig)

    val testLifecycle = classLoader.loadClass(classOf[DefaultTestLifecycle].getName).newInstance.asInstanceOf[TestLifecycle[_]]
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
  new SecureRandom() // this starts up the Poller SunPKCS11-Darwin thread early, outside of any Robolectric classloader

  private def defaultsFor[A <: Annotation](annotation: Class[A]): A = {
    annotation.cast(reflect.Proxy.newProxyInstance(annotation.getClassLoader, Array[Class[_]](annotation), new reflect.InvocationHandler() {
      override def invoke(proxy: AnyRef, method: reflect.Method, args: Array[AnyRef]): AnyRef = method.getDefaultValue
    }))
  }
}
