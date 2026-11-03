#!/usr/bin/env python3
"""Select real-time excerpts from the verified APK captures; preserve device audio."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

SEGMENTS = [
 ('overview',2,16,'충전 대기 시간을 집중과 휴식으로 바꾸는 CabinOS ONE'),
 ('overview',18,31,'집중 세션 · 할 일 추가, 완료, 삭제'),
 ('stop-plan',75,87,'출발 준비 시간을 지키며 집중 시간 연장'),
 ('stop-plan',108,125,'충전 시간이 줄면 세션도 조정 · 여유 없는 연장 제한'),
 ('stop-plan',130,142,'D 기어에서는 속도 0이어도 정차 기능 잠금'),
 ('overview',106,116,'휴식 · 호흡 가이드 · 세션 기록'),
 ('navigation-review',32,40,'휴대폰 검토 모드 · 경로 선택 후 지도 자동 확대'),
 ('navigation-review',47,70.5,'한국어 길 안내 · 앱이 출력한 실제 음성'),
 ('place-photos',25,34,'주변 카페 추천 · 실제 장소 사진과 출처'),
 ('place-photos',43,61,'서로 다른 장소 사진 · 카페와 산책 분류'),
 ('audio-review',26,29,'집중 배경음 1 / 6 · 백색 소음'),
 ('audio-review',44,47,'집중 배경음 2 / 6 · 자연의 소리'),
 ('audio-review',62,65,'집중 배경음 3 / 6 · 잔잔한 톤'),
 ('audio-review',80,83,'집중 배경음 4 / 6 · 빗소리'),
 ('audio-review',99,102,'집중 배경음 5 / 6 · 계곡 소리'),
 ('audio-review',120,123,'집중 배경음 6 / 6 · 파도 소리'),
 ('overview',121,130,'사용자 취향에 맞춘 화면 테마'),
]


def main(root, output):
 output.mkdir(parents=True, exist_ok=True)
 font='/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
 note=output/'notice.txt';note.write_text('실제 Android APK · 차량/GPS 시뮬레이션 · 정상 속도 발췌 편집 · 별도 더빙 없음')
 checks=[];offset=0
 for i,(scenario,start,end,title) in enumerate(SEGMENTS):
  folder=root/scenario
  source=folder/'CabinOS_ONE_android_apk.mp4'
  evidence=json.loads((folder/'evidence.json').read_text())
  assert evidence['apkSha256']=='f685dbce8ff531e921029c1fb961eafe04fee1fe2e70d8451d3fa184af049ba4'
  assert all(c['result']=='passed' for c in evidence['checks'])
  label=output/f'label-{i}.txt';label.write_text(title)
  duration=end-start
  filt=f'scale=1280:800:force_original_aspect_ratio=decrease,pad=1280:896:(ow-iw)/2:0,setsar=1,fps=30,drawtext=fontfile={font}:textfile={label}:fontsize=27:fontcolor=white:x=24:y=815,drawtext=fontfile={font}:textfile={note}:fontsize=18:fontcolor=0xAFC5C7:x=24:y=858'
  subprocess.run(['ffmpeg','-v','error','-y','-ss',str(start),'-i',str(source),'-t',str(duration),'-map','0:v:0','-map','0:a:0','-vf',filt,'-af',f'afade=t=in:d=0.04,afade=t=out:st={duration-.04}:d=0.04','-c:v','libx264','-preset','veryfast','-crf','21','-c:a','aac','-b:a','128k','-ar','48000','-ac','2',str(output/f'part-{i}.mp4')],check=True,timeout=180)
  checks.append(dict(scenario=scenario,sourceStart=start,sourceEnd=end,outputStart=offset,title=title,speed=1))
  offset+=duration
 (output/'concat.txt').write_text(''.join(f"file 'part-{i}.mp4'\n" for i in range(len(SEGMENTS))))
 video=output/'CabinOS_ONE_3min_demo.mp4'
 subprocess.run(['ffmpeg','-v','error','-y','-f','concat','-safe','0','-i',str(output/'concat.txt'),'-map_metadata','-1','-c','copy','-movflags','+faststart',str(video)],check=True)
 probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_format','-show_streams','-of','json',str(video)]))
 assert 0<float(probe['format']['duration'])<180
 assert {'video','audio'}<={s['codec_type'] for s in probe['streams']}
 (output/'short-video-manifest.json').write_text(json.dumps(dict(durationSeconds=float(probe['format']['duration']),videoSha256=hashlib.sha256(video.read_bytes()).hexdigest(),sourceRecordingRun=41428891716,sourceApkSha256=evidence['apkSha256'],segments=checks),ensure_ascii=False,indent=2)+'\n')
 print('Duration seconds:',probe['format']['duration'])

if __name__=='__main__':main(Path(sys.argv[1]).resolve(),Path(sys.argv[2]).resolve())
