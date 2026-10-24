#!/usr/bin/env bash
# Build a separately staged, verified team candidate. Never overwrite apk/ or publish.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
if [ "${CABINOS_PLEOS_SDK:-true}" != "true" ]; then
    printf 'Submission packaging requires the official SDK profile.\n' >&2
    exit 1
fi
: "${CABINOS_SIGNING_DIR:?Set the existing team key directory outside the repository}"
: "${CABINOS_TEAM_CERT_SHA256:?Set the separately trusted team certificate SHA-256}"
python3 scripts/prepare-submission.py \
    --signing-dir "$CABINOS_SIGNING_DIR" \
    --certificate-sha256 "$CABINOS_TEAM_CERT_SHA256" \
    --output "${CABINOS_CANDIDATE_DIR:-artifacts/submission-candidate}"
