#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console=plain
mkdir -p artifacts
cp app/build/outputs/apk/debug/app-debug.apk artifacts/CabinOS_ONE_demo.apk
cp app/build/outputs/apk/release/app-release-unsigned.apk artifacts/CabinOS_ONE_release_unsigned.apk
printf '\nBuild complete: debug-signed demo + unsigned release. No production signing key is stored.\n'
