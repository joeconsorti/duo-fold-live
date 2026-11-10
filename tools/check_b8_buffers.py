"""Exercise the compiled production buffer renderer's lifetime protocol, not GPU pixels."""
from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
production=root/'app/build/tmp/kotlin-classes/githubDebug'
stdlib=list((Path(os.environ['GRADLE_BIN']).resolve().parent.parent/'lib').glob('kotlin-stdlib-*.jar'))
assert len(stdlib)==1
with tempfile.TemporaryDirectory(prefix='b8-buffers-') as tmp:
 classes=Path(tmp)/'classes';classes.mkdir()
 cp=os.pathsep.join(map(str,[classes,production,stdlib[0]]))
 sources=list((root/'tools/b8-buffer-tests').rglob('*.java'))
 subprocess.run(['javac','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
 subprocess.run(['java','-cp',cp,'org.duofold.live.B8BufferLayerTest'],check=True,timeout=15)
