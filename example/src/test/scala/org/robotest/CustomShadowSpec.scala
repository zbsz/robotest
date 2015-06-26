package org.robotest

import org.robolectric.{ RuntimeEnvironment, Robolectric }

import org.robolectric.annotation.Config
import org.scalatest.{FeatureSpec, Matchers, RobolectricSuite}

@Config(manifest = "src/main/AndroidManifest.xml", shadows = Array(classOf[ShadowFloatMath]))
class CustomShadowAnnotationSpec extends FeatureSpec with Matchers with RobolectricSuite {
  scenario("Returns results of shadowed method calls") {
    android.util.FloatMath.floor(1.0f) should equal(42.0f)

  }
}

@Config(manifest = "src/main/AndroidManifest.xml")
class CustomShadowOverrideSpec extends FeatureSpec with Matchers with RobolectricSuite {
  override def robolectricShadows = Seq(classOf[ShadowFloatMath])
  scenario("Returns results of shadowed method calls") {
    android.util.FloatMath.floor(1.0f) should equal(42.0f)

  }
}
