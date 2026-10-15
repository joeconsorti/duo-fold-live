package org.duofold.live;
/**
 * Unified renderer, inner left strip before the switch (plan B2). The strip draws the mirrored cover with the cover
 * leaf model; its hinge is capped (UnifiedTuning.reflectMaxHinge, 55 deg) because the leaf folds edge-on near 90 deg.
 * Holding the cap froze perspective, blur and darkening for the last ~46 deg and then jumped to the inner model at
 * the reveal. Instead the pose eases from the cap to the hinge whose shader progress (hinge/90) equals the inner
 * glass's progress at the switch angle, and the blur factor ramps to the inner model's x1.25 — so progress, motion,
 * blur radius and darkening are continuous at the switch while the leaf never reaches edge-on.
 */
final class StripPose {
 private StripPose(){}
 static float hinge(float hinge,float cap,float switchAngle,float openThreshold){
  if(!Float.isFinite(hinge))return cap;
  if(hinge<=cap)return hinge;
  if(!(switchAngle>cap))return cap;
  float target=90f*Math.max(0,Math.min(1,(openThreshold-switchAngle)/(openThreshold-90f)));
  if(target<=cap)return cap;
  return cap+(target-cap)*ease(hinge,cap,switchAngle);
 }
 static float blurFactor(float hinge,float cap,float switchAngle){
  if(!Float.isFinite(hinge)||!(switchAngle>cap))return 1f;
  return 1f+.25f*ease(hinge,cap,switchAngle);
 }
 private static float ease(float hinge,float from,float to){
  float t=Math.max(0,Math.min(1,(hinge-from)/(to-from)));
  return t*t*(3-2*t);
 }
}
