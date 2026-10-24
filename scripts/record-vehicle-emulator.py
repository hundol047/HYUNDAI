#!/usr/bin/env python3
"""Record an installed app from ADB; never renders screenshots into a fake video.

Install the -PpleosSdk=true APK on the official Pleos Connect Emulator first.
While recording, operate the official Virtual Console at localhost:8088.
Image identity and visual correctness still require human review of the artifacts.
"""
import argparse
import datetime as dt
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import time


def evaluate(events):
    real = [e for e in events if e.get("event") == "vehicle_read_state" and e.get("source") == "VEHICLE"]
    connected = [e for e in real if e.get("connected") is True]
    brake = [e for e in real if e.get("pleosSdkIncluded") is True and
             type(e.get("brakePressed")) is bool and type(e.get("sdkReadAtMs")) is int and
             0 <= e.get("atMs", 0) - e["sdkReadAtMs"] <= 4000]
    unsafe = [e for e in real if e.get("parkedAllowed") is True and
              (e.get("connected") is not True or e.get("gear") != "PARK" or e.get("speedKph") != 0)]
    return {
        "realSourceObserved": bool(real),
        "noSimulationObserved": bool(events) and not any(e.get("source") == "SIMULATION" for e in events),
        "parkUnlockObserved": any(e.get("gear") == "PARK" and e.get("speedKph") == 0 and e.get("parkedAllowed") is True for e in connected),
        "driveAtZeroLocked": any(e.get("gear") == "DRIVE" and e.get("speedKph") == 0 and e.get("parkedAllowed") is False for e in connected),
        "movingLocked": any(e.get("speedKph", 0) > 0 and e.get("parkedAllowed") is False for e in connected),
        "unknownLocked": any(e.get("connected") is False and e.get("parkedAllowed") is False for e in real),
        "officialSdkReadObserved": bool(brake),
        "brakeBothStatesObserved": {e["brakePressed"] for e in brake} == {False, True},
        "noUnsafeParkingObserved": not unsafe,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit ADB emulator serial, e.g. emulator-5554")
    parser.add_argument("--apk", required=True, type=pathlib.Path, help="The exact APK already installed on the emulator")
    parser.add_argument("--seconds", type=int, default=90)
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("artifacts/pleos-device-recording"))
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial) or not 30 <= args.seconds <= 180:
        parser.error("Use an emulator serial and a recording duration from 30 to 180 seconds.")
    adb = ["adb", "-s", args.serial]

    def run(*command, timeout=30, check=True):
        return subprocess.run(adb + list(command), capture_output=True, text=True, timeout=timeout, check=check)

    if run("get-state").stdout.strip() != "device":
        raise SystemExit("Emulator is not ready.")
    features = run("shell", "pm", "list", "features").stdout
    if "android.hardware.type.automotive" not in features:
        raise SystemExit("This is not an Automotive emulator. No Pleos recording will be claimed.")
    package = run("shell", "dumpsys", "package", "io.cabinos.one").stdout
    if "versionName=" not in package:
        raise SystemExit("Install the official-SDK app build first.")
    args.output.mkdir(parents=True, exist_ok=False)
    paths = run("shell", "pm", "path", "io.cabinos.one").stdout.strip().splitlines()
    if len(paths) != 1 or not paths[0].startswith("package:") or not args.apk.is_file():
        raise SystemExit("A single installed APK and its local source file are required.")
    installed = args.output / "installed-base.apk"
    run("pull", paths[0].removeprefix("package:"), str(installed), timeout=60)
    apk_sha = hashlib.sha256(installed.read_bytes()).hexdigest()
    if apk_sha != hashlib.sha256(args.apk.read_bytes()).hexdigest():
        raise SystemExit("Installed APK differs from --apk; install the intended build before recording.")
    installed.unlink()
    device = {key: run("shell", "getprop", key).stdout.strip() for key in
              ["ro.build.fingerprint", "ro.build.version.sdk", "ro.product.manufacturer", "ro.product.model", "ro.product.name"]}
    (args.output / "device.json").write_text(json.dumps(device, indent=2) + "\n")
    (args.output / "package.txt").write_text(package)
    old_level = run("shell", "getprop", "log.tag.CabinVehicle").stdout.strip()
    remote = f"/sdcard/cabinos-vehicle-{int(time.time())}.mp4"
    forwards = [line.split() for line in run("forward", "--list").stdout.splitlines()]
    existing = [item for item in forwards if len(item) == 3 and item[1] == "tcp:8088"]
    if any(item != [args.serial, "tcp:8088", "tcp:8088"] for item in existing):
        raise SystemExit("Port 8088 already forwards to another target; retain that forwarding and choose the intended emulator.")
    own_forward = not existing
    if own_forward:
        run("forward", "tcp:8088", "tcp:8088")
    print("Use the official Virtual Console at http://localhost:8088 during recording.", flush=True)
    print("Sequence: P + 0 speed; brake on/off; D + 0; D + moving; P + 0; background/resume.", flush=True)
    log_process = None
    app_pid = None
    started = dt.datetime.now(dt.timezone.utc).isoformat()
    try:
        run("shell", "setprop", "log.tag.CabinVehicle", "INFO")
        with (args.output / "vehicle-logcat.txt").open("w") as log:
            log_process = subprocess.Popen(adb + ["logcat", "-v", "threadtime", "-T", "1", "CabinVehicle:I", "*:S"], stdout=log, stderr=subprocess.STDOUT)
            with (args.output / "screenrecord.txt").open("w") as errors:
                recorder = subprocess.Popen(adb + ["shell", "screenrecord", "--time-limit", str(args.seconds), remote], stdout=errors, stderr=subprocess.STDOUT)
                try:
                    run("shell", "am", "force-stop", "io.cabinos.one")
                    run("shell", "am", "start", "-W", "-n", "io.cabinos.one/.MainActivity")
                    app_pid = run("shell", "pidof", "io.cabinos.one").stdout.strip()
                    recorder.wait(timeout=args.seconds + 30)
                    if recorder.returncode:
                        raise RuntimeError("Device screenrecord failed; inspect screenrecord.txt")
                finally:
                    if recorder.poll() is None:
                        recorder.terminate()
                        recorder.wait(timeout=10)
            run("pull", remote, str(args.output / "device-screen.mp4"), timeout=60)
            run("shell", "rm", remote)
    finally:
        if log_process:
            log_process.terminate()
            log_process.wait(timeout=10)
        run("shell", "setprop", "log.tag.CabinVehicle", old_level or "WARN", check=False)
        if own_forward:
            run("forward", "--remove", "tcp:8088", check=False)
    events = []
    for line in (args.output / "vehicle-logcat.txt").read_text().splitlines():
        columns = line.split()
        if len(columns) < 3 or columns[2] != app_pid:
            continue
        start = line.find('{')
        if start >= 0:
            try:
                event = json.loads(line[start:])
                if event.get("event") == "vehicle_read_state":
                    events.append(event)
            except json.JSONDecodeError:
                pass
    checks = evaluate(events)
    result = {"startedAtUtc": started, "captureMethod": "adb shell screenrecord (continuous device frames)",
              "device": device, "apkSha256": apk_sha, "appPid": app_pid, "checks": checks, "events": events,
              "pleosImageIdentity": "manual_review_required_against_official_SDK_Manager_image",
              "visualReview": "pending", "submissionReady": False}
    (args.output / "evidence.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(checks, ensure_ascii=False, indent=2))
    print("Review the actual video and official image identity; log checks alone do not certify compatibility.")
    return 0 if all(checks.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
