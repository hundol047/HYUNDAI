#!/usr/bin/env bash
# Creates an optimized, non-debuggable release signed with a dedicated team key.
# Signing material stays OUTSIDE the repository. Back it up securely for updates.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
# Keep release compilation and lint analysis in separate invocations (K2/UAST stability).
./gradlew :core:test :app:testDebugUnitTest --console=plain
./gradlew :app:assembleRelease --console=plain
./gradlew :app:lintDebug --console=plain
SIGNING_DIR="${CABINOS_SIGNING_DIR:-/workspace/.signing/cabinos-one}"
mkdir -p "$SIGNING_DIR"
chmod 700 "$SIGNING_DIR"
KEYSTORE="$SIGNING_DIR/release.p12"
PASSWORD_FILE="$SIGNING_DIR/password"
if [ ! -f "$KEYSTORE" ] && [ ! -f "$PASSWORD_FILE" ]; then
    python3 - "$PASSWORD_FILE" <<'PY'
import os, secrets, sys
fd=os.open(sys.argv[1],os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:f.write(secrets.token_urlsafe(48))
PY
    keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -alias cabinosone \
        -storepass:file "$PASSWORD_FILE" -keypass:file "$PASSWORD_FILE" \
        -keyalg RSA -keysize 3072 -validity 10000 \
        -dname 'CN=CabinOS ONE Release, OU=FutureLabs Team, C=KR'
    chmod 600 "$KEYSTORE"
fi
if [ ! -f "$KEYSTORE" ] || [ ! -f "$PASSWORD_FILE" ]; then
    printf 'Signing directory is incomplete; restore both the existing key and password file.\n' >&2
    exit 1
fi
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
