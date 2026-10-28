#!/usr/bin/env python3
"""Check captured Android output, not bundled sample files or UI playback labels."""
import array
import hashlib
import json
import math
import pathlib
import subprocess
import sys


def measure(raw):
    values = array.array('h', raw)
    if sys.byteorder != 'little':
        values.byteswap()
    rms = math.sqrt(sum(v*v for v in values) / len(values)) / 32768 if values else 0
    active = sum(1 for i in range(0, len(values), 1600) if math.sqrt(sum(v*v for v in values[i:i+1600])/len(values[i:i+1600]))/32768 > 10**(-65/20)) * .1
    return {'activeSecondsAboveMinus65Dbfs': round(active, 2), 'samples': len(values), 'rmsDbfs': 20*math.log10(max(rms, 1e-10)),
            'peak': max((abs(v) for v in values), default=0), 'pcmSha256': hashlib.sha256(raw).hexdigest()}


def verify(folder):
    video = folder / 'CabinOS_ONE_android_apk.mp4'
    probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(video)]))
    assert any(s['codec_type'] == 'audio' for s in probe['streams']), 'Missing audio track'
    assert any(s['codec_type'] == 'video' for s in probe['streams']), 'Missing video track'
    duration = float(probe['format']['duration'])
    windows = json.loads((folder / 'audio-windows.json').read_text())
    assert windows, 'No playback windows recorded'
    results = []
    for w in windows:
        # Avoid onset/UI timing and codec startup; test the middle of each interval.
        start = w['startSeconds'] + 1
        length = w['endSeconds'] - start - 1
        assert length > 1 and start + length < duration, 'Playback interval outside video'
        raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-ss', str(start), '-i', str(video), '-t', str(length),
                                       '-map', '0:a:0', '-ac', '1', '-ar', '16000', '-f', 's16le', '-'])
        stats = measure(raw)
        stats.update(w)
        stats['passed'] = stats['rmsDbfs'] < -65 if w['expectSilence'] else stats['rmsDbfs'] > -65 and stats['activeSecondsAboveMinus65Dbfs'] >= 1
        results.append(stats)
    report = {'capture': 'Live Android output via scrcpy; no replacement soundtrack', 'durationSeconds': duration,
              'streams': [{'type': s['codec_type'], 'codec': s['codec_name']} for s in probe['streams']], 'windows': results,
              'allPassed': all(r['passed'] for r in results), 'physicalSpeakerTest': False}
    (folder / 'audio-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    assert report['allPassed'], 'Captured audio is silent during playback or continues after stop'


if __name__ == '__main__':
    verify(pathlib.Path(sys.argv[1]))
