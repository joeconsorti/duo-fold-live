package org.duofold.live;
import android.view.SurfaceControl;
import java.util.ArrayList;
import java.util.List;
/**
 * Duo's main glass overlay surfaces (code 7, the display-0 FrostSurface roots), held in the Shizuku service so
 * their skip-screenshot flag can follow the live mirror. Skip-screenshot does two jobs here: it keeps the glass
 * out of Duo's own captures and screen recordings, and it keeps it out of the live cover->inner mirror. In
 * record-visible diagnostics the glass is recorded except while the mirror is live, so captures never change
 * what the user sees (plan B1). Only the newest MAX_SURFACES handles are kept: a held handle keeps an old
 * window's layer alive offscreen.
 */
final class MainGlassSurfaces {
 static final int MAX_SURFACES=4;
 private final List<SurfaceControl> surfaces=new ArrayList<>();
 private Boolean applied;
 static boolean skipFor(boolean recordVisible,boolean mirrorLive){return !recordVisible||mirrorLive;}
 synchronized void add(SurfaceControl sc,boolean skip)throws Exception{
  prune();
  while(surfaces.size()>=MAX_SURFACES)surfaces.remove(0).release();
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){RecordVisible.set(t,sc,skip);t.apply();}
  surfaces.add(sc);applied=skip;
 }
 /** Called on every angle poll; a transaction only when the desired state changes. */
 synchronized void update(boolean skip){
  if(applied!=null&&applied==skip)return;
  prune();
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){for(SurfaceControl sc:surfaces)RecordVisible.set(t,sc,skip);t.apply();applied=skip;}
  catch(Exception ignored){applied=null;}
 }
 synchronized void close(){
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){for(SurfaceControl sc:surfaces)if(sc.isValid())RecordVisible.set(t,sc,true);t.apply();}catch(Exception ignored){}
  for(SurfaceControl sc:surfaces)sc.release();
  surfaces.clear();applied=null;
 }
 private void prune(){surfaces.removeIf(sc->{if(sc.isValid())return false;sc.release();return true;});}
}
