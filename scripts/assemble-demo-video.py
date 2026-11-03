#!/usr/bin/env python3
"""Join verified, continuous APK recordings; retain their actual Android audio."""
import hashlib
import json
import pathlib
import subprocess
import sys

SCENARIOS = [
    ('overview', '주요 기능'), ('stop-plan', '정차 계획'),
    ('navigation-review', '내비게이션'), ('place-photos', '장소와 사진'),
    ('audio-review', '배경 소리 6종'),
]


def run(*args):
    return subprocess.check_output(args, timeout=300)


def main(root, output, apk):
    output.mkdir(parents=True, exist_ok=True)
    apk_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
    chapters, metadata, offset = [], [';FFMETADATA1'], 0
    for index, (scenario, title) in enumerate(SCENARIOS):
        folder = root / ('latest-demo-' + scenario)
        evidence = json.loads((folder / 'evidence.json').read_text())
        assert evidence['apkSha256'] == apk_hash, 'Recording APK differs from published APK'
        assert evidence['checks'] and all(c['result'] == 'passed' for c in evidence['checks']), scenario
        if scenario in ('audio-review', 'navigation-review'):
            assert json.loads((folder / 'audio-verification.json').read_text())['allPassed'], scenario
        source = folder / 'CabinOS_ONE_android_apk.mp4'
        normalized = output / f'chapter-{index}.mp4'
        # Preserve all frames and sound, including portrait changes, inside a fixed canvas.
        run('ffmpeg', '-v', 'error', '-y', '-i', str(source), '-map', '0:v:0', '-map', '0:a:0',
            '-vf', 'scale=1280:800:force_original_aspect_ratio=decrease,pad=1280:800:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30',
            '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '23', '-c:a', 'aac', '-b:a', '128k',
            '-ar', '48000', '-ac', '2', str(normalized))
        duration = float(run('ffprobe', '-v', 'error', '-show_entries', 'format=duration',
                             '-of', 'default=nw=1:nk=1', str(normalized)))
        end = offset + round(duration * 1000)
        metadata += ['[CHAPTER]', 'TIMEBASE=1/1000', f'START={offset}', f'END={end}', f'title={title}']
        chapters.append({'scenario': scenario, 'title': title, 'startSeconds': offset / 1000,
                         'durationSeconds': duration, 'checks': len(evidence['checks']),
                         'originalVideoSha256': hashlib.sha256(source.read_bytes()).hexdigest()})
        offset = end
    (output / 'concat.txt').write_text(''.join(f"file 'chapter-{i}.mp4'\n" for i in range(len(SCENARIOS))))
    (output / 'chapters.ffmeta').write_text('\n'.join(metadata) + '\n')
    video = output / 'CabinOS_ONE_all_features.mp4'
    run('ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', str(output / 'concat.txt'),
        '-i', str(output / 'chapters.ffmeta'), '-map_metadata', '1', '-map_chapters', '1',
        '-c', 'copy', '-movflags', '+faststart', str(video))
    (output / 'video-manifest.json').write_text(json.dumps({
        'apkSha256': apk_hash,
        'method': 'Five separately recorded actual APK sessions concatenated without replacement audio',
        'vehicleAndGpsSimulated': True, 'officialPleosEmulator': False,
        'videoSha256': hashlib.sha256(video.read_bytes()).hexdigest(), 'chapters': chapters,
    }, ensure_ascii=False, indent=2) + '\n')


if __name__ == '__main__':
    main(*(pathlib.Path(arg) for arg in sys.argv[1:]))
