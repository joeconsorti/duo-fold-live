package org.duofold.live.experimental.b8;
import android.hardware.display.IDisplayManager;import android.hardware.devicestate.DeviceStateManager;
public class LiveB8PowerTest {
 static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  LiveB8Power p=new LiveB8Power();p.request(1);p.on();check(p.ready(),"source request activated");
  IDisplayManager.Call first=IDisplayManager.calls.get(0),second=IDisplayManager.calls.get(1);
  check(first.token!=second.token,"independent panel power ownership");p.park();
  IDisplayManager.Call clear=IDisplayManager.calls.get(2),off=IDisplayManager.calls.get(3);
  check(clear.id==0&&clear.state==0&&clear.timeout==-1&&clear.token==first.token,"primary ON override removed on stationary settle");
  check(off.id==1&&off.state==1&&off.timeout==1000&&off.token==second.token,"unused secondary OFF only with one-second owned lease");
  check(DeviceStateManager.current==4&&DeviceStateManager.cancels==0,"park preserves source-primary choice");
  p.park();check(IDisplayManager.calls.size()==5,"renewal does not repeatedly clear primary");
  p.on();check(IDisplayManager.calls.get(5).state==2&&IDisplayManager.calls.get(6).state==2,"resuming replaces OFF lease with ON on both panels");
  p.park();check(IDisplayManager.calls.get(7).state==0,"repeated settle again releases primary override");
  IDisplayManager.rejectClear=true;check(!p.close(),"failed power cleanup retains ownership for retry");
  IDisplayManager.rejectClear=false;check(p.close(),"power cleanup succeeds on retry");
  check(IDisplayManager.calls.get(IDisplayManager.calls.size()-1).state==0,"secondary OFF token released on cleanup");
  int n=IDisplayManager.calls.size();p.close();check(n==IDisplayManager.calls.size(),"cleanup is idempotent");
  p=new LiveB8Power();p.request(1);android.hardware.devicestate.DeviceStateRequest old=DeviceStateManager.request;
  android.hardware.devicestate.DeviceStateRequest.Callback oldCallback=DeviceStateManager.callback;
  p.request(2);oldCallback.onRequestCanceled(old);check(!p.canceled,"old source request cancellation cannot cancel resumed request");
  DeviceStateManager.callback.onRequestCanceled(DeviceStateManager.request);check(p.canceled,"Android cancellation reaches controller");p.close();
  System.out.println("LiveB8PowerTest: "+checks+" token/park/resume/cleanup checks passed (fake platform)");
 }
}
