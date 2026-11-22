#!/bin/sh
# Manual Gradle Wrapper launcher for Android builds (no scheduled or background actions).
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER="$BASE_DIR/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER" ]; then
  echo 'Missing gradle-wrapper.jar. Get the complete repository from GitHub or use installed Gradle 8.13.' >&2
  exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD="java"
fi
exec "$JAVA_CMD" -classpath "$WRAPPER" org.gradle.wrapper.GradleWrapperMain "$@"
