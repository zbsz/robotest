package org.robotest

import android.os.Build
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.scalatest.{FeatureSpec, Matchers, RobolectricSuite}

@Config(reportSdk = Build.VERSION_CODES.JELLY_BEAN, manifest = "src/main/AndroidManifest.xml")
class OverrideSdkVersionSpec extends FeatureSpec with Matchers with RobolectricSuite {

  scenario("Use sdk version specified in config annotation") {
    Build.VERSION.SDK_INT shouldEqual Build.VERSION_CODES.JELLY_BEAN

    val appManifest = Robolectric.shadowOf(Robolectric.application).getAppManifest
    Build.VERSION.SDK_INT should not equal appManifest.getTargetSdkVersion
  }
}
