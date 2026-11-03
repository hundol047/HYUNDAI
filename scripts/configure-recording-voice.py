"""Choose an installed recording engine voice through its public Android UI."""
import argparse
import json
from pathlib import Path
import time
import uiautomator2

parser = argparse.ArgumentParser()
parser.add_argument('--speaker', type=int, required=True)
parser.add_argument('--output', required=True)
args = parser.parse_args()
if args.speaker not in (0, 2):
    raise SystemExit('Only the two comparison presets are supported')
device = uiautomator2.connect('emulator-5554')
try:
    hierarchy = device.dump_hierarchy()
    Path(args.output).with_suffix('.xml').write_text(hierarchy)
    if 'Speaker ID:' not in hierarchy:
        raise RuntimeError('Engine speaker selection field not available')
    field = device(className='android.widget.EditText', text='0')
    if field.count != 1:
        raise RuntimeError('Expected exactly one default speaker field')
    field.set_text(str(args.speaker))
    time.sleep(1)
    if device(className='android.widget.EditText', text=str(args.speaker)).count != 1:
        raise RuntimeError('Speaker choice was not applied')
    device.press('back')
    Path(args.output).write_text(json.dumps({
        'engine': 'com.k2fsa.sherpa.onnx.tts.engine',
        'speakerId': args.speaker,
        'selectionMethod': 'Real engine settings UI before APK recording',
        'bundledInCabinOs': False,
        'subjectiveWarmthCertified': False,
    }, indent=2) + '\n')
finally:
    device.stop_uiautomator()
