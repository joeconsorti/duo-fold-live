"""Exercise compiled destination-bar composition with a tiny CPU image oracle.
No shader, compositor or optical measurements are made by this harness.
"""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
kotlin=root/'app/build/tmp/kotlin-classes/githubDebug'
java=root/'app/build/intermediates/javac/githubDebug/compileGithubDebugJavaWithJavac/classes'
stdlib=list((Path(os.environ['GRADLE_BIN']).resolve().parent.parent/'lib').glob('kotlin-stdlib-*.jar'))
android=Path(os.environ['ANDROID_HOME'])/'platforms/android-36/android.jar'
assert len(stdlib)==1
with tempfile.TemporaryDirectory(prefix='bar-effect-') as tmp:
 classes=Path(tmp)/'classes';classes.mkdir()
 cp=os.pathsep.join(map(str,[classes,kotlin,java,stdlib[0],android]))
 sources=list((root/'tools/bar-effect-tests').rglob('*.java'))
 sources += [root/'tools/home-frame-tests'/path for path in ['android/graphics/Paint.java','android/graphics/Rect.java','android/os/Handler.java','android/os/Looper.java','android/os/SystemClock.java','org/duofold/live/RecoveryLog.java']]
 subprocess.run(['javac','-cp',cp,'-d',str(classes),*map(str,sources)],check=True)
 subprocess.run(['java','-cp',cp,'org.duofold.live.B8BarEffectTest'],check=True,timeout=15)
