package org.duofold.live;

/** Reversible inner-left blackout in physical hinge coordinates. */
final class InnerPanelFade {
 static final float BLACK_ANGLE=60f, VISIBLE_ANGLE=85f;
 static float visibility(float angle) {
  if(!Float.isFinite(angle))return 0f;
  float t=Math.max(0f,Math.min(1f,(angle-BLACK_ANGLE)/(VISIBLE_ANGLE-BLACK_ANGLE)));
  return t*t*(3f-2f*t);
 }
 static float[] bounds(int width,int height,int rotation) {
  if(rotation==1)return new float[]{0,height/2f,width,height};
  if(rotation==2)return new float[]{width/2f,0,width,height};
  if(rotation==3)return new float[]{0,0,width,height/2f};
  return new float[]{0,0,width/2f,height};
 }
 // One fifth of the physical right half = one tenth of the complete panel.
 // Start at the hinge, finish inside the right half, regardless of rotation.
 static float[] gradient(int width,int height,int rotation) {
  if(rotation==1)return new float[]{0,height*.5f,0,height*.4f};
  if(rotation==2)return new float[]{width*.5f,0,width*.4f,0};
  if(rotation==3)return new float[]{0,height*.5f,0,height*.6f};
  return new float[]{width*.5f,0,width*.6f,0};
 }
 static float[] featherBounds(int width,int height,int rotation) {
  if(rotation==1)return new float[]{0,height*.4f,width,height*.5f};
  if(rotation==2)return new float[]{width*.4f,0,width*.5f,height};
  if(rotation==3)return new float[]{0,height*.5f,width,height*.6f};
  return new float[]{width*.5f,0,width*.6f,height};
 }
}
