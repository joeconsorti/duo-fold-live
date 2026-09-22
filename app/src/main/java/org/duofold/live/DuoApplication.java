package org.duofold.live;
import android.app.Application;
import android.content.SharedPreferences;
/** Applies the release's requested mode once; later user selections remain intact. */
public final class DuoApplication extends Application {
 @Override public void onCreate(){
  super.onCreate();
  FoldAwakeDefault.persist(this);
  SharedPreferences prefs=getSharedPreferences("standalone",MODE_PRIVATE);
  if(!prefs.getBoolean("defaults_170_applied",false)){
   prefs.edit().putBoolean("cover_preview",true).putBoolean("dual",false)
    .putBoolean("defaults_170_applied",true).apply();
  }
  // Set once; never overwrite a saved mode or the user's fade tuning on updates.
  if(!prefs.contains("animation_mode"))prefs.edit().putString("animation_mode",AnimationModePolicy.DEFAULT).apply();
  if(!prefs.getBoolean("preview_default_203",false))prefs.edit().putBoolean("cover_preview",true).putBoolean("preview_default_203",true).apply();
  new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> org.duofold.live.wallpaperlayer.WallpaperRestore.resume(this));
 }
}
