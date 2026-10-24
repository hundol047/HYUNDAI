#!/usr/bin/env python3
"""Check a signed submission binary; never sign, modify, upload or claim device validation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
MARKER = 'io.cabinos.one.PLEOS_SDK_ENABLED'


def inspect_metadata(badging, manifest, signer, version, code, certificate):
    errors = []
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package or package[1] != 'io.cabinos.one':
        errors.append('앱 패키지가 CabinOS ONE과 다릅니다.')
    if not package or package.groups()[1:] != (str(code), version):
        errors.append('APK 버전이 현재 소스와 다릅니다.')
    if 'application-debuggable' in badging:
        errors.append('디버그 가능 APK는 배포할 수 없습니다.')
    sdk = False
    for block in re.split(r'\bE: ', manifest):
        if block.startswith('meta-data') and MARKER in block:
            sdk = bool(re.search(r'android:value[^\n]*=\(type 0x12\)0xffffffff\b', block))
    if not sdk:
        errors.append('Pleos SDK 포함 빌드 표시가 없습니다.')
    digests = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', signer)
    if len(digests) != 1 or digests[0].lower() != certificate.lower():
        errors.append('서명 인증서가 지정한 팀 인증서와 다릅니다.')
    if re.search(r'certificate DN:.*Android Debug', signer, re.IGNORECASE):
        errors.append('Android debug 서명은 배포할 수 없습니다.')
    return errors


def checksum_matches(text, name, digest):
    matches = []
    for line in text.splitlines():
        fields = line.split(maxsplit=1)
        if len(fields) == 2 and Path(fields[1].lstrip('*')).name == name:
            matches.append(fields[0].lower())
    return matches == [digest.lower()]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=ROOT / 'apk/퓨처랩스_CabinOS_ONE_release.apk')
    parser.add_argument('--checksums', type=Path, default=ROOT / 'apk/SHA256SUMS')
    parser.add_argument('--certificate-sha256', required=True, help='Trusted team certificate digest, obtained separately from this candidate')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    certificate = args.certificate_sha256.replace(':', '').lower()
    if not re.fullmatch('[0-9a-f]{64}', certificate):
        parser.error('인증서 SHA-256은 64자리 16진수여야 합니다.')
    errors = []
    digest = None
    try:
        digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
        if not checksum_matches(args.checksums.read_text(), args.apk.name, digest):
            errors.append('APK SHA-256이 체크섬 파일과 일치하지 않습니다.')
        build = (ROOT / 'app/build.gradle.kts').read_text()
        version = re.search(r'versionName\s*=\s*"([^"]+)"', build)[1]
        code = int(re.search(r'versionCode\s*=\s*(\d+)', build)[1])
        sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '')))
        build_tools = sdk / 'build-tools/35.0.0'
        def run(tool, *arguments):
            executable = build_tools / tool
            return subprocess.run([str(executable), *map(str, arguments)], capture_output=True, text=True, timeout=60, check=True).stdout
        badging = run('aapt', 'dump', 'badging', args.apk)
        manifest = run('aapt', 'dump', 'xmltree', args.apk, 'AndroidManifest.xml')
        try:
            signer = run('apksigner', 'verify', '--print-certs', args.apk)
        except subprocess.CalledProcessError:
            signer = ''
            errors.append('APK 암호학적 서명 검증에 실패했습니다.')
        errors.extend(inspect_metadata(badging, manifest, signer, version, code, certificate))
    except (OSError, subprocess.SubprocessError, TypeError, ValueError):
        errors.append('검사 파일 또는 Android build-tools 35.0.0을 확인해 주세요. 검사를 완료하지 못했습니다.')
    report = {'binaryChecksPassed': not errors, 'sha256': digest, 'errors': errors,
              'scope': '버전·패키지·debug 여부·SDK 빌드 표시·서명·체크섬 검사만 수행. 실제 Pleos 실행·안전성·배포 승인을 대체하지 않음.'}
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(rendered + '\n')
    print(rendered)
    return 0 if not errors else 1


if __name__ == '__main__':
    raise SystemExit(main())
