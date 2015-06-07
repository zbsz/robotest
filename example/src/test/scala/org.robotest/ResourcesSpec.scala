package org.robotest

import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.scalatest.{FeatureSpec, Matchers, RobolectricSuite}

@Config(manifest = "src/main/AndroidManifest.xml")
class ResourcesSpec extends FeatureSpec with Matchers with RobolectricSuite {

  scenario("Load string from android resources") {
    RuntimeEnvironment.application.getResources.getString(R.string.test_string) shouldEqual "test"
  }
}
