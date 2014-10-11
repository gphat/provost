organization := "provost"

name := "provost"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.2"

crossScalaVersions := Seq("2.10.4")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

scalacOptions in Test ++= Seq("-Yrangepos")

resolvers ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo)

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies += "org.specs2" %% "specs2" % "2.4.6" % "test"

publishTo := Some(Resolver.file("file",  new File( "/Users/gphat/src/mvn-repo/releases" )) )