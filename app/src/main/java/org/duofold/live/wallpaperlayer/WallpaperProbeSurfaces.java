package org.duofold.live.wallpaperlayer;
import android.view.*;
import java.util.*;
/** Main-thread registry of our photo leaves only. Never excludes Samsung parents or anchors. */
public final class WallpaperProbeSurfaces {
 static LayerHost host;
 public static SurfaceControl[] captureExclusions() throws Exception {
  if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Main thread required");
  return host==null||!host.running ? new SurfaceControl[0] : snapshot();
 }
 public static SurfaceControl[] snapshot() throws Exception {
  if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Main thread required");
  LayerHost h=host;if(h==null||!h.running)throw new IllegalStateException("Wallpaper host unavailable");
  ArrayList<SurfaceControl> out=new ArrayList<>();
  if(h.nativePhoto!=null)for(NativePhotoLayer.Entry e:h.nativePhoto.layers.values()){add(out,e.child);if(e.wake!=null)add(out,e.wake);}
  if(h.homePhoto!=null){for(HomePhotoLayer.Entry e:h.homePhoto.entries.values())if(e.photo!=null)add(out,e.photo);for(HomePhotoLayer.Entry e:h.homePhoto.bridges.values())if(e.photo!=null)add(out,e.photo);}
  for(LayerHost.Panel p:h.panels.values()){
   Object root=View.class.getDeclaredMethod("getViewRootImpl").invoke(p.view);
   if(root==null)throw new IllegalStateException("Wallpaper window remapping");
   add(out,(SurfaceControl)root.getClass().getMethod("getSurfaceControl").invoke(root));
  }
  if(out.isEmpty()||out.size()>48)throw new IllegalStateException("Incomplete/oversized wallpaper surface set");
  return out.toArray(new SurfaceControl[0]);
 }
 private static void add(List<SurfaceControl> out,SurfaceControl s){if(s==null||!s.isValid())throw new IllegalStateException("Wallpaper surface changed");out.add(s);}
}
