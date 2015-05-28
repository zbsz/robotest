name := "robotest"

organization := "com.geteit"

version := "0.7"

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.10.5", "2.11.6")

resolvers ++= Seq(
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "AndroidSdk android extras repository" at (androidSdkDir.value / "extras" / "android" / "m2repository").toURI.toString
)

publishTo := {
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some(Resolver.file("snapshots", new File("../mvn-repo/snapshots" )) )
  else
    Some(Resolver.file("releases", new File("../mvn-repo/releases" )) )
}

libraryDependencies ++= Seq(
  "org.robolectric" % "robolectric" % "3.0-SNAPSHOT",
  "org.robolectric" % "android-all" % "5.0.0_r2-robolectric-1" % "provided",
  "com.android.support" % "support-v4" % "19.0.0" % "provided",
  "org.scalatest" %% "scalatest" % "2.2.5",
  "junit" % "junit" % "4.12",
  "org.apache.maven" % "maven-ant-tasks" % "2.1.3"
)

fork in Test := true

lazy val root = Project("robotest", file("."))

lazy val androidSdkDir = settingKey[File]("Android sdk dir from ANDROID_HOME")

androidSdkDir := {
  val path = System.getenv("ANDROID_HOME")
  if (path == null || !file(path).exists()) println(s"ANDROID_HOME not found: '$path'")
  file(path)
}
