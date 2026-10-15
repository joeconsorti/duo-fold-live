package org.duofold.live;
/**
 * Fold 7 unified renderer: compositor blur over the inner panel's right half while the inner panel is primary.
 * Before the switch the right half is the live cover mirror, blurred up to the mirror's maximum at the switch
 * angle (InnerLiveMirror); after it the glass shader leaves the right half transparent, exposing the sharp
 * native screen. This continues the same blur from the switch angle down to 0 at fully open, where the left
 * glass also clears, so the right half stays a flat full pane whose blur never jumps across the switch.
 */
final class RightHalfBlur {
 private RightHalfBlur(){}
 /** Max radius at or below {@code switchAngle}, easing to 0 at {@code clearAngle} on the left glass's curve. */
 static float radius(float angle,float switchAngle,float clearAngle,float max){
  if(!Float.isFinite(angle)||!Float.isFinite(max)||max<=0||!(clearAngle>switchAngle))return 0;
  float limit=Math.min(max,PreviewBlurPolicy.MAX_RADIUS);
  // Same easing as the left glass (inner progress spans clear -> 90 deg, DuoShadeCurve), normalized so the
  // value at the switch angle is exactly the mirror's maximum and clamped to it between 94 and 101 deg.
  float atSwitch=ease(clearAngle,switchAngle);
  return atSwitch<=0?0:Math.min(limit,limit*ease(clearAngle,angle)/atSwitch);
 }
 static final float LEFT_SPAN_END=90;
 private static float ease(float clearAngle,float angle){
  float t=Math.max(0,Math.min(1,(clearAngle-angle)/(clearAngle-LEFT_SPAN_END)));
  return t*t*(3-2*t);
 }
}
