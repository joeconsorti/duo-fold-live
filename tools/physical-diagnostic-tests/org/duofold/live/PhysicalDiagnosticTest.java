package org.duofold.live;
import android.os.*;import android.graphics.*;import android.hardware.*;import android.hardware.display.IDisplayManager;import android.hardware.display.IDisplayManager.Info;import android.view.IWindowManager;import android.window.ScreenCapture;
public class PhysicalDiagnosticTest{
 static int checks;static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 interface Throwing{void run()throws Exception;}
 static void rejected(Throwing r,String reason)throws Exception{try{r.run();throw new AssertionError("Expected rejection: "+reason);}catch(IllegalStateException|IllegalArgumentException expected){check(expected.getMessage().contains(reason),expected.toString());}}
 static void reset(){IDisplayManager.panels=new Info[]{new Info("local:1",2,1248,1972),new Info("local:2",2,2448,1848)};IWindowManager.locked=false;ScreenCapture.calls=0;ScreenCapture.secure=ScreenCapture.ignoreSize=false;ScreenCapture.during=null;}
 public static void main(String[] args)throws Exception{
  reset();for(String physical:new String[]{"local:1","local:2"}){Bundle b=MovingSceneCapture.captureDiagnosticPhysical(physical);Bitmap image=b.getParcelable("bitmap",Bitmap.class);check(b.getBoolean("ok"),"Home-independent app frame accepted");check(physical.equals(b.getString("physical")),"physical provenance preserved");check(image.width<=320&&image.height<=320,"bounded full-panel image");check(HardwareBuffer.last.closed,"native buffer released");image.recycle();}
  Info swap=IDisplayManager.panels[0];IDisplayManager.panels[0]=IDisplayManager.panels[1];IDisplayManager.panels[1]=swap;
  Bundle remap=MovingSceneCapture.captureDiagnosticPhysical("local:1");check(remap.getInt("display")==1,"physical panel resolved afresh after logical swap");remap.getParcelable("bitmap",Bitmap.class).recycle();
  reset();IDisplayManager.panels[0].state=1;rejected(()->MovingSceneCapture.captureDiagnosticPhysical("local:1"),"OFF/unmapped");check(ScreenCapture.calls==0,"OFF panel never captured/woken");
  reset();IDisplayManager.panels[1].uniqueId="local:1";rejected(()->MovingSceneCapture.captureDiagnosticPhysical("local:1"),"Ambiguous");check(ScreenCapture.calls==0,"ambiguous map never captured");
  reset();IWindowManager.locked=true;rejected(()->MovingSceneCapture.captureDiagnosticPhysical("local:1"),"Locked");check(ScreenCapture.calls==0,"locked request skipped before capture");
  reset();ScreenCapture.during=()->IWindowManager.locked=true;rejected(()->MovingSceneCapture.captureDiagnosticPhysical("local:1"),"Locked during");check(HardwareBuffer.last.closed,"mid-capture lock disposes native handle");
  reset();ScreenCapture.during=()->IDisplayManager.panels[0].uniqueId="local:9";rejected(()->MovingSceneCapture.captureDiagnosticPhysical("local:1"),"Mapping changed");check(HardwareBuffer.last.closed,"changed mapping disposes native handle");
  reset();ScreenCapture.secure=true;rejected(()->MovingSceneCapture.captureDiagnosticPhysical("local:1"),"Protected content");check(HardwareBuffer.last.closed,"secure result disposed");
  reset();ScreenCapture.ignoreSize=true;rejected(()->MovingSceneCapture.captureDiagnosticPhysical("local:1"),"ignored bounded output");check(HardwareBuffer.last.closed,"oversized result disposed before readback");
  reset();WindowWorkPriority.INSTANCE.enterCritical();try{rejected(()->MovingSceneCapture.captureDiagnosticPhysical("local:1"),"Critical window work");check(ScreenCapture.calls==0,"optional work cannot start while critical work active");}finally{WindowWorkPriority.INSTANCE.leaveCritical();}
  reset();rejected(()->MovingSceneCapture.captureDiagnosticPhysical("unknown"),"Unknown physical");check(ScreenCapture.calls==0,"unknown identity never captured");
  System.out.println("PhysicalDiagnosticTest: "+checks+" mapping/OFF/lock/security/bounds/priority checks passed; fake compositor, no optical proof");
 }
}
