"""Exercise the compiled workspace capture with fake framework callbacks; no handset/pixel claim."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
production=root/'app/build/tmp/kotlin-classes/githubDebug'
stdlib=list((Path(os.environ['GRADLE_BIN']).resolve().parent.parent/'lib').glob('kotlin-stdlib-*.jar'))
assert len(stdlib)==1
with tempfile.TemporaryDirectory(prefix='workspace-frames-') as tmp:
    classes=Path(tmp)/'classes';classes.mkdir()
    cp=os.pathsep.join(map(str,[classes,production,stdlib[0]]))
    sources=[p for p in (root/'tools/home-frame-tests').rglob('*.java') if p.name not in ('HomeFramesTest.java','HomeCaptureSource.java','LiveAngles.java')]
    sources+=list((root/'tools/workspace-frame-tests').rglob('*.java'))
    sources+=[root/'app/src/main/java/org/duofold/live'/f'{name}.java' for name in ('CaptureCompletion','HomeCapturePacing','WorkspaceBufferGeometry')]
    subprocess.run(['javac','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
    subprocess.run(['java','-cp',cp,'org.duofold.live.WorkspaceFramesTest'],check=True,timeout=20)
