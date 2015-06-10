name := "robotest"

organization := "com.geteit"

version := "0.9"

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.10.5", "2.11.6")

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

publishTo := {
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some(Resolver.file("snapshots", new File("../mvn-repo/snapshots" )) )
  else
    Some(Resolver.file("releases", new File("../mvn-repo/releases" )) )
}

libraryDependencies ++= Seq(
  "org.robolectric" % "robolectric" % "3.0-SNAPSHOT",
  "org.robolectric" % "android-all" % "5.0.0_r2-robolectric-1",
  "org.scalatest" %% "scalatest" % "2.2.5",
  "junit" % "junit" % "4.12",
  "org.apache.maven" % "maven-ant-tasks" % "2.1.3"
)

fork in Test := true

lazy val root = Project("robotest", file("."))

