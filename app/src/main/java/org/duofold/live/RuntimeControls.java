package org.duofold.live;
import android.content.*;
import org.duofold.live.wallpaperlayer.WallpaperRestore;
/** User intent is authoritative across process death, updates and boot. */
public final class RuntimeControls {
 private RuntimeControls(){}
 public static boolean wanted(Context c){
  android.content.SharedPreferences p=c.getSharedPreferences("standalone",0);
  return RuntimeConsent.ready(c)&&(p.getBoolean("enabled",false)||WallpaperRestore.enabled(c)||FoldAwakeDefault.enabled(c));
 }
 public static void stopAll(Context c){
  c.getSharedPreferences("standalone",0).edit().putBoolean("enabled",false).putBoolean("b8_lab_enabled",false).putBoolean("custom_wallpaper_enabled",false).commit();
  FoldAwakeDefault.setEnabled(c,false);
  try{WallpaperRestore.setEnabled(c,false);}catch(java.io.IOException e){RecoveryLog.add("Wallpaper stop: "+e.getMessage());}
  WallpaperRestore.reconnect();
  LiveAngles.stopCurrent();
  StandaloneService s=StandaloneService.Companion.getInstance();if(s!=null)s.restart();
  c.stopService(new Intent(c,FoldBackgroundService.class));
 }
}
