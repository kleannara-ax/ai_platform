#!/bin/bash
set -a
[ -f /home/user/webapp/.env ] && . /home/user/webapp/.env
set +a
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
exec java -Xms128m -Xmx384m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC \
  -jar /home/user/webapp/app/build/libs/platform-1.0.0.jar
