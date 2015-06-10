import android.Keys._

android.Plugin.androidBuild

platformTarget in Android := "android-18"

minSdkVersion in Android := "8"

targetSdkVersion in Android := "18"

name := "robotest-example"

organization := "com.geteit"

version := "0.1"

scalaVersion := "2.11.6"

resolvers ++= Seq(
  "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
  "RoboTest releases" at "https://raw.github.com/zbsz/mvn-repo/master/releases/"
)

libraryDependencies ++= Seq(
  "com.geteit" %% "robotest" % "0.9" % Test,
  "org.scalatest" %% "scalatest" % "2.2.5" % Test
)

fork in Test := true
