#!/usr/bin/env python3
"""Isolated emulator diagnostic: restart the existing DDS emulator after boot.

Uses Android Emulator's built-in root console on loopback. Does not alter images,
permissions, SELinux, app data or vehicle readings. Not a production setup step.
"""
import json
from pathlib import Path
import re
import socket
import subprocess
import time
import urllib.request

out = Path('artifacts/pleos-device')
out.mkdir(parents=True, exist_ok=True)
serial = 'emulator-5554'
def adb(*args):
    return subprocess.run(['adb', '-s', serial, *args], capture_output=True, text=True, timeout=15)

for attempt in range(60):
    if adb('shell', 'getprop', 'sys.boot_completed').stdout.strip() == '1':
        break
    time.sleep(5)
else:
    raise SystemExit('No completed boot; no restart attempted')
fingerprint = adb('shell', 'getprop', 'ro.build.fingerprint').stdout.strip()
if not fingerprint.startswith('Pleos/') or 'release_connect_v2.1.0' not in fingerprint:
    raise SystemExit('Unexpected image; no restart attempted')
adb('forward', 'tcp:8088', 'tcp:8088')
def state():
    try:
        with urllib.request.urlopen('http://127.0.0.1:8088/vehicle/state', timeout=10) as response:
            return json.load(response)
    except OSError as e:
        return {'error':str(e)}
before = state()
(out/'console-before-restart.json').write_text(json.dumps(before,indent=2))
with socket.create_connection(('127.0.0.1',4444), timeout=10) as shell:
    shell.settimeout(1)
    def command(text):
        marker='PLEOS_END_'+str(time.time_ns())
        shell.sendall((text+"; printf '\\n%s\\n' "+marker+'\n').encode())
        data=b''
        deadline=time.monotonic()+20
        while time.monotonic()<deadline:
            try:
                chunk=shell.recv(65536)
            except socket.timeout:
                continue
            if not chunk: break
            data+=chunk
            if marker.encode() in data.replace(b'\r',b'').splitlines():
                return data.decode(errors='replace')
        raise RuntimeError('Emulator console command did not complete')
    identity=command('id; ip -4 addr show eth0')
    (out/'console-root-network.txt').write_text(identity)
    if 'uid=0' not in identity or not re.search(r'inet \d+\.\d+\.\d+\.\d+/',identity):
        raise SystemExit('Root console or configured eth0 unavailable; no restart attempted')
    definitions=command("grep '^service ' /vendor/etc/init/*.rc /apex/ai.pleos.hardware.vehicle.emulator.service/etc/*.rc")
    (out/'console-service-definitions.txt').write_text(definitions)
    candidates=[]
    for line in definitions.splitlines():
        m=re.search(r'(?:^|:)service\s+([\w.-]+)\s+(\S+)',line)
        if m and 'vehicle_api_dds_emulator' in m.group(2): candidates.append(m.group(1))
    candidates=sorted(set(candidates))
    if len(candidates)!=1:
        raise SystemExit('No unique DDS emulator init service found; no restart attempted: '+repr(candidates))
    name=candidates[0]
    result=command('setprop ctl.restart '+name+'; getprop init.svc.'+name)
    (out/'console-restart.txt').write_text(result)
    print('Requested restart of existing emulator init service:',name,flush=True)
    time.sleep(15)
    after=state()
    (out/'console-after-restart.json').write_text(json.dumps(after,indent=2))
    print('Console emulator active after restart:',after.get('controls',{}).get('emulator_active'),flush=True)
