package org.duofold.live;
import android.os.Bundle;

/** Read-only identity/geometry validation around isolated workspace window captures. */
final class WorkspaceCaptureGuard {
 static Bundle inspect(int[] tasks,String physical)throws Exception{
  MultiWindowTasks.Snapshot before=MultiWindowTasks.requireTasks(tasks);
  Object wm=MovingSceneCapture.service("window","android.view.IWindowManager");
  if((boolean)Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked");
  Object dm=MovingSceneCapture.service("display","android.hardware.display.IDisplayManager");
  java.lang.reflect.Method info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class);
  Object d=info.invoke(dm,0);
  if(!MovingSceneCapture.matches(d,physical)||MovingSceneCapture.number(d,"committedState")!=2)throw new IllegalStateException("Workspace primary mapping pending");
  int w=MovingSceneCapture.number(d,"logicalWidth"),h=MovingSceneCapture.number(d,"logicalHeight"),r=MovingSceneCapture.number(d,"rotation");
  if(!before.fits(w,h))throw new IllegalStateException("Workspace bounds pending");
  String state=EndpointWindowState.workspaceGuard(EndpointWindowTrace.dump(),tasks,0);
  if(!state.equals("READY; HAS_DRAWN"))throw new IllegalStateException(state);
  Object after=info.invoke(dm,0);
  if(!MovingSceneCapture.matches(after,physical)||MovingSceneCapture.number(after,"committedState")!=2||MovingSceneCapture.number(after,"logicalWidth")!=w||MovingSceneCapture.number(after,"logicalHeight")!=h||MovingSceneCapture.number(after,"rotation")!=r||!before.signature.equals(MultiWindowTasks.requireTasks(tasks).signature))throw new IllegalStateException("Workspace changed during guard");
  Bundle out=new Bundle();out.putBoolean("ok",true);out.putString("workspace",before.signature);out.putString("physical",physical);
  out.putInt("width",w);out.putInt("height",h);out.putInt("rotation",r);return out;
 }
}
