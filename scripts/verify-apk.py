#!/usr/bin/env python3
"""Check the signature identity and actual packaged Android release metadata."""
import os
from pathlib import Path
import re
import subprocess
import sys

from version import release_metadata

if len(sys.argv) != 3:
    sys.exit('Usage: python3 scripts/verify-apk.py APK VERSION')
apk = Path(sys.argv[1]).resolve()
metadata = release_metadata(sys.argv[2])
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
signature = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
signers = re.findall(r'Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)', signature)
expected = (Path(__file__).resolve().parents[1] / 'release-certificate.sha256').read_text().strip()
if len(signers) != 1 or signers[0].lower() != expected:
    sys.exit('APK signing certificate does not match the repository release identity')
badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
package = badging.splitlines()[0]
for expected_field in ["name='com.sasakiy84.solovlog'", f"versionCode='{metadata['code']}'", f"versionName='{metadata['version']}'"]:
    if expected_field not in package:
        sys.exit(f'APK metadata mismatch: {expected_field}')
if "sdkVersion:'36'" not in badging.splitlines():
    sys.exit('APK minSdk must be 36 (Android 16)')
print(f"PASS: {apk.name}: release certificate, version {metadata['version']}, code {metadata['code']}, minSdk 36")
