#!/usr/bin/env python3
"""Sign an already verified APK with an external key and validate its release identity."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--sdk', required=True)
parser.add_argument('--unsigned', required=True, type=Path)
parser.add_argument('--tag', required=True)
parser.add_argument('--output-dir', required=True, type=Path)
args = parser.parse_args()
if not re.fullmatch(r'v\d+\.\d+\.\d+', args.tag):
    parser.error('Stable release tags must use vMAJOR.MINOR.PATCH')
sdk_tools = Path(args.sdk) / 'build-tools' / '36.0.0'
key = Path(os.environ['APK_SIGNING_KEYSTORE'])
if not key.is_file() or not os.environ.get('APK_SIGNING_PASSWORD'):
    raise SystemExit('An external keystore and APK_SIGNING_PASSWORD are required.')

badging = subprocess.run([str(sdk_tools / 'aapt2'), 'dump', 'badging', str(args.unsigned)],
                         check=True, capture_output=True, text=True).stdout
package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
if not package or package[1] != 'com.adamdelisi.kvaesitsobridge' or args.tag != 'v' + package[3]:
    raise SystemExit('APK package/version does not match this release tag.')
if 'application-debuggable' in badging:
    raise SystemExit('Refusing to publish a debuggable APK.')

args.output_dir.mkdir(parents=True, exist_ok=True)
apk = args.output_dir / f'kvaesitso-bridge-{package[3]}.apk'
subprocess.run([str(sdk_tools / 'apksigner'), 'sign', '--ks', str(key), '--ks-key-alias', 'bridge',
                '--ks-pass', 'env:APK_SIGNING_PASSWORD', '--key-pass', 'env:APK_SIGNING_PASSWORD',
                '--out', str(apk), str(args.unsigned)], check=True)
verification = subprocess.run([str(sdk_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)],
                              check=True, capture_output=True, text=True).stdout
certificate = re.search(r'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)', verification)
expected = (Path(__file__).resolve().parents[1] / 'docs/release-signing-certificate.sha256').read_text().strip()
if not certificate or certificate[1].lower() != expected.lower():
    apk.unlink()
    raise SystemExit('Signing certificate differs from the published app identity.')
subprocess.run([str(sdk_tools / 'zipalign'), '-c', '-P', '16', '4', str(apk)], check=True)
(args.output_dir / 'SHA256SUMS').write_text(f'{hashlib.sha256(apk.read_bytes()).hexdigest()}  {apk.name}\n')
print(f'Verified release APK: {apk}, versionCode {package[2]}, certificate {expected}')
