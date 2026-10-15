package org.duofold.live;
import android.view.SurfaceControl;
import java.util.ArrayList;
import java.util.List;
/**
 * Diagnostics: make Duo's own overlay layers visible to screen recordings so a transition can be
 * reviewed frame by frame as the user sees it. Normally they are setSkipScreenshot(true), which keeps
 * them out of Duo's own glass captures but also out of screenrecord. With
 * `adb shell setprop debug.duofold.record_visible 1` they are left visible and registered here instead,
 * and GlassCapture excludes them explicitly, so the glass still never samples its own overlays.
 * Lives in the Shizuku user-service process (HandoffFade, PreviewExpansion, InnerLiveMirror, GlassCapture).
 * Exception: Duo's main glass surfaces (code 7) are owned by MainGlassSurfaces and stay skipped while the live
 * cover->inner mirror is attached, because skip-screenshot is also what keeps them out of that mirror.
 */
final class RecordVisible {
 private RecordVisible(){}
 private static final List<SurfaceControl> layers=new ArrayList<>();
 private static long readAt;private static boolean cached;
 /** Cached for 1 s: called from per-frame transaction paths. */
 static synchronized boolean enabled(){
  long now=android.os.SystemClock.uptimeMillis();
  if(now-readAt>=1000){readAt=now;
   try{cached="1".equals(Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,"debug.duofold.record_visible",""));}
   catch(Exception e){cached=false;}}
  return cached;
 }
 /**
  * Replaces setSkipScreenshot(sc,true). Always sets the flag explicitly, so calling it on every update makes
  * a property change take effect on existing layers without restarting Duo (a force-stop would also remove
  * Duo from enabled_accessibility_services). Enabled: visible to recordings, excluded from glass capture.
  */
 static void hide(SurfaceControl.Transaction t,SurfaceControl sc)throws Exception{
  boolean record=enabled();
  SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,sc,!record);
  synchronized(layers){if(record){if(!layers.contains(sc))layers.add(sc);}else layers.remove(sc);}
 }
 /** Explicit skip state (MainGlassSurfaces): skipped layers are unregistered, recorded ones are excluded from glass capture. */
 static void set(SurfaceControl.Transaction t,SurfaceControl sc,boolean skip)throws Exception{
  SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,sc,skip);
  synchronized(layers){if(!skip){if(!layers.contains(sc))layers.add(sc);}else layers.remove(sc);}
 }
 /** Valid registered layers to add to a capture's exclusion list. */
 static SurfaceControl[] exclusions(){
  synchronized(layers){layers.removeIf(sc->sc==null||!sc.isValid());return layers.toArray(new SurfaceControl[0]);}
 }
 private static java.lang.reflect.Constructor<SurfaceControl> copier;private static boolean copierResolved;
 /**
  * Plan D4: GlassCapture's own handles to the registered layers, copied under the lock, so an owner thread
  * releasing its layer mid-capture cannot leave a released handle in the exclusion list. Copies are appended to
  * {@code owned} for the caller to release. If the (hidden) copy constructor is unavailable, the originals are
  * returned as before.
  */
 static SurfaceControl[] exclusionCopies(java.util.List<SurfaceControl> owned){
  synchronized(layers){
   layers.removeIf(sc->sc==null||!sc.isValid());
   if(!copierResolved){copierResolved=true;try{copier=SurfaceControl.class.getConstructor(SurfaceControl.class,String.class);}catch(Exception e){copier=null;}}
   SurfaceControl[] out=new SurfaceControl[layers.size()];
   for(int i=0;i<out.length;i++){
    SurfaceControl sc=layers.get(i);
    try{if(copier!=null){out[i]=copier.newInstance(sc,"DuoGlassCaptureExclusion");owned.add(out[i]);continue;}}catch(Exception ignored){}
    out[i]=sc;
   }
   return out;
  }
 }
}
