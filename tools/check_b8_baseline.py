#!/usr/bin/env python3
"""Verify the imported Lab 0.5.11 checkpoint and run its portable guards.

No device operations, networking, publishing, or signing are performed.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/org/duofold/live/experimental/b8'
OLD = b'package org.duofold.powerlab.privatebuild;'
NEW = b'package org.duofold.live.experimental.b8;'
manifest = json.loads((ROOT / 'docs/B8-BASELINE-SHA256.json').read_text())
actual = {p.name for p in SOURCE.glob('*.java')}
if not set(manifest).issubset(actual):
    raise SystemExit('Baseline file inventory changed; review before updating checkpoint.')
for name, expected in manifest.items():
    data = (SOURCE / name).read_bytes()
    if name == 'TaskMover.java':
        data = data.replace(b'pkg.equals("org.duofold.live")', b'pkg.equals("org.duofold.powerlab.privatebuild")')
    if data.count(NEW) != 1:
        raise SystemExit(f'{name}: unexpected package declaration')
    if hashlib.sha256(data.replace(NEW, OLD, 1)).hexdigest() != expected:
        raise SystemExit(f'{name}: differs from tested Lab 0.5.11 beyond package relocation')
print(f'PASS: all {len(manifest)} engine files match Lab 0.5.11, except package relocation and built-in task package identity', flush=True)

plans = ['ExperimentConfig', 'TaskGuard', 'RoutePlan', 'BridgePlan',
         'HandoffPlan', 'AnimatorRestorePlan', 'DesktopRestorePlan', 'LiveB8Policy']
tests = sorted((ROOT / 'tools/b8-tests').glob('*.java'))
with tempfile.TemporaryDirectory(prefix='duo-b8-check-') as output:
    subprocess.run(['javac', '-source', '8', '-target', '8', '-d', output,
                    *[str(SOURCE / (p + '.java')) for p in plans],
                    *map(str, tests)], check=True)
    for test in tests:
        if test.stem.endswith('Harness'):
            continue
        subprocess.run(['java', '-cp', output,
                        'org.duofold.live.experimental.b8.' + test.stem], check=True)
    for test in sorted((ROOT / 'tools/b8-tests').glob('test_*.py')):
        subprocess.run([sys.executable, str(test)], check=True,
                       env=dict(os.environ, B8_TEST_CLASSES=output))
    sdk = os.environ.get('ANDROID_HOME')
    if sdk:
        android = Path(sdk) / 'platforms/android-36/android.jar'
        lambdas = Path(sdk) / 'build-tools/35.0.0/core-lambda-stubs.jar'
        subprocess.run(['javac', '-source', '8', '-target', '8', '-proc:none',
                        '-bootclasspath', os.pathsep.join(map(str, [android, lambdas])),
                        '-d', output, *[str(SOURCE / name) for name in sorted(manifest)]], check=True)
        print('PASS: complete imported core compiles against Android 36')
    else:
        print('Android compile skipped: set ANDROID_HOME to enable it.')
print('These checks do not validate handset behavior or build a Duo APK.')
