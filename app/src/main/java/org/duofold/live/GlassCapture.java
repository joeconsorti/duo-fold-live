package org.duofold.live;
import android.os.*;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.view.SurfaceControl;
import java.io.*;
/** Shell-side isolated layer capture. Never requests secure/protected capture. */
final class GlassCapture extends Binder {
 static final String TOKEN="org.duofold.live.capture.GlassCapture";
 private final int owner;private volatile boolean closed;private DisplayCaptureApi captureApi;
 GlassCapture(int uid){owner=uid;}
 // Not synchronized: AngleReader.stop() must not queue behind an in-flight capture; onTransact checks `closed`.
 void close(){closed=true;}
 private Object service(String name,String stub)throws Exception{
  IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
  return Class.forName(stub).getMethod("asInterface",IBinder.class).invoke(null,b);
 }
 protected synchronized boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  data.enforceInterface(TOKEN);if(Binder.getCallingUid()!=owner)throw new SecurityException("Wrong caller");
  if(closed)throw new IllegalStateException("Reader stopped");
  long identity=Binder.clearCallingIdentity();Bundle result=new Bundle();
  SurfaceControl[] excluded=new SurfaceControl[0];HardwareBuffer buffer=null;
  try{
   if(code==2){
    String action=data.readString();
    String previous=command("settings","get","system","fold_lock_behavior").trim();
    if(!java.util.Arrays.asList("null","stay_awake_on_fold_key","selective_stay_awake_key","sleep_on_fold_key").contains(previous))throw new IllegalStateException("Unrecognized existing fold setting; use Samsung Display settings");
    String expected;
    if("always".equals(action)){expected="stay_awake_on_fold_key";if(!expected.equals(previous))command("settings","put","system","fold_lock_behavior",expected);}
    else if("restore".equals(action)){
     String original=data.readString();expected=original;
     if("null".equals(original))command("settings","delete","system","fold_lock_behavior");
     else if(java.util.Arrays.asList("stay_awake_on_fold_key","selective_stay_awake_key","sleep_on_fold_key").contains(original))command("settings","put","system","fold_lock_behavior",original);
     else throw new IllegalArgumentException("Unknown original fold setting");
    }else throw new IllegalArgumentException("Unknown action");
    String observed=command("settings","get","system","fold_lock_behavior").trim();
    if(!expected.equals(observed))throw new IllegalStateException("Fold setting readback mismatch: "+observed);
    result.putString("previous",previous);result.putString("value",observed);result.putBoolean("ok",true);
   }else if(code==4){
    int task=data.readInt();String physical=data.readString();int expectedWidth=data.readInt(),expectedHeight=data.readInt();
    Object wm=service("window","android.view.IWindowManager$Stub");
    if((boolean)Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked");
    Object dm=service("display","android.hardware.display.IDisplayManager$Stub");
    Object info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,0);
    if(info==null||!java.util.Objects.equals(physical,String.valueOf(info.getClass().getField("uniqueId").get(info)))||info.getClass().getField("state").getInt(info)!=2)throw new IllegalStateException("Incoming panel not ON on D0");
    int width=info.getClass().getField("logicalWidth").getInt(info),height=info.getClass().getField("logicalHeight").getInt(info);
    if(Math.min(width,height)!=Math.min(expectedWidth,expectedHeight)||Math.max(width,height)!=Math.max(expectedWidth,expectedHeight))throw new IllegalStateException("Waiting for destination dimensions");
    String state=EndpointWindowState.summarize(EndpointWindowTrace.dump(),task);
    result.putBoolean("ok",state.equals("READY; HAS_DRAWN"));result.putString("error",state);
   }else if(code==1 || code==3 || code==5){
    long captureStarted=SystemClock.elapsedRealtime();
    int n=data.readInt();if(n<1||n>8)throw new IllegalArgumentException("No valid overlay exclusion surfaces");
    excluded=new SurfaceControl[n];for(int i=0;i<n;i++)excluded[i]=data.readTypedObject(SurfaceControl.CREATOR);
    int displayId=(code==1||code==5)&&data.dataAvail()>=4?data.readInt():0;
    int exactTask=code==5?data.readInt():-1;
    String expectedPhysical=code==5?data.readString():null;
    if(displayId<0||displayId>1)throw new IllegalArgumentException("Unsupported capture display");
    for(SurfaceControl sc:excluded)if(sc==null||!sc.isValid())throw new IllegalStateException("Overlay surface changed");
    Object wm=service("window","android.view.IWindowManager$Stub");Class<?> wa=Class.forName("android.view.IWindowManager");
    if((boolean)wa.getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked — capture paused");
    Object dm=service("display","android.hardware.display.IDisplayManager$Stub");Object info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,displayId);
    if(code==5){
     if(exactTask<0||!java.util.Objects.equals(expectedPhysical,String.valueOf(info.getClass().getField("uniqueId").get(info))))throw new IllegalStateException("Incoming identity changed");
     if(!EndpointWindowState.summarize(EndpointWindowTrace.dump(),exactTask,displayId).equals("READY; HAS_DRAWN"))throw new IllegalStateException("Exact incoming app not drawn");
    }
    int w=info.getClass().getField("logicalWidth").getInt(info),h=info.getClass().getField("logicalHeight").getInt(info);
    int rotation=info.getClass().getField("rotation").getInt(info);
    if(info.getClass().getField("state").getInt(info)!=2)throw new IllegalStateException("Screen off — capture paused");
    if(captureApi==null)captureApi=DisplayCaptureApi.resolve(Class::forName,wa,Rect.class,SurfaceControl[].class);
    Class<?> builder=captureApi.builder;Object b=captureApi.constructor.newInstance();
    builder.getMethod("setSourceCrop",Rect.class).invoke(b,new Rect(0,0,w,h));
    builder.getMethod("setFrameScale",float.class).invoke(b,Math.min(1f,(code==1?640f:1440f)/Math.max(w,h)));
    builder.getMethod("setExcludeLayers",SurfaceControl[].class).invoke(b,(Object)excluded);
    Object args=builder.getMethod("build").invoke(b);
    Object shot;
    try(CaptureCompletion<Object> completion=new CaptureCompletion<>(GlassCapture::releaseShot)){
     // JNI keeps a weak reference to this callback; keep a strong reference until completion.
     Object callback=captureApi.statusCallback
       ? (java.util.function.ObjIntConsumer<Object>)completion::accept
       : (java.util.function.Consumer<Object>)(item->completion.accept(item,0));
     Object listener=captureApi.listenerConstructor.newInstance(callback);
     try{
      captureApi.capture.invoke(wm,displayId,args,listener);
      shot=completion.await(250);
     }finally{java.lang.ref.Reference.reachabilityFence(callback);java.lang.ref.Reference.reachabilityFence(listener);}
    }
    result.putString("backend","WindowManager display "+displayId+" / "+captureApi.name);
    buffer=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);
    if((boolean)shot.getClass().getMethod("containsSecureLayers").invoke(shot))throw new IllegalStateException("Protected content omitted");
    Bitmap hardware=(Bitmap)shot.getClass().getMethod("asBitmap").invoke(shot);
    if(hardware==null)throw new IllegalStateException("No readable frame");
    Bitmap bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);hardware.recycle();
    Object after=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,displayId);
    if(after==null || after.getClass().getField("state").getInt(after)!=2 || after.getClass().getField("logicalWidth").getInt(after)!=w || after.getClass().getField("logicalHeight").getInt(after)!=h || after.getClass().getField("rotation").getInt(after)!=rotation ||
       !java.util.Objects.equals(info.getClass().getField("uniqueId").get(info),after.getClass().getField("uniqueId").get(after))){bitmap.recycle();throw new IllegalStateException("Panel changed during capture; retry before handoff");}
    if(code==5&&!EndpointWindowState.summarize(EndpointWindowTrace.dump(),exactTask,displayId).equals("READY; HAS_DRAWN")){bitmap.recycle();throw new IllegalStateException("Exact app moved during capture");}
    result.putString("physicalId",String.valueOf(info.getClass().getField("uniqueId").get(info)));result.putInt("displayId",displayId);result.putParcelable("bitmap",bitmap);result.putInt("width",w);result.putInt("height",h);result.putInt("rotation",rotation);result.putLong("stamp",captureStarted);result.putBoolean("ok",true);
   }else throw new IllegalArgumentException("Unknown operation");
  }catch(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();result.putString("error",cause.getClass().getSimpleName()+": "+cause.getMessage());}
  finally{if(buffer!=null)buffer.close();for(SurfaceControl sc:excluded)if(sc!=null)sc.release();Binder.restoreCallingIdentity(identity);}
  reply.writeNoException();reply.writeBundle(result);return true;
 }
 private static void releaseShot(Object shot){
  try{HardwareBuffer b=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);if(b!=null)b.close();}catch(Exception ignored){}
 }
 private String command(String...args)throws Exception{
  // Bounded like FoldRotationHold: a hung `settings` process used to hold this object's monitor forever, so
  // AngleReader.stop() (capture.close()) and with it every later angle poll blocked for good. The output is a
  // few bytes, well under the pipe buffer, so waiting before reading cannot deadlock.
  java.lang.Process process=new ProcessBuilder(args).redirectErrorStream(true).start();
  try{
   if(!process.waitFor(2,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Settings command timed out: "+String.join(" ",args));
   String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   if(process.exitValue()!=0)throw new IOException(output);return output;
  }finally{if(process.isAlive())process.destroyForcibly();}
 }
}
