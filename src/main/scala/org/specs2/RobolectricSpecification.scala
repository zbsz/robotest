package org.specs2

import specification._
import core.Env

abstract class RobolectricSpecification
extends Specification
with robotest.RoboTest
{
  lazy val runner = new RoboSuiteRunner(this)

  override def structure = (env: Env) => {
    runner.setup()
    runner.roboInstance.structureShadow(env)
  }

  def structureShadow = super.structure
}

class RoboSuiteRunner(val suite: RobolectricSpecification) extends robotest.RoboSuiteRunner[RobolectricSpecification]
