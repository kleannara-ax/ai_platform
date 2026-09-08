#!/bin/bash
set -a
[ -f /home/user/webapp/.env ] && . /home/user/webapp/.env
set +a
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
# 힙: 안전(SAFETY) 엑셀 일괄업로드가 사진이 많은 50~90MB 파일을 다룬다.
# 파싱 자체는 파일 기반이라 100MB 안쪽이면 되지만, 추출한 사진 원본을 저장 전까지
# 들고 있어야 해서 384m 로는 OutOfMemoryError 가 난다.
exec java -Xms256m -Xmx1024m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC \
  -jar /home/user/webapp/app/build/libs/platform-1.0.0.jar
