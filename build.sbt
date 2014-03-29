
name := "robotest"

organization := "com.geteit"

libraryDependencies ++= Seq(
  "org.robolectric" % "robolectric" % "2.2",
  "com.google.android" % "android" % "4.1.1.4",
  "org.scalatest" %% "scalatest" % "2.1.0"
)

lazy val root = Project("robotest", file("."))

