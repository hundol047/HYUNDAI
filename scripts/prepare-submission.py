#!/usr/bin/env python3
"""Build and verify a separate team-signed candidate without changing apk/ or publishing."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

APK_NAME = '퓨처랩스_CabinOS_ONE_release.apk'
ROOT = Path(__file__).resolve().parents[1]


def prepare(root, signing, certificate, sdk, output, runner=subprocess.run):
    root, signing, sdk, output = (Path(p).resolve() for p in (root, signing, sdk, output))
    certificate = certificate.replace(':', '').lower()
    if not re.fullmatch('[0-9a-f]{64}', certificate):
        raise ValueError('별도로 확인한 팀 인증서 SHA-256이 필요합니다.')
    if signing.is_relative_to(root):
        raise ValueError('서명 키는 저장소 밖에 보관해야 합니다.')
    if output.is_relative_to(root) and not output.is_relative_to(root / 'artifacts'):
        raise ValueError('후보 출력은 artifacts/ 아래 또는 저장소 밖이어야 합니다. apk/를 직접 변경하지 않습니다.')
    if output.exists():
        raise FileExistsError('후보 폴더가 이미 있습니다. 새 폴더를 지정하세요.')
    key, password = signing / 'release.p12', signing / 'password'
    for path in (key, password):
        if path.resolve().is_relative_to(root):
            raise ValueError('서명 파일의 실제 경로도 저장소 밖이어야 합니다.')
        if not path.is_file():
            raise FileNotFoundError('기존 release.p12와 password 파일이 필요합니다. 새 키를 자동 생성하지 않습니다.')
    def run(command):
        return runner(list(map(str, command)), cwd=root, check=True, capture_output=True, text=True)
    if run(['git', 'status', '--porcelain', '--untracked-files=normal']).stdout.strip():
        raise ValueError('소스를 먼저 커밋해 주세요. 미커밋 소스를 특정 커밋의 산출물로 표시하지 않습니다.')
    commit = run(['git', 'rev-parse', 'HEAD']).stdout.strip()
    if not re.fullmatch('[0-9a-f]{40,64}', commit):
        raise ValueError('소스 커밋을 확인하지 못했습니다.')
    for tasks in ([':core:test', ':app:testDebugUnitTest'], [':app:assembleRelease'], [':app:lintDebug']):
        print('실행: 공식 SDK ' + ' '.join(tasks), flush=True)
        run(['./gradlew', '-PpleosSdk=true', *tasks, '--console=plain'])
    if run(['git', 'rev-parse', 'HEAD']).stdout.strip() != commit or run(['git', 'status', '--porcelain', '--untracked-files=normal']).stdout.strip():
        raise ValueError('빌드 중 소스가 변경됐습니다. 다시 빌드해 주세요.')
    output.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix='.candidate-', dir=output.parent))
    try:
        binary = stage / APK_NAME
        signer = sdk / 'build-tools/35.0.0/apksigner'
        run([signer, 'sign', '--ks', key, '--ks-key-alias', 'cabinosone', '--ks-pass', 'file:' + str(password),
             '--out', binary, root / 'app/build/outputs/apk/release/app-release-unsigned.apk'])
        run([sdk / 'build-tools/35.0.0/zipalign', '-c', '-P', '16', '4', binary])
        digest = hashlib.sha256(binary.read_bytes()).hexdigest()
        (stage / 'SHA256SUMS').write_text(f'{digest}  {APK_NAME}\n')
        run([sys.executable, root / 'scripts/verify-release-candidate.py', '--apk', binary,
             '--checksums', stage / 'SHA256SUMS', '--certificate-sha256', certificate,
             '--output', stage / 'verification.json'])
        if json.loads((stage / 'verification.json').read_text()).get('binaryChecksPassed') is not True:
            raise ValueError('배포 파일 검사가 통과하지 않았습니다.')
        (stage / 'candidate.json').write_text(json.dumps({'team': '퓨처랩스', 'app': 'CabinOS ONE',
            'sourceCommit': commit, 'sha256': digest, 'binaryChecksPassed': True,
            'deviceValidated': False, 'published': False}, ensure_ascii=False, indent=2) + '\n')
        if output.exists():
            raise FileExistsError('후보 폴더가 실행 중 생성됐습니다. 덮어쓰지 않습니다.')
        stage.rename(output)
    finally:
        if stage.exists():
            shutil.rmtree(stage)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--signing-dir', type=Path, required=True)
    parser.add_argument('--certificate-sha256', required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'artifacts/submission-candidate')
    args = parser.parse_args()
    try:
        result = prepare(ROOT, args.signing_dir, args.certificate_sha256,
                         os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '')), args.output)
    except subprocess.CalledProcessError as error:
        print(f'{Path(error.cmd[0]).name} 단계 실패(종료 코드 {error.returncode}). 빌드·서명·검사 설정을 확인하세요. apk/는 변경하지 않았습니다.', file=sys.stderr)
        return 1
    except (OSError, ValueError) as error:
        print(f'후보 생성 실패: {error} apk/는 변경하지 않았습니다.', file=sys.stderr)
        return 1
    print(f'퓨처랩스 검증 후보: {result}\n공식 Pleos 동일 APK 시연 전이며, 제출 APK 교체·게시를 수행하지 않았습니다.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
