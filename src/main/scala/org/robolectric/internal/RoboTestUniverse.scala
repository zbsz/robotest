package org.robolectric.internal

import org.robolectric._
import org.robolectric.shadows.{ShadowActivityThread, ShadowContextImpl, ShadowResources, ShadowLog}
import org.robolectric.res.{ResourceLoader, ResBunch}
import org.robolectric.res.builder.RobolectricPackageManager
import android.content.res.{Configuration, Resources}
import org.robolectric.Robolectric._
import org.fest.reflect.core.Reflection._
import android.app.{Application, Instrumentation}
import android.content.Context
import android.content.pm.{PackageManager, ApplicationInfo}
import java.lang.reflect.Method
import org.robolectric.annotation.Config

/**
  */
class RoboTestUniverse extends ParallelUniverseInterface {
  private final val DEFAULT_PACKAGE_NAME: String = "org.robolectric.default"
  private var loggingInitialized: Boolean = false
  private var sdkConfig: SdkConfig = null

  def resetStaticState() {
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
    ResBunch.getVersionQualifierApiLevel(qualifiers) match {
      case -1 if qualifiers.length > 0 => qualifiers + "-v" + sdkConfig.getApiLevel
      case -1 => qualifiers + "v" + sdkConfig.getApiLevel
      case _ => qualifiers
    }

  def setUpApplicationState(m: Method, testLifecycle: TestLifecycle[_], strictI18n: Boolean, systemResourceLoader: ResourceLoader, appManifest: AndroidManifest, config: Config) {
    Robolectric.application = null
    Robolectric.packageManager = new RobolectricPackageManager
    Robolectric.packageManager.addPackage(DEFAULT_PACKAGE_NAME)
    var resourceLoader: ResourceLoader = null
//    if (appManifest != null) {
//      resourceLoader = robolectricTestRunner.getAppResourceLoader(sdkConfig, systemResourceLoader, appManifest)
//      Robolectric.packageManager.addManifest(appManifest, resourceLoader)
//    }
//    else {
      resourceLoader = systemResourceLoader
//    }
    ShadowResources.setSystemResources(systemResourceLoader)
    val qualifiers: String = addVersionQualifierToQualifiers(config.qualifiers)
    val systemResources: Resources = Resources.getSystem
    val configuration: Configuration = systemResources.getConfiguration
    shadowOf(configuration).overrideQualifiers(qualifiers)
    systemResources.updateConfiguration(configuration, systemResources.getDisplayMetrics)
    val contextImplClass: Class[_] = `type`(ShadowContextImpl.CLASS_NAME).withClassLoader(getClass.getClassLoader).load
    val activityThreadClass: Class[_] = `type`(ShadowActivityThread.CLASS_NAME).withClassLoader(getClass.getClassLoader).load
    val activityThread = constructor.in(activityThreadClass).newInstance().asInstanceOf[AnyRef]

    Robolectric.activityThread = activityThread.asInstanceOf[AnyRef]
    field("mInstrumentation").ofType(classOf[Instrumentation]).in(activityThread).set(new RoboInstrumentation)
    field("mCompatConfiguration").ofType(classOf[Configuration]).in(activityThread).set(configuration)

    val systemContextImpl: Context = method("createSystemContext")
      .withReturnType(contextImplClass)
      .withParameterTypes(activityThreadClass)
      .in(contextImplClass)
      .invoke(activityThread).asInstanceOf[Context]

    val application: Application = testLifecycle.createApplication(m, appManifest).asInstanceOf[Application]

    if (application != null) {
      var packageName: String = if (appManifest != null) appManifest.getPackageName else null
      if (packageName == null) packageName = DEFAULT_PACKAGE_NAME
      var applicationInfo: ApplicationInfo = null
      try {
        applicationInfo = Robolectric.packageManager.getApplicationInfo(packageName, 0)
      } catch {
        case e: PackageManager.NameNotFoundException => throw new RuntimeException(e)
      }
      val compatibilityInfoClass: Class[_] = `type`("android.content.res.CompatibilityInfo").load

      val loadedApk = method("getPackageInfo")
        .withParameterTypes(classOf[ApplicationInfo], compatibilityInfoClass, classOf[ClassLoader], classOf[Boolean], classOf[Boolean])
        .in(activityThread)
        .invoke(applicationInfo, null, getClass.getClassLoader, java.lang.Boolean.FALSE, java.lang.Boolean.TRUE).asInstanceOf[AnyRef]

      shadowOf(application).bind(appManifest, resourceLoader)
      if (appManifest == null) {
        shadowOf(application).setPackageName(applicationInfo.packageName)
      }
      val appResources: Resources = application.getResources
      field("mResources").ofType(classOf[Resources]).in(loadedApk).set(appResources)
      val contextImpl: Context = method("createPackageContext")
        .withReturnType(classOf[Context])
        .withParameterTypes(classOf[String], classOf[Int])
        .in(systemContextImpl)
        .invoke(applicationInfo.packageName, Integer.valueOf(Context.CONTEXT_INCLUDE_CODE))

      field("mInitialApplication").ofType(classOf[Application]).in(activityThread).set(application)
      method("attach").withParameterTypes(classOf[Context]).in(application).invoke(contextImpl)
      appResources.updateConfiguration(configuration, appResources.getDisplayMetrics)
      shadowOf(application).setStrictI18n(strictI18n)

      Robolectric.application = application
      application.onCreate()
    }
  }

  def tearDownApplication(): Unit = {
    if (Robolectric.application != null) {
      Robolectric.application.onTerminate()
    }
  }

  def getCurrentApplication = Robolectric.application

  def setSdkConfig(sdkConfig: SdkConfig): Unit = {
    this.sdkConfig = sdkConfig
  }
}
