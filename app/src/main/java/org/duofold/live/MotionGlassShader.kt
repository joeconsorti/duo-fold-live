package org.duofold.live

/** Inverse geometry warp, shared by both glass shaders. Zero tilt is identity. */
internal object MotionGlassShader {
 val source="""
 float motionAxis(float x,float gain,float span,float pinnedSeam){
  if(pinnedSeam>0.5){
   if(x>=span)return x;
   return span+(x-span)/gain;
  }
  return x/gain;
 }
 float motionLever(float x,float span,float pinnedSeam){
  if(pinnedSeam>0.5)return x<span ? x-span : 0.0;
  return x;
 }
 float2 motionWarp(float2 uv){
  float fade=smoothstep(0.0,0.15,progress);
  float2 tilt=motionOffset*fade;
  if(tilt.x==0.0 && tilt.y==0.0)return uv;
  // The reflected preview is already flipped by its canvas. Undo that flip
  // for the anchor calculation, then restore it for the existing projection.
  float2 screen=uv;
  if(reflectedCover>0.5)screen.x=1.0-screen.x;
  float x=mix(screen.x,screen.y,horizontal);
  x=mix(x,1.0-x,reverse);
  float y=mix(screen.y,screen.x,horizontal);
  float along=mix(tilt.x,tilt.y,horizontal)*(1.0-2.0*reverse);
  float across=mix(tilt.y,tilt.x,horizontal);
  // Inner glass (including the reflected cover on the inner panel) pivots
  // at the clean-content seam; only the actual cover pins its left edge.
  float pinnedSeam=inner>0.5 || reflectedCover>0.5 ? 1.0 : 0.0;
  // Cover faces the opposite way; inner/reflected glass keeps its direction.
  if(pinnedSeam<0.5){along=-along;across=-across;}
  float span=inner>0.5 && fallback<0.5 ? 0.5 : 1.0;
  float gain=exp2(clamp(along,-2.0,2.0));
  float warped=motionAxis(x,gain,span,pinnedSeam);
  y-=0.125*across*motionLever(x,span,pinnedSeam);
  warped=mix(warped,1.0-warped,reverse);
  screen=horizontal>0.5 ? float2(y,warped) : float2(warped,y);
  if(reflectedCover>0.5)screen.x=1.0-screen.x;
  return screen;
 }
 """.trimIndent()
}
