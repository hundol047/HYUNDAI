#!/usr/bin/env python3
"""Package the already built static site for HTTPS hosting; does not claim to deploy it."""
from pathlib import Path
import hashlib, zipfile, json
root=Path(__file__).resolve().parents[1]
site=root/'build/site'
assert (site/'engine.js').is_file() and (site/'voice-navigation.js').is_file()
assert (site/'apk/퓨처랩스_CabinOS_ONE_release.apk').is_file()
out=root/'artifacts/releases';out.mkdir(parents=True,exist_ok=True)
archive=out/'CabinOS_ONE_web_v0.10.0.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for f in sorted(site.rglob('*')):
        if f.is_file():z.write(f,f.relative_to(site))
    z.writestr('HOSTING.txt','CabinOS ONE 0.10.0 / FutureLabs\nUpload this directory to HTTPS static hosting.\nDo not open index.html as file://. Microphone/GPS require HTTPS and supported device services.\nGitHub repository: https://github.com/hundol047/HYUNDAI\nBundled MP4 is the historic 0.5 browser demo; see repository docs for the versioned voice demo.\n')
checks=out/'SHA256SUMS'
checks.write_text(''.join(f'{hashlib.sha256(f.read_bytes()).hexdigest()}  {f.name}\n' for f in [archive,root/'apk/퓨처랩스_CabinOS_ONE_release.apk']),encoding='utf-8')
print(json.dumps({'path':str(archive.relative_to(root)),'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()}))
