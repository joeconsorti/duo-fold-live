"""Build the separate test application; no GitHub publication or release signing.

Requires JDK 17 and ANDROID_HOME (API 36, build-tools 35.0.0).
Set GRADLE_BIN to a locally installed Gradle 8.13 executable if needed.
Keep ~/.android/debug.keystore private and backed up for compatible test updates.
"""
import hashlib
import os
import re
from pathlib import Path
import shutil
import subprocess
import time
from package_local_test import require_clean, package
from urllib.parse import urlparse

root = Path(__file__).resolve().parents[1]
env = os.environ.copy()
proxy = urlparse(env.get('HTTPS_PROXY', ''))
options = env.get('GRADLE_OPTS', '')
if proxy.hostname and proxy.port and not proxy.username:
    options += f' -Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port}'
    options += f' -Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={proxy.port}'
env['GRADLE_OPTS'] = options
require_clean()
source_commit = subprocess.check_output(['git','rev-parse','HEAD'],cwd=root)
started = time.monotonic()
subprocess.run([env.get('GRADLE_BIN', str(root/'gradlew')), '--no-daemon',
                '--console=plain', '--no-build-cache', 'clean', 'testGithubDebugUnitTest', 'assembleGithubDebug'],
               cwd=root, env=env, check=True)
if source_commit != subprocess.check_output(['git','rev-parse','HEAD'],cwd=root):
    raise SystemExit('Source commit changed during build; rebuild before packaging.')
require_clean()
apk = root/'app/build/outputs/apk/github/debug/app-github-debug.apk'
# Do not publish a stale APK even when Gradle reports success.
config = (root/'app/build.gradle.kts').read_text()
expected_code = re.search(r'versionCode\s*=\s*(\d+)', config).group(1)
expected_name = re.search(r'versionName\s*=\s*"([^"]+)"', config).group(1) + '-local'
aapt = Path(env['ANDROID_HOME'])/'build-tools/35.0.0/aapt'
badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
package_line = badging.splitlines()[0]
if (f"versionCode='{expected_code}'" not in package_line
        or f"versionName='{expected_name}'" not in package_line
        or "name='org.duofold.live.test'" not in package_line):
    raise SystemExit('APK identity/version does not match committed source: ' + package_line)
out = root/'build/local-test' 
out.mkdir(parents=True, exist_ok=True)
target = out/'Duo-Fold-Live-Test.apk'
shutil.copy2(apk, target)
(out/'SHA256SUMS.txt').write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
package(target)
print(f'Local test build: {time.monotonic()-started:.1f}s; {target}')
