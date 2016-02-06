package org.specs2

import specification._
import core.Env

abstract class RobolectricSpecification
extends Specification
with BeforeAfterAll
with robotest.RoboTest
{
  var runner = new RoboSuiteRunner(this)

  override def structure = (env: Env) => {
    runner.setup()
    val i = runner.roboInstance
    i.runner = runner
    i.structureShadow(env)
  }

  def afterAll() = {
    runner.teardown()
  }

  def structureShadow = super.structure
}

class RoboSuiteRunner(val suite: RobolectricSpecification) extends robotest.RoboSuiteRunner[RobolectricSpecification]
