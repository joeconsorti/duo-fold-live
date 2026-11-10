"""Run the production both-panel diagnostic capture against a fake compositor.
Reject OFF/lock/mapping/security/size/priority failures without fallback or wake.
"""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='duo-physical-diagnostic-') as tmp:
    classes=Path(tmp)/'classes';classes.mkdir()
    sources=list((root/'tools/physical-diagnostic-tests').rglob('*.java'))
    sources += [root/'tools/bar-effect-tests/android/graphics/Bitmap.java']
    sources += [root/'tools/home-frame-tests'/p for p in ['android/hardware/HardwareBuffer.java','android/os/SystemClock.java']]
    sources += [root/'app/src/main/java/org/duofold/live'/p for p in ['MovingSceneCapture.java','PhysicalCaptureSize.java','WindowWorkPriority.java','CaptureApi.java','CaptureCompletion.java']]
    subprocess.run(['javac','-d',str(classes),*map(str,sources)],check=True)
    subprocess.run(['java','-cp',str(classes),'org.duofold.live.PhysicalDiagnosticTest'],check=True,timeout=15)
