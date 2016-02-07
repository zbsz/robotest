package org.scalatest

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
trait RobolectricSuite extends SuiteMixin with robotest.RoboTest { self: Suite =>

  lazy val runner = new RoboSuiteRunner(this)

  abstract override def run(testName: Option[String], args: Args): Status =
    runner.run(testName, args)

  def runShadow(testName: Option[String], args: Args): Status =
    super.run(testName, args)
}

class RoboSuiteRunner(val suite: RobolectricSuite) extends robotest.RoboSuiteRunner[RobolectricSuite] { runner =>
  def run(testName: Option[String], args: Args): Status = {
    try {
      setup()
      roboInstance.runShadow(testName, args)
    } finally {
      teardown()
    }
  }
}
