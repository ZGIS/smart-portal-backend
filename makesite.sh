#!/usr/bin/env bash

activator clean coverage test coverageReport copyCoverage scapegoat check makeSite ghpagesPushSite
