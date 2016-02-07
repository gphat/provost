organization := "provost"

name := "provost"

version := "1.0.0"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.10.4")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

scalacOptions in Test ++= Seq("-Yrangepos")

resolvers ++= Seq("snapshots", "releases").map(Resolver.sonatypeRepo)

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"

publishTo := Some(Resolver.file("file",  new File( "/Users/gphat/src/mvn-repo/releases" )) )
