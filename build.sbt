/*
 * Copyright (c) 2011-2017 Interfaculty Department of Geoinformatics, University of
 * Salzburg (Z_GIS) & Institute of Geological and Nuclear Sciences Limited (GNS Science)
 * in the SMART Aquifer Characterisation (SAC) programme funded by the New Zealand
 * Ministry of Business, Innovation and Employment (MBIE)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker._
import com.sksamuel.scapegoat.sbt._
import com.sksamuel.scapegoat.sbt.ScapegoatSbtPlugin.autoImport._
import scoverage.ScoverageKeys._
import java.util.Date

name := """smart-portal-backend"""

version := "1.0-SNAPSHOT"

// new sbt-site 1.0.0 config SiteScaladocPlugin incompatible with activator sbt-site bundle 0.8.1
lazy val root = (project in file(".")).enablePlugins(PlayScala, BuildInfoPlugin, SiteScaladocPlugin, JavaAppPackaging, DockerPlugin)

scalaVersion := "2.11.8"

val googleVersion = "1.22.0"
val googleCloudVersion = "0.8.0"
val postgresDriver = "9.4.1211.jre7"
val akkaVersion = "2.4.11"

// google-cloud versions 0.8.0 ... 0.8.3-beta

libraryDependencies ++= Seq(
  jdbc,
  evolutions,
  cache,
  ws,
  filters,
  "info.smart-project" %% "smart-owc-geojson" % "1.1.0",
  "com.sendgrid" % "sendgrid-java"  % "3.1.0",

  "com.typesafe.play" %% "anorm" % "2.5.2",
  "org.postgresql" % "postgresql" % postgresDriver,

  // "com.vividsolutions" % "jts-core" % "1.14.0",
  "org.locationtech.spatial4j" % "spatial4j" % "0.6",
  // "org.noggit" % "noggit" % "0.7",

  "com.google.oauth-client" % "google-oauth-client" % googleVersion,
  "com.google.oauth-client" % "google-oauth-client-java6" % googleVersion,
  "com.google.http-client" % "google-http-client" % googleVersion,
  "com.google.http-client" % "google-http-client-jackson2" % googleVersion,
  "com.google.api-client" % "google-api-client" % googleVersion,

  "com.google.cloud" % "google-cloud" % "0.8.0" excludeAll(ExclusionRule(organization = "io.netty")),

  "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,

  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,

  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  specs2 % Test,
  "de.leanovate.play-mockws" %% "play-mockws" % "2.5.1" % Test,
  "com.github.unisay" %% "mockserver-client-scala" % "0.2.1" % Test
)

resolvers += Resolver.bintrayRepo("hmrc", "releases")

resolvers += Resolver.bintrayIvyRepo("allixender", "ivy2")

scalacOptions in ThisBuild ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint:_", // recommended additional warnings
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-unused-import", // Warn when imports are unused
  "-Ywarn-unused", // Warn when local and private vals, vars, defs, and types are unused
  "-Ywarn-numeric-widen", // Warn when numerics are widened, Int and Double, for instance
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-dead-code", // Warn when dead code is identified
  "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`
  "-Ywarn-nullary-override", //  Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit", // Warn when nullary methods return Unit
  "-language:reflectiveCalls",
  "-language:postfixOps" // too lazy?
)

fork in run := true

javaOptions in Test +="-Dlogger.resource=logback-stdout-test.xml"

// -----------------
// coverage, style and dependency checks

val genSiteDir = "src/site/generated"

// Scala style task for compile, config file is scalastyle-config.xml
lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")
compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value
(compile in Compile) <<= (compile in Compile) dependsOn compileScalastyle

// Scala style task to run with tests
lazy val testScalastyle = taskKey[Unit]("testScalastyle")
testScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Test).toTask("").value
(test in Test) <<= (test in Test) dependsOn testScalastyle

scapegoatVersion := "1.2.1"

scapegoatOutputPath := genSiteDir + "/scapegoat"

// scalacOptions only for the scapegoat task
scalacOptions in Scapegoat ++= Seq("-P:scapegoat:overrideLevels:TraversableHead=Warning:OptionGet=Warning")

// disabling coverage for standard tasks, call explicit in test runs / publish site
// coverageEnabled := true

lazy val coverageCopyTask = TaskKey[Unit]("copy-coverage")

coverageCopyTask := {
  println(s"Copying: ./target/scala-2.11/scoverage-report/ to $genSiteDir")
  val result = Seq("cp", "-r", "./target/scala-2.11/scoverage-report", genSiteDir + "/scoverage-report") !!
}

// sbt-dependency-graph
dependencyCheckOutputDirectory := Some(file(genSiteDir + "/dep-sec"))

// Use e.g. yEd to format the graph
dependencyGraphMLFile := file(genSiteDir + "/dep-sec/dependencies.graphml")

// Use e.g.graphviz to render
dependencyDotFile := file(genSiteDir + "/dep-sec/dependencies.dot")

// updates of libs and dependencies
dependencyUpdatesFilter -= moduleFilter(organization = "org.scala-lang")
dependencyUpdatesReportFile := file(genSiteDir + "/dep-sec/dependency-updates.txt")

// -----------------
// publish docs on github
// new sbt-site 1.0.0 config incompatible with activator sbt-site bundle 0.8.1
includeFilter in makeSite := "*.txt" | "*.html" | "*.md" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js"

// will automatically be included with makeSite
// Puts Scaladoc output in `target/site/api/latest`
siteSubdirName in SiteScaladoc := "api/latest"

previewLaunchBrowser := false

ghpages.settings

git.remoteRepo := "git@github.com:ZGIS/smart-portal-backend.git"

// -----------------
// packaging options
val buildNumber = sys.props.getOrElse("buildNumber", default = System.currentTimeMillis().toString)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, "buildNumber" -> buildNumber)

buildInfoPackage := "utils"

buildInfoObject := "BuildInfo"

version in Docker := version.value

maintainer in Docker := "allixender@gmail.com"

dockerBaseImage in Docker := "java:8-jre"

dockerBaseImage := "java:8-jre"

sources in (Compile, doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

javaOptions in Universal ++= Seq(
  // others will be added as app parameters
  "-DapplyEvolutions.default=true",
  "-Dconfig.resource=application.conf",
  "-Dlogger.resource=logback-stdout.xml"
  //"-Dapplication.base_url=http://test.smart-project.info/"
)
