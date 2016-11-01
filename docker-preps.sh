#!/usr/bin/env bash

APPNAME=$(cat build.sbt  | egrep "^name := " | cut -d "=" -f 2 | sed "s/\"*//g" | sed "s/\ *//g")
APPVERSION=$(cat build.sbt  | egrep "^version := " | cut -d "=" -f 2 | sed "s/\"*//g" | sed "s/\ *//g")

pwd

ls -lh target/universal

cp Dockerfile target/universal
cp portal-backend.k8s.yaml target/universal

ls -lh target/universal

cd target/universal && test -f ${APPNAME}-${APPVERSION}.tgz && tar -cvzf ${APPNAME}-${APPVERSION}-docker.tgz ${APPNAME}-${TRAVIS_BUILD_NUMBER}.tgz Dockerfile

ls -lh