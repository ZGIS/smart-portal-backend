
FROM openjdk:8-jre

# that's me!
MAINTAINER Alex K, allixender@googlemail.com

LABEL app="smart-portal-backend"
LABEL version="1.0-SNAPSHOT"
LABEL repo="https://github.com/ZGIS/smart-portal-backend"
LABEL build_number=TRAVIS_BUILD_NUMBER

RUN apt-get update -y \
  && apt-get install -y --no-install-recommends \
	ca-certificates curl wget pwgen unzip openssl \
  && rm -rf /var/lib/apt/lists/*

ADD smart-portal-backend-1.0-SNAPSHOT.tgz /

ENV JAVA_OPTS "-Xms192m -Xmx398m -XX:+UseParallelGC -XX:+UseParallelOldGC"

EXPOSE 9000

# -Dpidfile.path=$APP_HOME/running.pid
# -Dpidfile.path=/tmp/play.pid
# -Dapplication.base_url=http://test.smart-project.info/
# -Dfilepath.data=/data

# We need envs for
# ${?APPLICATION_SECRET}
# ${?PG_DBNAME}
# ${?PG_USER}
# ${?PG_PASSWORD}
# ${?EMAIL_USERNAME}
# ${?EMAIL_PASSWORD}
# ${?APP_TIMEZONE}

CMD [ "/smart-portal-backend-1.0-SNAPSHOT/bin/smart-portal-backend", \
    "-Dconfig.resource=application.conf", \
    "-Dhttp.address=0.0.0.0", \
    "-Dhttp.port=9000", \
    "-DapplyEvolutions.default=true", \
    "-Dlogger.resource=logback-stdout.xml"]
