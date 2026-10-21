#!/usr/bin/env bash
# Source from any directory: source /workspace/HYUNDAI/scripts/env.sh
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.cache/gradle}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/workspace/.cache/android}"
export ANDROID_HOME="${ANDROID_HOME:-/workspace/toolchains/android-sdk}"
if [ -x /workspace/toolchains/jdk/usr/lib/jvm/java-21-openjdk-amd64/bin/javac ]; then
  export JAVA_HOME=/workspace/toolchains/jdk/usr/lib/jvm/java-21-openjdk-amd64
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
export PATH="$ANDROID_HOME/platform-tools:$PATH"
mkdir -p "$GRADLE_USER_HOME" "$ANDROID_USER_HOME"
