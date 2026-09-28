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
  // Owner-requested 3.5.1 upgrade: apply only these two values once.
  // Runs before activities/services read their settings; later edits persist.
  if(!prefs.getBoolean("stretch_glass_defaults_351",false)){
   prefs.edit().putFloat("end_stretch",1.25f).putFloat("intensity",1f)
    .putBoolean("stretch_glass_defaults_351",true).apply();
  }
  // Reset only Advanced animation controls once per installed build.
  // Removing these keys uses the same defaults as the UI and renderer.
  String build=BuildConfig.VERSION_NAME+":"+BuildConfig.VERSION_CODE;
  if(!build.equals(prefs.getString("advanced_animation_defaults_build",""))){
   SharedPreferences.Editor edit=prefs.edit();
   for(String key:new String[]{
    "handoff_angle","fade_smoothing_ms","fade_gradualness","window_reveal_v2",
    "cover_vertical_compression","startup_easing","early_stretch","enhanced_end_stretch","end_stretch",
    "inner_vertical_compression","inner_startup_easing","inner_early_stretch","inner_enhanced_end_stretch","inner_end_stretch",
    "antialias_enabled","antialias_method_v2","antialias_strength","content_fps","frosted_reflection",
    "seam_offset","open_threshold","closed_threshold"
   })edit.remove(key);
   edit.putString("advanced_animation_defaults_build",build).commit();
  }
  // Keep the selected animation style and all settings outside Advanced intact.
  if(!prefs.contains("animation_mode"))prefs.edit().putString("animation_mode",AnimationModePolicy.DEFAULT).apply();
  if(!prefs.getBoolean("preview_default_203",false))prefs.edit().putBoolean("cover_preview",true).putBoolean("preview_default_203",true).apply();
  new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> org.duofold.live.wallpaperlayer.WallpaperRestore.resume(this));
 }
}
