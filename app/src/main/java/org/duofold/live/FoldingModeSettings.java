package org.duofold.live;

import android.content.SharedPreferences;

/** Public mode choice. Legacy route flags are derived here, never exposed as modes. */
final class FoldingModeSettings {
 static final String KEY="folding_mode", LIVE="live", SCREENSHOT="screenshot";
 static final String DEFAULT_MIGRATION="duo_mode_default_alpha62";
 static final String DUO_LABEL="Duo Mode", LIVE_LABEL="Live Screen Mode (Legacy)";
 private FoldingModeSettings(){}
 static boolean screenshot(SharedPreferences prefs){
  // Adopt Duo once on install/upgrade. An explicit later selection survives restarts.
  if(!prefs.getBoolean(DEFAULT_MIGRATION,false))return true;
  return !LIVE.equals(prefs.getString(KEY,SCREENSHOT));
 }
 static void initialize(SharedPreferences prefs,boolean screenshot){write(prefs,screenshot,false);}
 static void select(SharedPreferences prefs,boolean screenshot){write(prefs,screenshot,true);}
 private static void write(SharedPreferences prefs,boolean screenshot,boolean selected){
  SharedPreferences.Editor edit=prefs.edit().putString(KEY,screenshot?SCREENSHOT:LIVE)
   .putBoolean(DEFAULT_MIGRATION,true)
   .putBoolean("dual",false).putBoolean("live_b8_enabled",screenshot)
   .putBoolean("b8_snapshot_test",screenshot).putBoolean("screenshot_native_test",false)
   .putBoolean("incoming_app_hold",false).putBoolean("screenshot_endpoint_fade",false);
  if(screenshot){
   edit.putBoolean("cover_preview",false).putBoolean(HandoffVisualPolicy.KEY,true)
    .putBoolean("screenshot_camera_startup",true).putBoolean("closing_app_return",true);
  }else if(selected)edit.putBoolean("cover_preview",true);
  edit.commit();
 }
}
