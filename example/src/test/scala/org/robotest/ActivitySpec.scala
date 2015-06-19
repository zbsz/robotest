package org.robotest

import org.robolectric.{ RuntimeEnvironment, Robolectric }

import org.robolectric.annotation.Config
import org.scalatest.{FeatureSpec, Matchers, RobolectricSuite}

@Config(manifest = "src/main/AndroidManifest.xml")
class ActivitySpec extends FeatureSpec with Matchers with RobolectricSuite {

  scenario("Start real activity") {
    Robolectric.setupActivity(classOf[TestActivity])
  }
}
