package org.duofold.live;
import android.os.*;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.view.SurfaceControl;
import java.io.*;
/** Shell-side isolated layer capture. Never requests secure/protected capture. */
final class GlassCapture extends Binder {
 static final String TOKEN="org.duofold.live.capture.GlassCapture";
 private final int owner;private boolean closed;private DisplayCaptureApi captureApi;
 GlassCapture(int uid){owner=uid;}
 synchronized void close(){closed=true;}
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
   }else if(code==1 || code==3){
    long captureStarted=SystemClock.elapsedRealtime();
    int n=data.readInt();if(n<1||n>4)throw new IllegalArgumentException("No valid overlay exclusion surfaces");
    excluded=new SurfaceControl[n];for(int i=0;i<n;i++)excluded[i]=data.readTypedObject(SurfaceControl.CREATOR);
    for(SurfaceControl sc:excluded)if(sc==null||!sc.isValid())throw new IllegalStateException("Overlay surface changed");
    Object wm=service("window","android.view.IWindowManager$Stub");Class<?> wa=Class.forName("android.view.IWindowManager");
    if((boolean)wa.getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked — capture paused");
    Object dm=service("display","android.hardware.display.IDisplayManager$Stub");Object info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,0);
    int w=info.getClass().getField("logicalWidth").getInt(info),h=info.getClass().getField("logicalHeight").getInt(info);
    if(info.getClass().getField("state").getInt(info)!=2)throw new IllegalStateException("Screen off — capture paused");
    if(captureApi==null)captureApi=DisplayCaptureApi.resolve(Class::forName,wa,Rect.class,SurfaceControl[].class);
    Class<?> builder=captureApi.builder;Object b=captureApi.constructor.newInstance();
    builder.getMethod("setSourceCrop",Rect.class).invoke(b,new Rect(0,0,w,h));
    builder.getMethod("setFrameScale",float.class).invoke(b,Math.min(1f,(code==3?1440f:640f)/Math.max(w,h)));
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
      captureApi.capture.invoke(wm,0,args,listener);
      shot=completion.await(250);
     }finally{java.lang.ref.Reference.reachabilityFence(callback);java.lang.ref.Reference.reachabilityFence(listener);}
    }
    result.putString("backend","WindowManager display 0 / "+captureApi.name);
    buffer=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);
    if((boolean)shot.getClass().getMethod("containsSecureLayers").invoke(shot))throw new IllegalStateException("Protected content omitted");
    Bitmap hardware=(Bitmap)shot.getClass().getMethod("asBitmap").invoke(shot);
    if(hardware==null)throw new IllegalStateException("No readable frame");
    Bitmap bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);hardware.recycle();
    Object after=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,0);
    if(after==null || after.getClass().getField("logicalWidth").getInt(after)!=w || after.getClass().getField("logicalHeight").getInt(after)!=h ||
       !java.util.Objects.equals(info.getClass().getField("uniqueId").get(info),after.getClass().getField("uniqueId").get(after))){bitmap.recycle();throw new IllegalStateException("Panel changed during capture; retry before handoff");}
    result.putParcelable("bitmap",bitmap);result.putInt("width",w);result.putInt("height",h);result.putLong("stamp",captureStarted);result.putBoolean("ok",true);
   }else throw new IllegalArgumentException("Unknown operation");
  }catch(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();result.putString("error",cause.getClass().getSimpleName()+": "+cause.getMessage());}
  finally{if(buffer!=null)buffer.close();for(SurfaceControl sc:excluded)if(sc!=null)sc.release();Binder.restoreCallingIdentity(identity);}
  reply.writeNoException();reply.writeBundle(result);return true;
 }
 private static void releaseShot(Object shot){
  try{HardwareBuffer b=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);if(b!=null)b.close();}catch(Exception ignored){}
 }
 private String command(String...args)throws Exception{
  java.lang.Process process=new ProcessBuilder(args).redirectErrorStream(true).start();
  String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
  if(process.waitFor()!=0)throw new IOException(output);return output;
 }
}
