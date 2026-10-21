#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
./gradlew :core:jsBrowserDistribution --console=plain
python3 - <<'PY'
from pathlib import Path
import shutil
root=Path.cwd()
out=root/'build/site'
if out.exists():shutil.rmtree(out) # This script owns this generated build directory.
shutil.copytree(root/'core/build/dist/js/productionExecutable',out)
(out/'.nojekyll').write_text('')
(out/'apk').mkdir()
for p in (root/'apk').glob('*'):
 if p.suffix=='.apk' or p.name=='SHA256SUMS':shutil.copy2(p,out/'apk'/p.name)
shutil.copytree(root/'docs/screenshots',out/'images')
if (root/'docs/video/CabinOS_ONE_demo.mp4').exists():
 (out/'videos').mkdir();shutil.copy2(root/'docs/video/CabinOS_ONE_demo.mp4',out/'videos/CabinOS_ONE_demo.mp4')
print('Static site:',out)
PY
