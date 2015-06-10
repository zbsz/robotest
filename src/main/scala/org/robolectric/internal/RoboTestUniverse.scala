package org.robolectric.internal

import java.lang.reflect.Method
import java.security.Security

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.{Configuration, Resources}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.robolectric._
import org.robolectric.annotation.Config
import org.robolectric.internal.fakes.RoboInstrumentation
import org.robolectric.manifest.AndroidManifest
import org.robolectric.res.builder.DefaultPackageManager
import org.robolectric.res.{ResBundle, ResourceLoader}
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

class RoboTestUniverse(resourceLoader: ResourceLoader) extends ParallelUniverseInterface {
  private final val DEFAULT_PACKAGE_NAME: String = "org.robolectric.default"
  private val shadowsAdapter: ShadowsAdapter = Robolectric.getShadowsAdapter
  private var loggingInitialized: Boolean = false
  private var sdkConfig: SdkConfig = null

  def resetStaticState(config: Config) {
    Robolectric.reset()
    if (!loggingInitialized) {
      shadowsAdapter.setupLogging()
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
    val packageManager = new DefaultPackageManager(shadowsAdapter)
    packageManager.addPackage(DEFAULT_PACKAGE_NAME)
    if (appManifest != null) {
      packageManager.addManifest(appManifest, resourceLoader)
    }
    RuntimeEnvironment.setRobolectricPackageManager(packageManager)
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.insertProviderAt(new BouncyCastleProvider, 1)
    }
    shadowsAdapter.setSystemResources(systemResourceLoader)
    val qualifiers: String = addVersionQualifierToQualifiers(config.qualifiers)
    val systemResources: Resources = Resources.getSystem
    val configuration: Configuration = systemResources.getConfiguration
    shadowsAdapter.overrideQualifiers(configuration, qualifiers)
    systemResources.updateConfiguration(configuration, systemResources.getDisplayMetrics)
    RuntimeEnvironment.setQualifiers(qualifiers)

    val contextImplClass: Class[_] = ReflectionHelpers.loadClass(getClass.getClassLoader, shadowsAdapter.getShadowContextImplClassName)
    val activityThreadClass: Class[_] = ReflectionHelpers.loadClass(getClass.getClassLoader, shadowsAdapter.getShadowActivityThreadClassName)
    val activityThread: AnyRef = ReflectionHelpers.callConstructor(activityThreadClass.asInstanceOf[Class[AnyRef]])
    RuntimeEnvironment.setActivityThread(activityThread)
    ReflectionHelpers.setField(activityThread, "mInstrumentation", new RoboInstrumentation)
    ReflectionHelpers.setField(activityThread, "mCompatConfiguration", configuration)

    val systemContextImpl: Context = ReflectionHelpers.callStaticMethod(contextImplClass, "createSystemContext", ClassParameter.from(activityThreadClass, activityThread))
    val application: Application = testLifecycle.createApplication(method, appManifest, config).asInstanceOf[Application]
    if (application != null) {
      var packageName: String = if (appManifest != null) appManifest.getPackageName else null
      if (packageName == null) packageName = DEFAULT_PACKAGE_NAME
      var applicationInfo: ApplicationInfo = null
      applicationInfo = RuntimeEnvironment.getPackageManager.getApplicationInfo(packageName, 0)
      val compatibilityInfoClass: Class[_] = ReflectionHelpers.loadClass(getClass.getClassLoader, "android.content.res.CompatibilityInfo")
      val loadedApk: AnyRef = ReflectionHelpers.callInstanceMethod(activityThread, "getPackageInfo", ClassParameter.from(classOf[ApplicationInfo], applicationInfo), ClassParameter.from(compatibilityInfoClass, null), ClassParameter.from(classOf[Int], Context.CONTEXT_INCLUDE_CODE))
      shadowsAdapter.bind(application, appManifest, resourceLoader)
      if (appManifest == null) {
        shadowsAdapter.setPackageName(application, applicationInfo.packageName)
      }
      val appResources: Resources = application.getResources
      ReflectionHelpers.setField(loadedApk, "mResources", appResources)
      val contextImpl: Context = systemContextImpl.createPackageContext(applicationInfo.packageName, Context.CONTEXT_INCLUDE_CODE)
      ReflectionHelpers.setField(activityThreadClass, activityThread, "mInitialApplication", application)
      ReflectionHelpers.callInstanceMethod(classOf[Application], application, "attach", ClassParameter.from(classOf[Context], contextImpl))
      appResources.updateConfiguration(configuration, appResources.getDisplayMetrics)
      shadowsAdapter.setAssetsQualifiers(appResources.getAssets, qualifiers)
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
