#!/usr/bin/env python3
"""Install the unmodified official Pleos 2.1.0/API 34 image outside the repository.

The archive checksum is published in the official crp-sys-img.xml. Large zero-filled
image extents are written sparsely, preserving every byte while reducing disk use.
This installer does not make a generic emulator into a Pleos emulator.
"""
import argparse
import hashlib
import os
from pathlib import Path
import urllib.request
import zipfile

URL = "https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp_car_emulator.zip"
SHA1 = "b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa"
SIZE = 4087751964


def install(archive, sdk, avd_home):
    digest = hashlib.sha1()
    with archive.open("rb") as f:
        for block in iter(lambda: f.read(8*1024*1024), b""):
            digest.update(block)
    if archive.stat().st_size != SIZE or digest.hexdigest() != SHA1:
        raise SystemExit("Official archive checksum mismatch; installation stopped.")
    image = sdk / "system-images/android-34/pleos_car_emulator/x86_64"
    image.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for entry in z.infolist():
            relative = Path(entry.filename).relative_to("crp_avd_out_x86_64")
            if ".." in relative.parts:
                raise SystemExit("Unsafe archive path")
            target = image / relative
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            with z.open(entry) as source, target.open("wb") as out:
                for block in iter(lambda: source.read(1024*1024), b""):
                    if block == bytes(len(block)):
                        out.seek(len(block), 1)
                    else:
                        out.write(block)
                out.truncate(entry.file_size)
    avd = avd_home / "CabinOS_Pleos_21.avd"
    avd.mkdir(parents=True, exist_ok=True)
    (avd_home / "CabinOS_Pleos_21.ini").write_text(
        f"avd.ini.encoding=UTF-8\npath={avd}\ntarget=android-34\n")
    # Official source.properties specifies avd_config_ini.hw.ramSize=8192.
    # 4 GiB runs observed UIAutomator exit 137; retain the image default here.
    (avd / "config.ini").write_text(f"""AvdId=CabinOS_Pleos_21
avd.ini.encoding=UTF-8
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.ramSize=8192
hw.device.name=Pleos_Connect_v2_0
hw.device.manufacturer=42dot
hw.lcd.width=2560
hw.lcd.height=1440
hw.lcd.density=160
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.mainKeys=no
hw.audioInput=yes
hw.gps=yes
hw.multi_display_window=no
hw.hotplug_multi_display=no
image.sysdir.1={image}/
tag.id=android-automotive
tag.display=Automotive
disk.dataPartition.size=2G
showDeviceFrame=no
fastboot.forceColdBoot=yes
""")
    print(f"Verified official image: {SHA1}; AVD: {avd}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_HOME"), type=Path)
    parser.add_argument("--avd-home", required=True, type=Path)
    args = parser.parse_args()
    if not args.sdk:
        parser.error("Set ANDROID_HOME or --sdk")
    if not args.archive.exists():
        args.archive.parent.mkdir(parents=True, exist_ok=True)
        partial = args.archive.with_suffix(".download")
        with urllib.request.urlopen(URL, timeout=60) as response, partial.open("wb") as out:
            print(f"Official archive HTTP {response.status}", flush=True)
            for block in iter(lambda: response.read(8*1024*1024), b""):
                out.write(block)
        partial.rename(args.archive)
    install(args.archive.resolve(), args.sdk.resolve(), args.avd_home.resolve())


if __name__ == "__main__":
    main()
