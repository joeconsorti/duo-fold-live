"""Exercise production window dump concurrency with a blocked fake Binder service."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/org/duofold/live'
with tempfile.TemporaryDirectory(prefix='window-priority-') as temp:
    files=list((root/'tools/window-priority-tests').rglob('*.java'))
    files += [source/(name+'.java') for name in ['EndpointWindowTrace','EndpointWindowState','WindowWorkPriority']]
    files += [root/'tools/home-frame-tests/android/os/SystemClock.java']
    subprocess.run(['javac','-d',temp,*map(str,files)],check=True)
    subprocess.run(['java','-cp',temp,'org.duofold.live.WindowPriorityTest'],check=True,timeout=10)
