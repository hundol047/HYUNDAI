#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
npm ci --prefix scripts/web --ignore-scripts --no-audit --no-fund
./gradlew :core:jsBrowserDistribution --console=plain
python3 - <<'PY'
from pathlib import Path
import shutil, re
root=Path.cwd()
out=root/'build/site'
if out.exists():shutil.rmtree(out) # This script owns this generated build directory.
shutil.copytree(root/'core/build/dist/js/productionExecutable',out)
(out/'.nojekyll').write_text('')
(out/'preview').mkdir()
for p in (root/'preview').iterdir():
 if p.is_file() and (p.suffix in {'.apk','.png','.mp4','.json'} or p.name=='SHA256SUMS'):
  shutil.copy2(p,out/'preview'/p.name)
leaflet=root/'scripts/web/node_modules/leaflet/dist'
(out/'vendor/leaflet').mkdir(parents=True)
for name in ['leaflet.js','leaflet.css']:shutil.copy2(leaflet/name,out/'vendor/leaflet'/name)
shutil.copytree(leaflet/'images',out/'vendor/leaflet/images')
shutil.copy2(root/'scripts/web/node_modules/leaflet/LICENSE',out/'vendor/leaflet/LICENSE')
(out/'apk').mkdir()
for p in (root/'apk').glob('*'):
 if p.suffix=='.apk' or p.name=='SHA256SUMS':shutil.copy2(p,out/'apk'/p.name)
# Ship gallery/poster assets, keeping validation-only screenshots in the repo.
# This avoids growing every user's download when a new road audit is added.
html=(out/'index.html').read_text()
images={name+'.png' for name in re.findall(r'data-image="([a-z0-9-]+)"',html)}
images.update(re.findall(r'(?:src|poster)="images/([^"/]+)"',html))
(out/'images').mkdir()
for name in sorted(images):shutil.copy2(root/'docs/screenshots'/name,out/'images'/name)
videos=list((root/'docs/video').glob('*.mp4'))
if videos:
 (out/'videos').mkdir()
 for video in videos:shutil.copy2(video,out/'videos'/video.name)
print('Static site:',out)
PY
