#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
npm ci --prefix scripts/web --ignore-scripts --no-audit --no-fund
./gradlew :core:jsBrowserDistribution --console=plain
python3 - <<'PY'
from pathlib import Path
import shutil
root=Path.cwd()
out=root/'build/site'
if out.exists():shutil.rmtree(out) # This script owns this generated build directory.
shutil.copytree(root/'core/build/dist/js/productionExecutable',out)
(out/'.nojekyll').write_text('')
leaflet=root/'scripts/web/node_modules/leaflet/dist'
(out/'vendor/leaflet').mkdir(parents=True)
for name in ['leaflet.js','leaflet.css']:shutil.copy2(leaflet/name,out/'vendor/leaflet'/name)
shutil.copytree(leaflet/'images',out/'vendor/leaflet/images')
shutil.copy2(root/'scripts/web/node_modules/leaflet/LICENSE',out/'vendor/leaflet/LICENSE')
(out/'apk').mkdir()
for p in (root/'apk').glob('*'):
 if p.suffix=='.apk' or p.name=='SHA256SUMS':shutil.copy2(p,out/'apk'/p.name)
shutil.copytree(root/'docs/screenshots',out/'images')
if (root/'docs/video/CabinOS_ONE_demo.mp4').exists():
 (out/'videos').mkdir();shutil.copy2(root/'docs/video/CabinOS_ONE_demo.mp4',out/'videos/CabinOS_ONE_demo.mp4')
print('Static site:',out)
PY
