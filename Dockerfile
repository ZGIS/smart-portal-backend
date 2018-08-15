
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
# ${?JAVA_OPTS}
# ${?PG_DBNAME}
# ${?PG_USER}
# ${?PG_PASSWORD}
# ${?CSWI_URL}
# ${?PYCSW_URL}
# ${?UPLOAD_DATA_DIR}
# ${?SENDGRID_API_KEY}
# ${?APP_TIMEZONE}
# ${?GOOGLE_RECAPTCHA_SECRET}
# ${?GOOGLE_CLIENT_SECRET}
# ${?GOOGLE_BUCKET_NAME}
# ${?GOOGLE_PROJECT_ID}

# ${?BASE_URL}
# ${?VOCAB_URL}
# ${?ADMIN_URL}

CMD [ "/smart-portal-backend-1.0-SNAPSHOT/bin/smart-portal-backend", \
    "-Dconfig.resource=application.conf", \
    "-Dhttp.address=0.0.0.0", \
    "-Dhttp.port=9000", \
    "-Dplay.evolutions.db.default.autoApply=true", \
    "-Dplay.evolutions.db.default.autoApplyDowns=true", \
    "-Dlogger.resource=logback-stdout.xml"]
