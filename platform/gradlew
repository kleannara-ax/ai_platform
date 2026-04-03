#!/bin/sh
# Gradle Wrapper stub
# This wrapper will download Gradle if not present

APP_NAME="Gradle"
GRADLE_VERSION="8.7"
GRADLE_HOME="${HOME}/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_BIN="${GRADLE_HOME}/gradle-${GRADLE_VERSION}/bin/gradle"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

if [ ! -f "${GRADLE_BIN}" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p "${GRADLE_HOME}"
    curl -sL "${DIST_URL}" -o "${GRADLE_HOME}/gradle.zip"
    cd "${GRADLE_HOME}" && unzip -qo gradle.zip && cd - > /dev/null
fi

exec "${GRADLE_BIN}" "$@"
