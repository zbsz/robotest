import android.Keys._

android.Plugin.androidBuild

platformTarget in Android := "android-21"

minSdkVersion in Android := "8"

targetSdkVersion in Android := "21"

name := "robotest-example"

organization := "com.geteit"

version := "0.1"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.android.support" % "appcompat-v7" % "22.1.1",
  "com.geteit" %% "robotest" % "0.12" % Test,
  "org.scalatest" %% "scalatest" % "2.2.5" % Test
)

fork in Test := true
