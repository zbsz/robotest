package org.robolectric.internal

import java.lang.reflect.Method

import android.app.Application
import android.content.Context
import android.content.pm.{ApplicationInfo, PackageManager}
import android.content.res.{Configuration, Resources}
import org.robolectric.Robolectric._
import org.robolectric._
import org.robolectric.internal.fakes.RoboInstrumentation
import org.robolectric.manifest.AndroidManifest
import org.robolectric.annotation.Config
import org.robolectric.res.builder.{ RobolectricPackageManager, DefaultPackageManager }
import org.robolectric.res.{ResBunch, ResourceLoader, ResBundle}
import org.robolectric.shadows.{ShadowActivityThread, ShadowContextImpl, ShadowLog, ShadowResources}
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import org.scalatest.RoboSuiteRunner

/**
  */
class RoboTestUniverse(roboSuiteRunner: RoboSuiteRunner) extends ParallelUniverseInterface {
  private final val DEFAULT_PACKAGE_NAME: String = "org.robolectric.default"
  private var loggingInitialized: Boolean = false
  private var sdkConfig: SdkConfig = null

  def resetStaticState(config: Config) {
    Robolectric.reset()
    if (!loggingInitialized) {
      ShadowLog.setupLogging()
      loggingInitialized = true
    }
  }

  /*
   * If the Config already has a version qualifier, do nothing. Otherwise, add a version
   * qualifier for the target api level (which comes from the manifest or Config.emulateSdk()).
   */
  private def addVersionQualifierToQualifiers(qualifiers: String): String =
    ResBundle.getVersionQualifierApiLevel(qualifiers) match {
      case -1 if qualifiers.length > 0 => qualifiers + "-v" + sdkConfig.getApiLevel
      case -1 => qualifiers + "v" + sdkConfig.getApiLevel
      case _ => qualifiers
    }

  def setUpApplicationState(method: Method, testLifecycle: TestLifecycle[_], systemResourceLoader: ResourceLoader, appManifest: AndroidManifest, config: Config) {
    RuntimeEnvironment.application = null
    val packageManager = new DefaultPackageManager(Robolectric.getShadowsAdapter())
    packageManager.addPackage(DEFAULT_PACKAGE_NAME)
    val resourceLoader = roboSuiteRunner.resourceLoader
    if (appManifest != null) {
      packageManager.addManifest(appManifest, resourceLoader)
    }
    RuntimeEnvironment.setRobolectricPackageManager(packageManager)
    ShadowResources.setSystemResources(systemResourceLoader)
    val qualifiers: String = addVersionQualifierToQualifiers(config.qualifiers)
    val systemResources: Resources = Resources.getSystem
    val configuration: Configuration = systemResources.getConfiguration
    shadowOf(configuration).overrideQualifiers(qualifiers)
    systemResources.updateConfiguration(configuration, systemResources.getDisplayMetrics)
    shadowOf(systemResources.getAssets).setQualifiers(qualifiers)
    val contextImplClass: Class[_] = ReflectionHelpers.loadClass(getClass.getClassLoader, ShadowContextImpl.CLASS_NAME)
    val activityThreadClass: Class[_] = ReflectionHelpers.loadClass(getClass.getClassLoader, ShadowActivityThread.CLASS_NAME)
    val activityThread: AnyRef = ReflectionHelpers.callConstructor(activityThreadClass.asInstanceOf[Class[AnyRef]])
    RuntimeEnvironment.setActivityThread(activityThread)
    ReflectionHelpers.setField(activityThread, "mInstrumentation", new RoboInstrumentation)
    ReflectionHelpers.setField(activityThread, "mCompatConfiguration", configuration)
    val systemContextImpl: Context = ReflectionHelpers.callStaticMethod(contextImplClass, "createSystemContext", new ReflectionHelpers.ClassParameter(activityThreadClass, activityThread))
    val application: Application = testLifecycle.createApplication(method, appManifest, config).asInstanceOf[Application]
    if (application != null) {
      var packageName: String = if (appManifest != null) appManifest.getPackageName else null
      if (packageName == null) packageName = DEFAULT_PACKAGE_NAME
      var applicationInfo: ApplicationInfo = null
      try {
        applicationInfo = RuntimeEnvironment.getPackageManager.getApplicationInfo(packageName, 0)
      }
      catch {
        case e: PackageManager.NameNotFoundException => {
          throw new RuntimeException(e)
        }
      }
      val compatibilityInfoClass: Class[_] = ReflectionHelpers.loadClass(getClass.getClassLoader, "android.content.res.CompatibilityInfo")
      val loadedApk: AnyRef = ReflectionHelpers.callInstanceMethod(activityThread, "getPackageInfo", new ReflectionHelpers.ClassParameter(classOf[ApplicationInfo], applicationInfo), new ReflectionHelpers.ClassParameter(compatibilityInfoClass, null), new ReflectionHelpers.ClassParameter(classOf[ClassLoader], getClass.getClassLoader), new ReflectionHelpers.ClassParameter(classOf[Boolean], false), new ReflectionHelpers.ClassParameter(classOf[Boolean], true))
      shadowOf(application).bind(appManifest, resourceLoader)
      if (appManifest == null) {
        shadowOf(application).setPackageName(applicationInfo.packageName)
      }
      val appResources: Resources = application.getResources
      ReflectionHelpers.setField(loadedApk, "mResources", appResources)
      val contextImpl: Context = ReflectionHelpers.callInstanceMethod(systemContextImpl, "createPackageContext", new ReflectionHelpers.ClassParameter(classOf[String], applicationInfo.packageName), new ReflectionHelpers.ClassParameter(classOf[Int], Context.CONTEXT_INCLUDE_CODE))
      ReflectionHelpers.setField(activityThread, "mInitialApplication", application)
      ReflectionHelpers.callInstanceMethod(application, "attach", new ReflectionHelpers.ClassParameter(classOf[Context], contextImpl))
      appResources.updateConfiguration(configuration, appResources.getDisplayMetrics)
      shadowOf(appResources.getAssets).setQualifiers(qualifiers)
      RuntimeEnvironment.application = application
      application.onCreate()
    }
  }

  def tearDownApplication(): Unit = {
    if (RuntimeEnvironment.application != null) {
      RuntimeEnvironment.application.onTerminate()
    }
  }

  def getCurrentApplication = RuntimeEnvironment.application

  def setSdkConfig(sdkConfig: SdkConfig): Unit = {
    this.sdkConfig = sdkConfig
  }
}
