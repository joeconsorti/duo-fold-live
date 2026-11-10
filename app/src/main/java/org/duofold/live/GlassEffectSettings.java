package org.duofold.live;

import android.content.SharedPreferences;

/** One effect profile for Duo and Live Screen, on both physical panels. */
final class GlassEffectSettings {
 static final String RESOLUTION_KEY="effect_resolution_percent";
 private GlassEffectSettings(){}
 static int resolution(SharedPreferences prefs){
  return RenderQuality.resolution(prefs.getInt(RESOLUTION_KEY,RenderQuality.DEFAULT_RESOLUTION));
 }
 static int bufferDimension(SharedPreferences prefs,int logical){
  return bufferDimension(prefs,logical,resolution(prefs));
 }
 static int bufferDimension(SharedPreferences prefs,int logical,int effectiveResolution){
  return RenderQuality.bufferDimension(logical,effectiveResolution,
   prefs.getBoolean("antialias_enabled",true),prefs.getInt("antialias_method_v2",0));
 }
 static void initialize(SharedPreferences prefs){
  SharedPreferences.Editor edit=prefs.edit().putInt(RESOLUTION_KEY,resolution(prefs))
   .remove("full_resolution_glass").remove("motion_glass_invert");
  if(!prefs.contains(AdaptiveResolution.ENABLED_KEY))edit.putBoolean(AdaptiveResolution.ENABLED_KEY,true);
  // These already match the approved screenshot profile. Keep later user tuning.
  if(!prefs.contains("intensity"))edit.putFloat("intensity",RenderQuality.DEFAULT_GLASS);
  if(!prefs.contains("blur_strength"))edit.putFloat("blur_strength",RenderQuality.DEFAULT_BLUR);
  edit.commit();
 }
}
