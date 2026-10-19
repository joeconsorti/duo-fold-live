package org.duofold.live;
import android.app.Application;
import android.content.SharedPreferences;
/** Applies the release's requested mode once; later user selections remain intact. */
public final class DuoApplication extends Application {
 @Override public void onCreate(){
  super.onCreate();
  PrivilegedAccess.init(this);
  registerActivityLifecycleCallbacks(new DeveloperShortcut());
  FoldAwakeDefault.persist(this);
  if(BuildConfig.IS_PLAY)getSystemService(android.app.NotificationManager.class).cancel(220);
  SharedPreferences prefs=getSharedPreferences("standalone",MODE_PRIVATE);
  // Retune motion once on upgrade; preserve later user adjustments.
  if(!prefs.getBoolean("motion_defaults_357_alpha4",false))prefs.edit()
   .putBoolean("motion_glass_invert",true).putFloat("motion_glass_projection_strength",1f)
   .putBoolean("motion_defaults_357_alpha4",true).commit();
  // Alpha.6 gives each panel its own facing direction; preserve sensitivity.
  if(!prefs.getBoolean("motion_direction_357_alpha6",false))prefs.edit()
   .putBoolean("motion_glass_invert",false).putBoolean("motion_direction_357_alpha6",true).commit();
  if(!prefs.getBoolean("defaults_170_applied",false)){
   prefs.edit().putBoolean("cover_preview",true).putBoolean("dual",false)
    .putBoolean("defaults_170_applied",true).apply();
  }
  // Owner-requested 3.5.1 upgrade: apply only these two values once.
  // Runs before activities/services read their settings; later edits persist.
  if(!prefs.getBoolean("stretch_glass_defaults_351",false)){
   prefs.edit().putFloat("end_stretch",1.25f).putFloat("intensity",.4f)
    .putBoolean("stretch_glass_defaults_351",true).apply();
  }
  // Owner-approved stable 3.5.4: select Smooth once on install/upgrade, then preserve choices.
  if(!prefs.getBoolean("smooth_default_354",false))prefs.edit()
   .putFloat("smoothing_ms",FrameSmoothing.DEFAULT_MS).putBoolean("smooth_default_354",true).commit();
  if(prefs.contains("closing_direct_power"))prefs.edit().remove("closing_direct_power").apply();
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
  // Alpha.24 mistakenly stored the transport as an animation style. Preserve its route.
  if("screenshot".equals(prefs.getString("animation_mode","")))prefs.edit().putString("animation_mode",AnimationModePolicy.DEFAULT).apply();
  // Owner-requested default on install/upgrade, once. Subsequent mode choices persist.
  if(!prefs.getBoolean("live_screen_default_353_27",false))prefs.edit()
   .putBoolean("dual",false).putBoolean("cover_preview",true)
   .putBoolean("live_screen_default_353_27",true).commit();
  // Screenshot mode stays unavailable until explicitly unlocked with the 10s gesture.
  ScreenshotModeAccess.enforce(prefs);
  // Split legacy direction-only styles into independent appearance and behavior.
  String oldMode=prefs.getString("animation_mode",AnimationModePolicy.DEFAULT);
  prefs.edit().putString("animation_behavior",AnimationModePolicy.initialBehavior(prefs.getString("animation_behavior",null),oldMode))
   .putString("animation_mode",AnimationModePolicy.style(oldMode)).commit();
  // Endpoint cover-up experiments are parked; clear legacy values as well as gating execution.
  if(prefs.getBoolean("incoming_app_hold",true)||prefs.getBoolean("screenshot_endpoint_fade",false))
   prefs.edit().putBoolean("incoming_app_hold",false).putBoolean("screenshot_endpoint_fade",false).commit();
  new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> org.duofold.live.wallpaperlayer.WallpaperRestore.resume(this));
 }
}
