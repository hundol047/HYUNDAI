#!/usr/bin/env bash
# Source from any directory: source /workspace/HYUNDAI/scripts/env.sh
if [ -d /workspace ] && [ -w /workspace ]; then
  CABINOS_CACHE_BASE="${CABINOS_CACHE_DIR:-/workspace/.cache}"
else
  CABINOS_CACHE_BASE="${CABINOS_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/cabinos}"
fi
export NPM_CONFIG_CACHE="${NPM_CONFIG_CACHE:-$CABINOS_CACHE_BASE/npm}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$CABINOS_CACHE_BASE/gradle}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$CABINOS_CACHE_BASE/android}"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/workspace/toolchains/android-sdk}}"
if [ -x /workspace/toolchains/jdk/usr/lib/jvm/java-21-openjdk-amd64/bin/javac ]; then
  export JAVA_HOME=/workspace/toolchains/jdk/usr/lib/jvm/java-21-openjdk-amd64
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
export PATH="$ANDROID_HOME/platform-tools:$PATH"
mkdir -p "$GRADLE_USER_HOME" "$ANDROID_USER_HOME"
