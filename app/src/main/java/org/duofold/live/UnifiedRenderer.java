package org.duofold.live;
/**
 * Unified renderer (fork, Phase 7): the glass shader draws the inner panel before the panel switch too,
 * so blur, perspective corners and timing are continuous across the switch. Toggle for on-device comparison
 * with `adb shell setprop debug.duofold.unified 0|1`; default on for the Fold 7.
 */
final class UnifiedRenderer {
 private UnifiedRenderer(){}
 static boolean enabled(){
  String v="";
  try{v=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,"debug.duofold.unified","");}catch(Exception ignored){}
  return decide(v,DeviceCompatibility.isFold7(android.os.Build.MODEL));
 }
 static boolean decide(String property,boolean fold7){
  if("1".equals(property)||"true".equals(property))return true;
  if("0".equals(property)||"false".equals(property))return false;
  return fold7;
 }
}
