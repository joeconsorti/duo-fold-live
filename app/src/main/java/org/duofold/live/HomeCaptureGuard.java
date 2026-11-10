package org.duofold.live;
import android.os.Bundle;

/** Shell-side read-only validation surrounding an isolated launcher capture. */
final class HomeCaptureGuard {
 static Bundle inspect(int task,String physical)throws Exception{
  String home=HomeCaptureIdentity.focused(false);
  Object dm=MovingSceneCapture.service("display","android.hardware.display.IDisplayManager");
  Object d=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,0);
  if(!MovingSceneCapture.matches(d,physical)||MovingSceneCapture.number(d,"committedState")!=2)throw new IllegalStateException("Home primary mapping pending");
  String state=HomeWindowEvidence.guard(EndpointWindowTrace.dump(),task,0);
  if(!state.equals("READY; HAS_DRAWN"))throw new IllegalStateException(state);
  Bundle out=new Bundle();out.putString("home",home);out.putString("physical",physical);
  out.putInt("width",MovingSceneCapture.number(d,"logicalWidth"));out.putInt("height",MovingSceneCapture.number(d,"logicalHeight"));out.putInt("rotation",MovingSceneCapture.number(d,"rotation"));
  out.putBoolean("ok",true);return out;
 }
}
