package org.duofold.live;
import android.os.SystemClock;
/**
 * One source for the preview/right-half blur tuning properties (plan B10), cached for 1 s like UnifiedTuning.
 * Before, the mirror read them per attach, the hold per prepare and the right half once per fade session, so a
 * `setprop` mid-session gave different maxima on the two sides of the switch.
 * Shell-writable: debug.duofold.preview_blur_start|max|smooth_ms, debug.duofold.right_blur_clear.
 */
final class BlurTuning {
 private BlurTuning(){}
 static final float DEFAULT_MAX=56,DEFAULT_START=0,DEFAULT_SMOOTH_MS=40;
 private static long readAt=-1;
 private static float max=DEFAULT_MAX,start=DEFAULT_START,smoothMs=DEFAULT_SMOOTH_MS,rightClear=-1;
 /** Maximum preview / hold / right-half blur radius, px. */
 static float max(){refresh();return max;}
 /** Angle where the opening preview blur starts. */
 static float start(){refresh();return start;}
 /** Glide time constant for blur radius changes, ms. */
 static float smoothMs(){refresh();return smoothMs;}
 /** Angle where the right half is sharp again after the switch; -1 = the open threshold. */
 static float rightClear(){refresh();return rightClear;}
 static float sanitizeMax(float v){return Float.isFinite(v)?Math.max(0,Math.min(PreviewBlurPolicy.MAX_RADIUS,v)):DEFAULT_MAX;}
 static float sanitizeAngle(float v,float fallback){return Float.isFinite(v)&&v>=0&&v<=180?v:fallback;}
 static float sanitizeSmooth(float v){return Float.isFinite(v)&&v>=0&&v<=1000?v:DEFAULT_SMOOTH_MS;}
 private static synchronized void refresh(){
  long now=SystemClock.uptimeMillis();if(readAt>=0&&now-readAt<1000)return;readAt=now;
  max=sanitizeMax(prop("debug.duofold.preview_blur_max",DEFAULT_MAX));
  start=sanitizeAngle(prop("debug.duofold.preview_blur_start",DEFAULT_START),DEFAULT_START);
  smoothMs=sanitizeSmooth(prop("debug.duofold.preview_blur_smooth_ms",DEFAULT_SMOOTH_MS));
  rightClear=sanitizeAngle(prop("debug.duofold.right_blur_clear",-1),-1);
 }
 private static float prop(String key,float fallback){
  try{String v=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,"");
   return v==null||v.isEmpty()?fallback:Float.parseFloat(v);}catch(Exception e){return fallback;}
 }
}
