#!/usr/bin/env bash
# Produces a release-variant APK signed with the standard DEVELOPMENT key only.
# Do not submit this as production-signed without checking competition requirements.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
./gradlew :app:assembleDebug :app:assembleRelease --console=plain
mkdir -p artifacts
SIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
"$SIGNER" sign --ks "$ANDROID_USER_HOME/debug.keystore" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out artifacts/CabinOS_ONE_release.apk app/build/outputs/apk/release/app-release-unsigned.apk
"$SIGNER" verify --verbose artifacts/CabinOS_ONE_release.apk
cp app/build/outputs/apk/debug/app-debug.apk artifacts/CabinOS_ONE_demo.apk
cp app/build/outputs/apk/release/app-release-unsigned.apk artifacts/CabinOS_ONE_release_unsigned.apk
sha256sum artifacts/*.apk > artifacts/SHA256SUMS
printf '\nCabinOS_ONE_release.apk is signed with a DEVELOPMENT certificate; production signing is outstanding.\n'
