package org.duofold.live;
import android.os.*;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.view.SurfaceControl;
import java.lang.reflect.*;
import java.util.function.*;

/** Diagnostic only: physical compositor capture includes independent root layers.
 * Never substitutes WindowManager's subtree capture, which can omit these roots.
 */
final class MovingSceneCapture {
 static Bundle captureWorkspace(int display,String physical,int[] tasks)throws Exception{
  MultiWindowTasks.Snapshot before=MultiWindowTasks.requireTasks(tasks);
  Bundle out=capture(display,-1,physical,false);
  try{
   if(!before.signature.equals(MultiWindowTasks.requireTasks(tasks).signature))throw new IllegalStateException("Workspace changed during diagnostic");
   out.putString("provenance","workspace="+java.util.Arrays.toString(tasks)+"; unchanged native task membership/geometry; physical composition includes display layers; no single-task assertion");
   return out;
  }catch(Exception e){Bitmap b=out.getParcelable("bitmap",Bitmap.class);if(b!=null)b.recycle();throw e;}
 }
 static Object service(String name,String type)throws Exception{
  IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
  return Class.forName(type+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);
 }
 static Bundle capture(int display,int task,String physical)throws Exception{return capture(display,task,physical,true);}
 static Bundle capture(int display,int task,String physical,boolean windowObservation)throws Exception{
  if(display<0||display>1||physical==null||!physical.matches("local:[0-9]+"))throw new IllegalArgumentException("Invalid physical display");
  Object wm=service("window","android.view.IWindowManager");
  if((boolean)Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked");
  Object dm=service("display","android.hardware.display.IDisplayManager");Method info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class);
  Object before=info.invoke(dm,display);if(!matches(before,physical))throw new IllegalStateException("Physical mapping pending");
  int w=number(before,"logicalWidth"),h=number(before,"logicalHeight"),rotation=number(before,"rotation");
  IBinder token=(IBinder)SurfaceControl.class.getMethod("getPhysicalDisplayToken",long.class).invoke(null,Long.parseUnsignedLong(physical.substring(6)));
  if(token==null)throw new IllegalStateException("Physical capture token unavailable");
  long began=SystemClock.elapsedRealtime();Object shot=null;String backend="";
  for(String family:CaptureApi.FAMILIES){
   Class<?> api,args,builder,listener;Constructor<?> constructor;boolean status;
   try{
    api=Class.forName(family);args=Class.forName(family+"$DisplayCaptureArgs");builder=Class.forName(family+"$DisplayCaptureArgs$Builder");listener=Class.forName(family+"$ScreenCaptureListener");
    try{constructor=listener.getConstructor(ObjIntConsumer.class);status=true;}catch(NoSuchMethodException e){constructor=listener.getConstructor(Consumer.class);status=false;}
    api.getMethod("captureDisplay",args,listener);
   }catch(ReflectiveOperationException unsupported){continue;}
   Object b=builder.getConstructor(IBinder.class).newInstance(token);
   PhysicalCaptureSize.configure(b,w,h);
   try(CaptureCompletion<Object> completion=new CaptureCompletion<>(MovingSceneCapture::release)){
    Object callback=status?(ObjIntConsumer<Object>)completion::accept:(Consumer<Object>)(value->completion.accept(value,0));
    Object l=constructor.newInstance(callback);
    try{Object result=api.getMethod("captureDisplay",args,listener).invoke(null,builder.getMethod("build").invoke(b),l);if(result instanceof Integer&&(Integer)result!=0)throw new IllegalStateException("Physical capture rejected="+result);shot=completion.await(350);}
    finally{java.lang.ref.Reference.reachabilityFence(callback);java.lang.ref.Reference.reachabilityFence(l);}
   }
   backend=family+" physical compositor";break;
  }
  if(shot==null)throw new IllegalStateException("Physical compositor capture unavailable; no subtree fallback");
  HardwareBuffer buffer=null;Bitmap hardware=null,image=null;
  try{
   buffer=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);
   PhysicalCaptureSize.requireBounded(buffer.getWidth(),buffer.getHeight());
   if((boolean)shot.getClass().getMethod("containsSecureLayers").invoke(shot))throw new IllegalStateException("Protected content omitted");
   hardware=(Bitmap)shot.getClass().getMethod("asBitmap").invoke(shot);if(hardware==null)throw new IllegalStateException("No capture pixels");
   image=hardware.copy(Bitmap.Config.ARGB_8888,false);
   Object after=info.invoke(dm,display);
   if(!matches(after,physical)||w!=number(after,"logicalWidth")||h!=number(after,"logicalHeight")||rotation!=number(after,"rotation"))throw new IllegalStateException("Mapping changed during capture");
   Bundle out=new Bundle();out.putBoolean("ok",true);out.putParcelable("bitmap",image);image=null;out.putLong("stamp",began);out.putString("backend",backend);
   out.putInt("display",display);out.putString("physical",physical);out.putInt("width",w);out.putInt("height",h);out.putInt("rotation",rotation);out.putLong("completed",SystemClock.elapsedRealtime());
   String observation="NOT_SAMPLED";try{if(windowObservation)observation=EndpointWindowState.captureGuard(EndpointWindowTrace.dump(),task,display);}
   catch(Exception busy){observation="UNAVAILABLE: "+busy.getMessage();}
   out.putString("provenance",observation);return out;
  }finally{if(image!=null)image.recycle();if(hardware!=null)hardware.recycle();if(buffer!=null)buffer.close();}
 }
 /** Home diagnostic resolves the logical slot afresh for every physical frame.
  * No per-frame dumpsys: it must not contend with handoff readiness at 8 Hz. */
 static Bundle captureHomeInner(String physical,boolean observeWindow)throws Exception{
  String home=HomeCaptureIdentity.focused(true);
  Object dm=service("display","android.hardware.display.IDisplayManager");
  Method info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class);
  int display=-1;
  for(int id=0;id<=1;id++){
   Object i=info.invoke(dm,id);
   if(matches(i,physical)){
    int w=number(i,"logicalWidth"),h=number(i,"logicalHeight");
    if(Math.min(w,h)/(float)Math.max(w,h)<=.7f)throw new IllegalArgumentException("Inner panel only");
    if(display!=-1)throw new IllegalStateException("Physical mapping ambiguous");display=id;
   }
  }
  if(display<0)throw new IllegalStateException("Inner panel OFF or mapping pending");
  Bundle out=capture(display,-1,physical,false);
  try{
   if(!home.equals(HomeCaptureIdentity.focused(true)))throw new IllegalStateException("Launcher changed during sample");
   out.putString("focusedLauncher",home);
   if(observeWindow){
    try{
     Object atm=service("activity_task","android.app.IActivityTaskManager");
     Object root=Class.forName("android.app.IActivityTaskManager").getMethod("getFocusedRootTaskInfo").invoke(atm);
     int task=root.getClass().getField("taskId").getInt(root);
     out.putString("provenance",HomeWindowEvidence.detail(EndpointWindowTrace.dump(),task,0));
    }catch(Exception busy){out.putString("provenance","UNAVAILABLE: "+busy.getMessage());}
   }
   return out;
  }catch(Exception e){Bitmap b=out.getParcelable("bitmap",Bitmap.class);if(b!=null)b.recycle();throw e;}
 }
 static boolean matches(Object i,String physical)throws Exception{return i!=null&&physical.equals(String.valueOf(i.getClass().getField("uniqueId").get(i)))&&number(i,"state")==2;}
 static int number(Object i,String name)throws Exception{return i.getClass().getField(name).getInt(i);}
 static void release(Object shot){try{HardwareBuffer b=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);if(b!=null)b.close();}catch(Exception ignored){}}
}
