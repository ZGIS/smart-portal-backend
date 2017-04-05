// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += Resolver.sonatypeRepo("releases")

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.9")

// for autoplugins
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")

// code quality etc documentation plugins
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.4")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.3.5")

// site and docs publish,  com.typesafe.sbt:sbt-site:0.8.1 -> 1.0.0 ?
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.4")

// dependencies and security
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "0.1.1")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.2.0")

