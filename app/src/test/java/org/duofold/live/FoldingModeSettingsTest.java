package org.duofold.live;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class FoldingModeSettingsTest {
 private final Map<String,Object> values=new HashMap<>();
 private final SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(
  SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
   switch(method.getName()){
    case "contains":return values.containsKey(args[0]);
    case "getString":case "getBoolean":case "getFloat":case "getInt":return values.getOrDefault(args[0],args[1]);
    case "edit":return editor();
    default:throw new AssertionError(method.getName());
   }
  });
 private SharedPreferences.Editor editor(){
  Map<String,Object> pending=new HashMap<>();
  return (SharedPreferences.Editor)Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(proxy,method,args)->{
   if(method.getName().startsWith("put")){pending.put((String)args[0],args[1]);return proxy;}
   if(method.getName().equals("remove")){pending.put((String)args[0],null);return proxy;}
   if(method.getName().equals("commit")){pending.forEach((k,v)->{if(v==null)values.remove(k);else values.put(k,v);});return true;}
   throw new AssertionError(method.getName());
  });
 }
 private void restart(){boolean selected=FoldingModeSettings.screenshot(prefs);FoldingModeSettings.initialize(prefs,selected);}
 private void assertRoute(boolean screenshot){
  assertEquals(screenshot,FoldingModeSettings.screenshot(prefs));
  assertFalse(prefs.getBoolean("dual",true));
  assertEquals(screenshot,prefs.getBoolean("live_b8_enabled",!screenshot));
  assertEquals(screenshot,prefs.getBoolean("b8_snapshot_test",!screenshot));
  assertFalse(prefs.getBoolean("screenshot_native_test",true));
  if(screenshot){assertFalse(prefs.getBoolean("cover_preview",true));assertTrue(prefs.getBoolean(HandoffVisualPolicy.KEY,false));}
 }
 @Test public void newInstallUsesDuoDefault(){restart();assertRoute(true);}
 @Test public void upgradeAdoptsDuoOnceThenPreservesUserChoice(){
  values.put(FoldingModeSettings.KEY,FoldingModeSettings.LIVE);
  values.put("live_screen_default_353_27",true);
  restart();assertRoute(true);
  FoldingModeSettings.select(prefs,false);restart();assertRoute(false);
  restart();assertRoute(false);
 }
 @Test public void oldPublicScreenshotMigratesWithoutUnlockGesture(){
  values.put("dual",true);values.put("screenshot_native_test",true);restart();assertRoute(true);
 }
 @Test public void privateCoordinatedPlainOrGlassBothAdoptTestedGlass(){
  for(boolean oldGlass:new boolean[]{false,true}){
   values.clear();values.put("live_b8_enabled",true);values.put("b8_snapshot_test",true);
   values.put(HandoffVisualPolicy.KEY,oldGlass);restart();assertRoute(true);
  }
 }
 @Test public void modeIsCapturedBeforeOldUpgradeDefaultsRun(){
  values.put("dual",true);boolean selected=FoldingModeSettings.screenshot(prefs);
  values.put("dual",false);values.put("cover_preview",true);
  FoldingModeSettings.initialize(prefs,selected);assertRoute(true);
 }
 @Test public void switchingBothDirectionsSurvivesRestartAndClearsOldRoutes(){
  FoldingModeSettings.select(prefs,true);restart();assertRoute(true);
  FoldingModeSettings.select(prefs,false);restart();assertRoute(false);
  assertTrue(prefs.getBoolean("cover_preview",false));
  FoldingModeSettings.select(prefs,true);restart();assertRoute(true);
 }
 @Test public void savedLiveWinsOverStalePrivateFlags(){
  values.put(FoldingModeSettings.DEFAULT_MIGRATION,true);
  values.put(FoldingModeSettings.KEY,FoldingModeSettings.LIVE);
  values.put("dual",true);values.put("live_b8_enabled",true);restart();assertRoute(false);
 }

 @Test public void sharedDefaultsMatchApprovedDuoProfileAndRetireFullResolutionFlag(){
  // The old lightweight screenshot renderer used 25% even with this flag set.
  values.put("full_resolution_glass",true);
  values.put("motion_glass_invert",true);
  GlassEffectSettings.initialize(prefs);restart();assertRoute(true);
  assertEquals(25,GlassEffectSettings.resolution(prefs));
  assertEquals(.4f,prefs.getFloat("intensity",0),0);
  assertEquals(1.5f,prefs.getFloat("blur_strength",0),0);
  assertFalse(prefs.contains("full_resolution_glass"));
  assertFalse(prefs.contains("motion_glass_invert"));
  assertEquals(612,GlassEffectSettings.bufferDimension(prefs,2448));
  FoldingModeSettings.select(prefs,false);GlassEffectSettings.initialize(prefs);restart();assertRoute(false);
  assertEquals(25,GlassEffectSettings.resolution(prefs));
  assertEquals(.4f,prefs.getFloat("intensity",0),0);assertEquals(1.5f,prefs.getFloat("blur_strength",0),0);
 }
 @Test public void resolutionAndGlassEditsCarryAcrossModesAndRestarts(){
  for(int resolution:new int[]{25,50,75,100})for(boolean shot:new boolean[]{true,false}){
   values.put(GlassEffectSettings.RESOLUTION_KEY,resolution);
   values.put("intensity",.65f);values.put("blur_strength",2.25f);
   values.put("glass_render_mode","light");
   FoldingModeSettings.select(prefs,shot);GlassEffectSettings.initialize(prefs);restart();assertRoute(shot);
   assertEquals(resolution,GlassEffectSettings.resolution(prefs));
   assertEquals(.65f,prefs.getFloat("intensity",0),0);assertEquals(2.25f,prefs.getFloat("blur_strength",0),0);
   assertEquals(2448*resolution/100,GlassEffectSettings.bufferDimension(prefs,2448));
  }
 }
 @Test public void malformedQualityFallsBackWithoutChangingSavedGlassTuning(){
  values.put(GlassEffectSettings.RESOLUTION_KEY,999);values.put("intensity",.75f);values.put("blur_strength",.9f);
  GlassEffectSettings.initialize(prefs);
  assertEquals(25,prefs.getInt(GlassEffectSettings.RESOLUTION_KEY,0));
  assertEquals(.75f,prefs.getFloat("intensity",0),0);assertEquals(.9f,prefs.getFloat("blur_strength",0),0);
 }
 @Test public void disabledAnimationAndSavedAppearanceArePreserved(){
  values.put("enabled",false);values.put("intensity",.4f);values.put("blur_strength",1.5f);
  values.put("animation_mode","duo_classic");values.put("closed_hinge_jitter_protection",false);
  FoldingModeSettings.select(prefs,true);restart();assertRoute(true);
  assertFalse(prefs.getBoolean("enabled",true));assertEquals(.4f,prefs.getFloat("intensity",0),0);
  assertEquals(1.5f,prefs.getFloat("blur_strength",0),0);assertEquals("duo_classic",prefs.getString("animation_mode",null));
  assertFalse(prefs.getBoolean("closed_hinge_jitter_protection",true));
 }
 @Test public void restartPreservesLivePreviewPreference(){
  FoldingModeSettings.select(prefs,false);values.put("cover_preview",false);restart();assertRoute(false);
  assertFalse(prefs.getBoolean("cover_preview",true));
 }
 @Test public void appearanceUpgradePreservesTransportDirectionAndGyroTuning(){
  for(boolean shot:new boolean[]{false,true})for(String old:new String[]{"plain_fade","duo_classic","fold_only","unfold_only"}){
   values.clear();FoldingModeSettings.select(prefs,shot);
   values.put("animation_mode",old);values.put("animation_style","classic");values.put("debug_mode",true);
   values.put("motion_glass",false);values.put("motion_glass_invert",true);values.put("motion_glass_inner_strength",1.7f);
   values.put("motion_glass_smoothing",130f);values.put("blur_strength",1.5f);values.put("intensity",.4f);
   AnimationModePolicy.initialize(prefs);restart();AnimationModePolicy.initialize(prefs);
   assertRoute(shot);assertEquals("windowed",prefs.getString("animation_mode",null));assertEquals("duo",prefs.getString("animation_style",null));
   assertFalse(prefs.getBoolean("debug_mode",true));assertEquals(AnimationModePolicy.behavior(old),prefs.getString("animation_behavior",null));
   assertFalse(prefs.getBoolean("motion_glass",true));assertTrue(prefs.getBoolean("motion_glass_invert",false));
   assertEquals(1.7f,prefs.getFloat("motion_glass_inner_strength",0),0);assertEquals(130f,prefs.getFloat("motion_glass_smoothing",0),0);
   assertEquals(1.5f,prefs.getFloat("blur_strength",0),0);assertEquals(.4f,prefs.getFloat("intensity",0),0);
  }
 }

}
