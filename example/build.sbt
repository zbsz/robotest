import android.Keys._

android.Plugin.androidBuild

platformTarget in Android := "android-18"

minSdkVersion in Android := "8"

targetSdkVersion in Android := "18"

name := "robotest-example"

organization := "com.geteit"

version := "0.1"

scalaVersion := "2.11.4"

resolvers ++= Seq(
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "RoboTest releases" at "https://raw.github.com/zbsz/mvn-repo/master/releases/"
)

libraryDependencies ++= Seq(
  "org.robolectric" % "android-all" % "5.0.0_r2-robolectric-0" % "provided",
  aar("com.android.support" % "support-v4" % "20.0.0"),
  "com.geteit" %% "robotest" % "0.7" % "test",
  "junit" % "junit" % "4.8.2" % "test",
  "org.scalatest" %% "scalatest" % "2.1.6" % "test"
)

fork in Test := true
