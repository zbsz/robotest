
name := "robotest"

organization := "com.geteit"

version := "0.2"

scalaVersion := "2.11.0"

crossScalaVersions := Seq("2.10.0")

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
  "org.robolectric" % "robolectric" % "2.3",
  "org.robolectric" % "android-all" % "4.3_r2-robolectric-0" % "provided",
  "org.scalatest" %% "scalatest" % "2.1.6",
  "junit" % "junit" % "4.8.2",
  "org.easytesting" % "fest-assert-core" % "2.0M10" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test"
)

parallelExecution in Test := false

fork in Test := true

testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v")

lazy val root = Project("robotest", file("."))

