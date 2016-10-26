## sbt plugins

### scalastyle-sbt

- is activated default in compile and test

```scala
sbt scalastyle
```

### sbt-scapegoat

- uses scapegoat version 1.2.0 and provides not only style but also static code analysis
- generates report under `src/site/scapegoat` and is so included in `ghpagesPushSite`

```scala
sbt scapegoat
```

### sbt-scoverage

- instrumentation is activated, but the HTML report needs to be manually generated
- the output parameter doesn't seem to be adjustable, so I added a copy task
- I think `coverage` needs to be run before/together with tests
- if you activated `coverage` in your activator/sbt console you should deactivate it with `coverageOff`  before `run`

```scala
sbt coverage test
sbt coverageReport
sbt copyCoverage
```

### sbt-site

- Don't use `publishSite` from sbt-site, but the `ghpagesPushSite` from sbt-ghpages.
- sbt-site in version 0.8.1 seems to come pre-packaged with `activator` and gets evicted from the newer version declared in `plugins.sbt`
- new sbt-site 1.0.0 config incompatible with activator sbt-site bundle 0.8.1
- preps in `makesite.sh`

```scala
sbt clean coverage test coverageReport copyCoverage scapegoat check makeSite ghpagesPushSite
sbt makeSite
sbt previewSite
```

### sbt-ghpages

- pusblish to gh-pages branch of the project (from what was generated from `sbt site` in `src/site` but including ScalaDocs Api)

```scala
sbt ghpagesPushSite
```

### sbt-dependency-check

- OWASP Dependency-Check and Vulnerability is an open source tool performing a best effort analysis of 3rd party dependencies.
- first runs will take a while (up to several minutes) for downloadloading the CVE lists from nist.gov ...

```scala
sbt check
```

### sbt-dependency-graph

- `dependencyTree`: Shows an ASCII tree representation of the project's dependencies
- `dependencyList`: Shows a flat list of all transitive dependencies on the sbt console (sorted by organization and name)
- `dependencyLicenseInfo`: show dependencies grouped by declared license

- `dependencyGraphMl`: Generates a .graphml file with the project's dependencies to target/dependencies-<config>.graphml. Use e.g. yEd to format the graph to your needs.
- `dependencyDot`: Generates a .dot file with the project's dependencies to target/dependencies-<config>.dot. Use graphviz to render it to your preferred graphic format.

- `ivyReport`: let's ivy generate the resolution report for you project. Use show ivyReport for the filename of the generated report

- `dependencyBrowseGraph`: Opens a browser window with a visualization of the dependency graph (courtesy of graphlib-dot + dagre-d3).

```scala
sbt dependencyGraph
sbt dependencyTree
sbt dependencyLicenseInfo
```


### sbt-native-packager

```scala
sbt docker:publishLocal
```

### sbt-release TODO

```scala
sbt
```
