#!/usr/bin/env python3
"""Collect and attempt reversible recovery on an isolated official Pleos AVD."""
import json
from pathlib import Path
import subprocess
import time
import urllib.request

out = Path('artifacts/pleos-device')
out.mkdir(parents=True, exist_ok=True)
def adb(*args, timeout=20):
    try:
        r=subprocess.run(['adb','-s','emulator-5554',*args],capture_output=True,text=True,timeout=timeout)
        return r.stdout+r.stderr
    except subprocess.TimeoutExpired as e:
        return 'TIMEOUT '+str(e)
def record(name, *args):
    result=adb(*args)
    (out/name).write_text(result)
    print(name, result[:1500], flush=True)
    return result
for attempt in range(90):
    if adb('shell','getprop','sys.boot_completed').strip()=='1':break
    time.sleep(5)
else:raise SystemExit('Boot did not complete')
if not adb('shell','getprop','ro.build.fingerprint').startswith('Pleos/'):
    raise SystemExit('Unexpected image')
adb('forward','tcp:8088','tcp:8088')
def state(name):
    try:
        with urllib.request.urlopen('http://127.0.0.1:8088/vehicle/state',timeout=10) as r:
            data=r.read()
        (out/name).write_bytes(data)
    except OSError as e:(out/name).write_text(str(e))
state('console-before-recovery.json')
record('recovery-system.txt','shell','id; getprop ro.debuggable; ip -4 addr; getprop | grep -E "init.svc.*(vehicle|netconfig)|service.netconfig"; ps -A | grep -E "vehicle|dds"')
record('recovery-package.txt','shell','dumpsys package ai.pleos.hardware.vehicle.emulator')
record('recovery-init.txt','shell','cat /apex/ai.pleos.hardware.vehicle.emulator.service/etc/*.rc; cat /system/bin/netconfig.sh')
record('recovery-adb-root.txt','root')
time.sleep(3)
record('recovery-restart.txt','shell','id; setprop ctl.restart vendor.vehicle_api_dds_emulator; getprop init.svc.vendor.vehicle_api_dds_emulator')
time.sleep(10)
state('console-after-recovery.json')
# Capture supported command help and executable accessibility after the network exists.
record('recovery-native-help.txt','shell','ls -l /apex/ai.pleos.hardware.vehicle.emulator.service/bin/; timeout 3 /apex/ai.pleos.hardware.vehicle.emulator.service/bin/ai.pleos.hardware.vehicle_api_dds_emulator --help')
