package org.duofold.live.experimental.b8;
import java.io.File;import java.lang.reflect.Field;import java.util.List;import android.view.SurfaceControl;
final class PowerExperiment {static Object service(String n,String c){return n.equals("window")?new android.view.IWindowManager():new android.hardware.display.IDisplayManager();}}
public class LiveB8BridgeCleanupTest {
 static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static List<Object> list(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return (List<Object>)f.get(o);}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  LiveB8Bridge bridge=new LiveB8Bridge(new File(args[0]),true);
  bridge.arm();check(!bridge.ready(),"arm submission without commit cannot authorize promotion");
  check(SurfaceControl.Transaction.applies==1,"both panels arm in one transaction");
  for(Object o:list(bridge,"surfaces")){SurfaceControl s=(SurfaceControl)o;check(s.shown&&s.skipScreenshot,"arm makes all containers and buffers visible and capture-excluded");}
  check(bridge.resources().contains("captures=2")&&bridge.resources().contains("localBufferHandlesClosed=2"),"arm captured both panels and closed local buffer handles");
  SurfaceControl.Transaction.commit.run();check(bridge.ready(),"single arm commit supplies complete shield evidence");
  check(SurfaceControl.Transaction.applies==1,"readiness does not enqueue a redundant transaction");
  java.util.Map<String,Object> physical=(java.util.Map<String,Object>)get(bridge,"physicalContainers");
  SurfaceControl layer=(SurfaceControl)physical.get("cover"),container=(SurfaceControl)physical.get("inner");
  bridge.followPhysicalStacks();check(layer.stack==10&&container.stack==20,"frozen images initially follow physical panels");
  android.hardware.display.IDisplayManager.swapped=true;bridge.followPhysicalStacks();check(layer.stack==20&&container.stack==10,"frozen images follow physical identity through swap");
  check(SurfaceControl.Transaction.removals==0&&SurfaceControl.Transaction.clears==0,"arm retains buffer ownership");
  check(layer.skipScreenshot,"primary refresh must exclude held screenshot pixels");
  bridge.followPhysicalStacks();check(layer.stack==20&&container.stack==10,"held screenshots remain on original physical panels after commit");
  android.hardware.display.IDisplayManager.stack0=30;bridge.followPhysicalStacks();check(container.stack==30&&layer.stack==20,"physical panel stack changes are followed");
  check(SurfaceControl.Transaction.hides==0,"hold must never hide screenshot before reveal");
  bridge.close();check(!bridge.ready(),"removal revokes promotion even before commit");check(!bridge.isClosed(),"must wait for removal commit");check(!layer.released,"keep handle until commit");check(SurfaceControl.Transaction.removals==4&&SurfaceControl.Transaction.clears==2,"remove all layers and detach both frozen buffers");
  bridge.close();check(SurfaceControl.Transaction.removals==4,"pending removal is not submitted repeatedly");
  SurfaceControl.Transaction.commit.run();check(bridge.isClosed()&&layer.released&&container.released,"commit releases handles");check(list(bridge,"surfaces").isEmpty()&&list(bridge,"bufferLayers").isEmpty(),"clear strong references");check(bridge.resources().contains("removeCommitted=true"),"report confirmation");
  System.out.println("LiveB8BridgeCleanupTest: delayed commit, buffer detach, idempotency, handle release and reference cleanup passed");
 }
}
