package org.duofold.live;
/**
 * Angle-driven blur for the live cover preview on the inner panel while opening. The preview is a
 * full-resolution compositor mirror, so without this it stays sharp until the switch and then jumps
 * to the blurred glass. Ramping the blur with the hinge makes the right half arrive at the glass look.
 */
final class PreviewBlurPolicy {
 static final float MAX_RADIUS=150;
 private PreviewBlurPolicy(){}
 /** Smoothstep ramp from 0 at {@code start} to {@code max} at {@code end} (the panel-switch angle). */
 static float radius(float angle,float start,float end,float max){
  if(!Float.isFinite(angle)||!Float.isFinite(start)||!Float.isFinite(end)||!Float.isFinite(max)||max<=0)return 0;
  float limit=Math.min(max,MAX_RADIUS);
  if(end<=start)return angle>=end?limit:0;
  float t=Math.max(0,Math.min(1,(angle-start)/(end-start)));
  return limit*t*t*(3-2*t);
 }
 /**
  * Frame-rate glide toward the latest hinge sample. Samples arrive every 30-130 ms in 2-3 degree
  * jumps while opening; gliding at the display rate turns those jumps into continuous motion.
  * Exponential approach with time constant {@code tauMs}: never overshoots, 0 snaps to the target.
  */
 static float glide(float current,float target,float dtMs,float tauMs){
  if(!Float.isFinite(target))return current;
  if(!Float.isFinite(current)||tauMs<=0)return target;
  if(dtMs<=0)return current;
  return current+(target-current)*(float)(1-Math.exp(-dtMs/tauMs));
 }
 /** Compositor transactions are not free; resubmit only for a visible (>= 1 px) change or to reach exactly 0. */
 static boolean changed(float previous,float next){
  return previous<0||Math.abs(next-previous)>=1||(next==0&&previous!=0);
 }
}
