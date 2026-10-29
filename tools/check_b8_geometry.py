"""Replay display changes against the compiled production B8PanelGeometry wrapper.

Run after compileGithubDebugKotlin or the delivery build. Uses the same Gradle
installation's Kotlin runtime, with small fake Android display/preferences APIs.
"""
from pathlib import Path
import os, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
production = root / 'app/build/tmp/kotlin-classes/githubDebug'
assert (production / 'org/duofold/live/B8PanelGeometry.class').is_file(), 'Compile GithubDebug Kotlin first'
gradle = Path(os.environ['GRADLE_BIN']).resolve().parent.parent
stdlib = list((gradle / 'lib').glob('kotlin-stdlib-*.jar'))
assert len(stdlib) == 1, 'Expected one Kotlin runtime in GRADLE_BIN installation'
with tempfile.TemporaryDirectory(prefix='b8-geometry-test-') as tmp:
    classes = Path(tmp) / 'classes'
    classes.mkdir()
    sources = list((root / 'tools/b8-geometry-tests').rglob('*.java'))
    sources.append(root / 'app/src/main/java/org/duofold/live/PanelIdentityPolicy.java')
    cp = os.pathsep.join(map(str, [classes, production, stdlib[0]]))
    subprocess.run(['javac', '-cp', cp, '-d', str(classes), *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', cp, 'org.duofold.live.B8PanelGeometryTest'], check=True)
