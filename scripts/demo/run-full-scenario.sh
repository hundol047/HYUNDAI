#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
: "${PLEOS_AVD:?Set PLEOS_AVD to the official Pleos Connect 2.1.0 AVD name}"
[[ -r /dev/kvm && -w /dev/kvm ]] || { echo 'KVM is required; run on a KVM-capable PC.' >&2; exit 1; }
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
serial=emulator-5554
if adb -s "$serial" get-state >/dev/null 2>&1; then
  echo 'Close emulator-5554 first. A verified cold boot is required.' >&2; exit 1
fi
adb start-server
emulator -avd "$PLEOS_AVD" -port 5554 -no-snapshot-load -no-snapshot-save > /tmp/cabinos-full-scenario-boot.log 2>&1 &
emulator_pid=$!
trap 'kill "$emulator_pid" 2>/dev/null || true' EXIT
adb -s "$serial" wait-for-device
for ((attempt=0;attempt<180;attempt++)); do
  [[ $(adb -s "$serial" shell getprop sys.boot_completed | tr -d '\r') == 1 ]] && break
  sleep 2
done
[[ $(adb -s "$serial" shell getprop sys.boot_completed | tr -d '\r') == 1 ]] || { echo 'Boot timeout' >&2; exit 1; }
python3 scripts/demo/full_scenario.py --serial "$serial" \
  --apk "${1:-apk/퓨처랩스_CabinOS_ONE_release.apk}" \
  --output "${2:-artifacts/full-scenario-$(date -u +%Y%m%dT%H%M%SZ)}"
