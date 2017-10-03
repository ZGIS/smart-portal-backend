// Comment to get more information during initialization
// logLevel := Level.Warn

resolvers ++= Seq(
  "Typesafe Repo"           at "http://repo.typesafe.com/typesafe/releases/",
  "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases",
  "jgit-repo" at "http://download.eclipse.org/jgit/maven",
  "allixender maven" at "https://dl.bintray.com/allixender/maven2",
  Resolver.sonatypeRepo("releases")
)

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.12")

// for autoplugins
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.4.0")
// addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

// code quality etc documentation plugins
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.4")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")

// site and docs publish,  com.typesafe.sbt:sbt-site:0.8.1 -> 1.0.0 ?
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.4")

// dependencies and security
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "0.1.7")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.1")

