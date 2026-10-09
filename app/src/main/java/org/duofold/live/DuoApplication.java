package org.duofold.live;
import android.app.Application;
import android.content.SharedPreferences;
/** Applies the release's requested mode once; later user selections remain intact. */
public final class DuoApplication extends Application {
 @Override public void onCreate(){
  super.onCreate();
  safeModeCheck();
  org.duofold.live.root.Shizuku.init(this);
  registerActivityLifecycleCallbacks(new DeveloperShortcut());
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
 /**
  * If the phone was restarted while the effect was on screen, or this is the first run of a build with new overlay code,
  * start with the animation OFF so it can never lock the user out. The user turns it on again in the app.
  */
 private void safeModeCheck(){
  try{
   android.content.SharedPreferences safety=getSharedPreferences("safety",0);
   android.content.SharedPreferences standalone=getSharedPreferences("standalone",0);
   int boot=android.provider.Settings.Global.getInt(getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,0);
   boolean rebooted=safety.getInt("boot_count",-1)!=boot;
   boolean stuckAtReboot=rebooted&&safety.getBoolean("overlay_visible",false);
   boolean firstRunOfThisBuild=!safety.getBoolean("overlay_safe_v3",false);
   if(stuckAtReboot||firstRunOfThisBuild){
    standalone.edit().putBoolean("enabled",false).apply();
    RecoveryLog.init(this);
    RecoveryLog.add(stuckAtReboot?"Safe mode: the effect was on screen when the phone restarted; animation turned off":"Safe mode: first run of this build; animation turned off");
   }
   String primaryMode=standalone.getString("primary_mode","wm");
   if(!"wm".equals(primaryMode)&&!"free".equals(primaryMode))standalone.edit().putString("primary_mode","wm").apply(); // "window" was removed
   safety.edit().putInt("boot_count",boot).putBoolean("overlay_visible",false).putBoolean("overlay_safe_v3",true).apply();
  }catch(Throwable ignored){}
 }
}
