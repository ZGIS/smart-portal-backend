#!/usr/bin/env bash

./bin/activator clean coverage test coverageReport copyCoverage scapegoat check makeSite ghpagesPushSite

