"""Exercise production Home capture concurrency and rejection using fake Android APIs, not pixels."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
production=root/'app/build/tmp/kotlin-classes/githubDebug'
stdlib=list((Path(os.environ['GRADLE_BIN']).resolve().parent.parent/'lib').glob('kotlin-stdlib-*.jar'))
assert len(stdlib)==1
with tempfile.TemporaryDirectory(prefix='home-frames-') as tmp:
 classes=Path(tmp)/'classes';classes.mkdir()
 cp=os.pathsep.join(map(str,[classes,production,stdlib[0]]))
 sources=list((root/'tools/home-frame-tests').rglob('*.java'))
 sources += [root/'app/src/main/java/org/duofold/live'/f'{name}.java' for name in ['CaptureCompletion','HomeCapturePacing','HomeBufferGeometry','HomeContentPolicy']]
 subprocess.run(['javac','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
 subprocess.run(['java','-cp',cp,'org.duofold.live.HomeFramesTest'],check=True,timeout=15)
