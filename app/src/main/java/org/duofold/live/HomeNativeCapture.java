package org.duofold.live;
import android.graphics.*;
import android.os.SystemClock;
import android.view.SurfaceControl;
import android.hardware.HardwareBuffer;
/** Composed Home plus an independent launcher validation probe.
 * The probe is never painted into the returned image. Borrowed exclusions,
 * owned buffers; no layer visibility or wallpaper setting changes. */
final class HomeNativeCapture {
 static HomeNativeComposition.Result capture(Object wm,Class<?> wa,int display,int w,int h,SurfaceControl[] excluded,long launcherUid,HomeContentPolicy.Sample reference)throws Exception{
  DisplayCaptureApi api=DisplayCaptureApi.resolve(Class::forName,wa,Rect.class,SurfaceControl[].class);
  Object[] callbacks=new Object[2],listeners=new Object[2];Bitmap launcher=null,scene=null;
  // Dispatch both captures before waiting. They share one callback budget and
  // existing pre/post window guards; there is no added settling timer or polling.
  try(CaptureCompletion<Object> front=new CaptureCompletion<>(HomeNativeCapture::releaseShot);
      CaptureCompletion<Object> back=new CaptureCompletion<>(HomeNativeCapture::releaseShot)){
   long deadline=SystemClock.elapsedRealtime()+250;
   for(int i=0;i<2;i++){
    Object b=api.constructor.newInstance();
    api.builder.getMethod("setSourceCrop",Rect.class).invoke(b,new Rect(0,0,w,h));
    api.builder.getMethod("setFrameScale",float.class).invoke(b,Math.min(1f,1440f/Math.max(w,h)));
    api.builder.getMethod("setExcludeLayers",SurfaceControl[].class).invoke(b,(Object)excluded);
    // Only the validation probe is owner-filtered. A replacement photo can
    // belong to a different UID than the registered live wallpaper. Filtering
    // the displayed image to that wallpaper UID exposes the underlying live
    // wallpaper even when the full composition (including the bars) has a photo.
    if(i==0)api.restrictOwner(b,launcherUid);
    CaptureCompletion<Object> completion=i==0?front:back;
    callbacks[i]=api.statusCallback?(java.util.function.ObjIntConsumer<Object>)completion::accept:(java.util.function.Consumer<Object>)(item->completion.accept(item,0));
    listeners[i]=api.listenerConstructor.newInstance(callbacks[i]);
    api.capture.invoke(wm,display,api.builder.getMethod("build").invoke(b),listeners[i]);
   }
   launcher=copyOwnedShot(front.await(Math.max(0,deadline-SystemClock.elapsedRealtime())));
   // A faded launcher fails this attempt on either panel. The completion owns
   // the composed shot until we actually need its pixels, even if it arrives late.
   HomeNativeComposition.LauncherProbe probe = HomeNativeComposition.validateLauncher(launcher, reference);
   launcher.recycle();
   launcher = null;
   scene=copyOwnedShot(back.await(Math.max(0,deadline-SystemClock.elapsedRealtime())));
   return HomeNativeComposition.validateScene(scene,probe);
  }finally{
   java.lang.ref.Reference.reachabilityFence(callbacks);java.lang.ref.Reference.reachabilityFence(listeners);
   if(launcher!=null)launcher.recycle();if(scene!=null)scene.recycle();
  }
 }
 private static Bitmap copyOwnedShot(Object shot)throws Exception{
  Bitmap hardware=null;
  try{
   if((boolean)shot.getClass().getMethod("containsSecureLayers").invoke(shot))throw new IllegalStateException("Protected Home content omitted");
   hardware=(Bitmap)shot.getClass().getMethod("asBitmap").invoke(shot);
   if(hardware==null)throw new IllegalStateException("No readable Home frame");
   Bitmap copy=hardware.copy(Bitmap.Config.ARGB_8888,false);
   if(copy==null)throw new IllegalStateException("No readable Home copy");return copy;
  }finally{if(hardware!=null)hardware.recycle();releaseShot(shot);}
 }
 private static void releaseShot(Object shot){
  try{HardwareBuffer b=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);if(b!=null)b.close();}catch(Exception ignored){}
 }
}
