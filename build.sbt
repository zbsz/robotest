
name := "robotest"

organization := "com.geteit"

version := "0.4"

scalaVersion := "2.11.0"

crossScalaVersions := Seq("2.10.0", "2.11.0")

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

publishTo := {
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some(Resolver.file("snapshots", new File("../mvn-repo/snapshots" )) )
  else
    Some(Resolver.file("releases", new File("../mvn-repo/releases" )) )
}

libraryDependencies ++= Seq(
  "org.robolectric" % "robolectric" % "2.3",
  "org.robolectric" % "android-all" % "4.3_r2-robolectric-0" % "provided",
  "org.scalatest" %% "scalatest" % "2.1.6",
  "junit" % "junit" % "4.8.2",
  "org.easytesting" % "fest-assert-core" % "2.0M10" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test"
)

fork in Test := true

lazy val root = Project("robotest", file("."))

