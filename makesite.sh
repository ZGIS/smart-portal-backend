#!/usr/bin/env bash

./bin/activator clean coverage test coverageReport copyCoverage scapegoat dependencyCheckAggregate dependencyUpdatesReport makeSite ghpagesPushSite

