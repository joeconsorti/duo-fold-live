"""Execute the real live controller against a deterministic fake Android platform."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/org/duofold/live/experimental/b8'
# Architectural regression: the production shield must never create a mirror graph,
# even if a future change keeps it hidden beneath screenshot buffers.
bridge=(source/'LiveB8Bridge.java').read_text()
for forbidden in ('mirrorDisplay', 'mirrorSurface', 'LiveWindowBridge', 'ScreenshotOnlyBridge'):
 assert forbidden not in bridge, f'Unsafe mirror backend restored: {forbidden}'

with tempfile.TemporaryDirectory(prefix='live-b8-test-') as tmp:
 classes=Path(tmp)/'classes'; reports=Path(tmp)/'reports';classes.mkdir();reports.mkdir()
 files=list((root/'tools/b8-live-tests').rglob('*.java'))+[source/'LiveB8Controller.java',source/'LiveB8Policy.java',source.parent.parent/'HingePausePolicy.java']
 subprocess.run(['javac','-source','8','-target','8','-d',str(classes),*map(str,files)],check=True)
 subprocess.run(['java','-cp',str(classes),'org.duofold.live.experimental.b8.LiveB8ControllerTest',str(reports)],check=True)
with tempfile.TemporaryDirectory(prefix='live-b8-cleanup-') as tmp:
 classes=Path(tmp)/'classes';reports=Path(tmp)/'reports';classes.mkdir();reports.mkdir()
 files=list((root/'tools/b8-cleanup-tests').rglob('*.java'))+[source/(name+'.java') for name in ['LiveB8Bridge','BridgePlan','CaptureCompletion','DisplayCaptureApi']]
 subprocess.run(['javac','-source','8','-target','8','-d',str(classes),*map(str,files)],check=True)
 subprocess.run(['java','-cp',str(classes),'org.duofold.live.experimental.b8.LiveB8BridgeCleanupTest',str(reports)],check=True)
with tempfile.TemporaryDirectory(prefix='live-b8-brightness-') as tmp:
 classes=Path(tmp)/'classes';reports=Path(tmp)/'reports';classes.mkdir();reports.mkdir()
 files=list((root/'tools/b8-brightness-tests').rglob('*.java'))+[source/(name+'.java') for name in ['LiveB8Brightness','BrightnessSamples']]
 subprocess.run(['javac','-source','8','-target','8','-d',str(classes),*map(str,files)],check=True)
 subprocess.run(['java','-cp',str(classes),'org.duofold.live.experimental.b8.LiveB8BrightnessTest',str(reports)],check=True)
with tempfile.TemporaryDirectory(prefix='live-b8-scene-') as tmp:
 classes=Path(tmp)/'classes';classes.mkdir()
 files=list((root/'tools/b8-scene-tests').rglob('*.java'))+[source/(name+'.java') for name in ['LiveB8Scene','SceneMapping']]
 subprocess.run(['javac','-source','8','-target','8','-d',str(classes),*map(str,files)],check=True)
 subprocess.run(['java','-cp',str(classes),'org.duofold.live.experimental.b8.LiveB8SceneTest'],check=True)

with tempfile.TemporaryDirectory(prefix='live-b8-tasks-') as tmp:
 classes=Path(tmp)/'classes';reports=Path(tmp)/'reports';classes.mkdir();reports.mkdir()
 files=list((root/'tools/b8-task-tests').rglob('*.java'))+[source/(name+'.java') for name in ['LiveB8Task','TaskGuard','TaskLayoutPolicy']]+[source.parent.parent/'MultiWindowTasks.java']
 subprocess.run(['javac','-source','8','-target','8','-d',str(classes),*map(str,files)],check=True)
 subprocess.run(['java','-cp',str(classes),'org.duofold.live.experimental.b8.LiveB8TaskTest',str(reports)],check=True)

with tempfile.TemporaryDirectory(prefix='live-b8-power-') as tmp:
 classes=Path(tmp)/'classes';classes.mkdir()
 files=list((root/'tools/b8-power-tests').rglob('*.java'))+[source/'LiveB8Power.java']
 subprocess.run(['javac','-source','8','-target','8','-d',str(classes),*map(str,files)],check=True)
 subprocess.run(['java','-cp',str(classes),'org.duofold.live.experimental.b8.LiveB8PowerTest'],check=True)
