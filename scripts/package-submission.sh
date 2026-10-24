#!/usr/bin/env bash
# Creates an optimized, non-debuggable release signed with a dedicated team key.
# Signing material stays OUTSIDE the repository. Back it up securely for updates.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
if [ "${CABINOS_PLEOS_SDK:-true}" != "true" ]; then
    printf 'Submission packaging requires the official SDK profile; use Gradle directly for base development builds.\n' >&2
    exit 1
fi
PLEOS_FLAGS=(-PpleosSdk=true)
SIGNING_DIR="${CABINOS_SIGNING_DIR:?Set CABINOS_SIGNING_DIR to the existing team key directory outside the repository}"
SIGNING_DIR="$(realpath "$SIGNING_DIR")"
case "$SIGNING_DIR/" in "$PWD/"*) printf 'Signing material must stay outside the repository.\n' >&2; exit 1;; esac
KEYSTORE="$SIGNING_DIR/release.p12"
PASSWORD_FILE="$SIGNING_DIR/password"
if [ ! -f "$KEYSTORE" ] || [ ! -f "$PASSWORD_FILE" ]; then
    printf 'Restore the existing team release.p12 and password files; do not silently rotate the signing key.\n' >&2
    exit 1
fi
# Keep release compilation and lint analysis in separate invocations (K2/UAST stability).
./gradlew :core:test :app:testDebugUnitTest --console=plain "${PLEOS_FLAGS[@]}"
./gradlew :app:assembleRelease --console=plain "${PLEOS_FLAGS[@]}"
./gradlew :app:lintDebug --console=plain "${PLEOS_FLAGS[@]}"
mkdir -p apk
APK_PATH='apk/퓨처랩스_CabinOS_ONE_release.apk'
SIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
"$SIGNER" sign --ks "$KEYSTORE" --ks-key-alias cabinosone \
    --ks-pass "file:$PASSWORD_FILE" \
    --out "$APK_PATH" app/build/outputs/apk/release/app-release-unsigned.apk
"$SIGNER" verify --verbose --print-certs "$APK_PATH"
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -c -P 16 4 "$APK_PATH"
sha256sum "$APK_PATH" > apk/SHA256SUMS
printf '\nSubmission APK: %s\nSigning material is stored outside the repository. Back it up securely.\n' "$APK_PATH"
