#!/usr/bin/env bash
# Cloud-only: register the system's existing public CA certificates for Chromium.
# Does not disable TLS verification or replace existing certificate/key entries.
set -euo pipefail
CABIN_NSS_DIRECTORY="${CABIN_BROWSER_NSS_DIR:-/home/agent/.pki/nssdb}"
python3 - "$CABIN_NSS_DIRECTORY" <<'PY'
import subprocess,sys
from pathlib import Path
store='sql:'+sys.argv[1]
for cert in Path('/usr/local/share/ca-certificates').glob('*.crt'):
 name='CabinOS existing OS trust '+cert.stem
 exists=subprocess.run(['certutil','-L','-d',store,'-n',name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
 if not exists:subprocess.run(['certutil','-A','-d',store,'-n',name,'-t','C,,','-i',str(cert)],check=True)
print('Chromium trusts the existing public OS CA roots; TLS verification remains enabled.')
PY
