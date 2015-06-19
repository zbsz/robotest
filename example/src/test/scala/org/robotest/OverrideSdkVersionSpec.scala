package org.robotest

import android.os.Build
import org.robolectric.{Shadows, RuntimeEnvironment}
import org.robolectric.annotation.Config
import org.scalatest.{FeatureSpec, Matchers, RobolectricSuite}

@Config(sdk = Array(Build.VERSION_CODES.JELLY_BEAN), manifest = "src/main/AndroidManifest.xml")
class OverrideSdkVersionSpec extends FeatureSpec with Matchers with RobolectricSuite {

  scenario("Use sdk version specified in config annotation") {
    Build.VERSION.SDK_INT shouldEqual Build.VERSION_CODES.JELLY_BEAN

    val appManifest = Shadows.shadowOf(RuntimeEnvironment.application).getAppManifest
    Build.VERSION.SDK_INT should not equal appManifest.getTargetSdkVersion
  }
}
