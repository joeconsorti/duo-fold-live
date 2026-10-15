package org.duofold.live;
import android.os.SystemClock;
/**
 * Live-tunable unified-renderer parameters (debug.duofold.* properties, shell-writable), cached for 1 s so
 * the per-frame draw path does not reflect into SystemProperties every frame.
 */
final class UnifiedTuning {
 private UnifiedTuning(){}
 private static long readAt;private static float darken=.55f,reflectHinge=55f;
 static float maxDarken(){refresh();return darken;}
 static float reflectMaxHinge(){refresh();return reflectHinge;}
 private static synchronized void refresh(){
  long now=SystemClock.uptimeMillis();if(now-readAt<1000)return;readAt=now;
  darken=clamp(prop("debug.duofold.max_darken",.55f),0f,1f);
  reflectHinge=clamp(prop("debug.duofold.reflect_max_hinge",55f),5f,180f);
 }
 static float clamp(float v,float lo,float hi){return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):lo;}
 private static float prop(String key,float fallback){
  try{String v=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,"");
   return v==null||v.isEmpty()?fallback:Float.parseFloat(v);}catch(Exception e){return fallback;}
 }
}
