#!/usr/bin/env python3
"""Package the already built static site for HTTPS hosting; does not claim to deploy it."""
from pathlib import Path
import hashlib, zipfile, json, re
root=Path(__file__).resolve().parents[1]
site=root/'build/site'
assert all((site/name).is_file() for name in ['engine.js','voice-navigation.js','road-tiles.js'])
assert (site/'apk/퓨처랩스_CabinOS_ONE_release.apk').is_file()
out=root/'artifacts/releases';out.mkdir(parents=True,exist_ok=True)
version=re.search(r'versionName = "([^"]+)"',(root/'app/build.gradle.kts').read_text()).group(1)
archive=out/f'CabinOS_ONE_web_v{version}.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for f in sorted(site.rglob('*')):
        if f.is_file():z.write(f,f.relative_to(site))
    z.writestr('HOSTING.txt',f'CabinOS ONE {version} / FutureLabs\nUpload this directory to HTTPS static hosting.\nDo not open index.html as file://. Microphone/GPS require HTTPS and supported device services.\nGitHub repository: https://github.com/hundol047/HYUNDAI\nBundled MP4 is the historic 0.5 browser demo; see repository docs for the current validation scope.\n')
checks=out/'SHA256SUMS'
checks.write_text(''.join(f'{hashlib.sha256(f.read_bytes()).hexdigest()}  {f.name}\n' for f in [archive,root/'apk/퓨처랩스_CabinOS_ONE_release.apk']),encoding='utf-8')
print(json.dumps({'path':str(archive.relative_to(root)),'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()}))
