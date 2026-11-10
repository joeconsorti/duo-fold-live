"""Exercise the production native-Home compositor against a CPU image oracle."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='home-native-') as tmp:
    classes=Path(tmp)/'classes';classes.mkdir()
    files=list((root/'tools/home-native-tests').rglob('*.java'))
    files += [root/'tools/bar-effect-tests/android/graphics/Bitmap.java']
    files += [root/'tools/home-frame-tests/android/graphics'/name for name in ['Color.java','Paint.java','Rect.java']]
    files += [root/'tools/home-frame-tests'/name for name in ['android/hardware/HardwareBuffer.java','android/os/SystemClock.java']]
    files += [root/'app/src/main/java/org/duofold/live'/name for name in ['HomeNativeComposition.java','HomeNativeCapture.java','HomeContentPolicy.java','CaptureApi.java','DisplayCaptureApi.java','CaptureCompletion.java']]
    subprocess.run(['javac','-d',str(classes),*map(str,files)],check=True)
    subprocess.run(['java','-cp',str(classes),'org.duofold.live.HomeNativeCompositionTest'],check=True)
    subprocess.run(['java','-cp',str(classes),'org.duofold.live.HomeNativeCaptureTest'],check=True)
