val scala3Lts = "3.6.4"
val scala3Latest = "3.8.3"

val scalaSqlVersion = "0.3.0"
val zioVersion = "2.1.25"
val ironVersion = "3.3.0"

ThisBuild / organization := "org.typebricks"
ThisBuild / scalaVersion := scala3Latest
ThisBuild / crossScalaVersions := Seq(scala3Lts, scala3Latest)

ThisBuild / sonatypeCredentialHost := "central.sonatype.com"
ThisBuild / licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
ThisBuild / homepage := Some(url("https://github.com/typebricks/scalasql-zio"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/typebricks/scalasql-zio"),
    "scm:git@github.com:typebricks/scalasql-zio.git"
  )
)
ThisBuild / developers := List(
  Developer("ivan-klass", "Ivan Klass", "klass.ivanklass@gmail.com", url("https://github.com/ivan-klass"))
)
ThisBuild / Compile / doc / sources := Seq.empty

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / scalacOptions := Seq(
  "-Wunused:all",
  "-Wconf:msg=unused import&origin=.*dialectSelf.*:s",
  "-Wvalue-discard",
  "-feature",
  "-Werror",
  "-deprecation",
  "-unchecked"
)

lazy val root = (project in file("."))
  .aggregate(core)
  .settings(
    name := "scalasql-zio",
    publish / skip := true
  )

lazy val core = (project in file("modules/core"))
  .settings(
    name := "scalasql-zio-core",
    description := "ZIO interop layer for lihaoyi's scalasql",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "scalasql" % scalaSqlVersion,
      "dev.zio" %% "zio" % zioVersion,
      // Test
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      "org.xerial" % "sqlite-jdbc" % "3.51.3.0" % Test,
      "io.github.iltotore" %% "iron" % ironVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

addCommandAlias("lint", "scalafmtAll; scalafixAll") ++ addCommandAlias(
  "lintCheck",
  "scalafmtCheckAll; scalafixAll --check;"
)
