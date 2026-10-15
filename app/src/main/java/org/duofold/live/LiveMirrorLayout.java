package org.duofold.live;
final class LiveMirrorLayout {
 static float[] fit(int sw,int sh,int dw,int dh){
  if(sw<=0||sh<=0||dw<=0||dh<=0)throw new IllegalArgumentException("Invalid mirror dimensions");
  float scale=Math.min(dw/(float)sw,dh/(float)sh);
  return new float[]{scale,dw-sw*scale,(dh-sh*scale)/2f};
 }
 /** Unified mode (plan B3): fill exactly the right half, uniform scale, centered vertically (crops overflow). */
 static float[] rightHalf(int sw,int sh,int dw,int dh){
  if(sw<=0||sh<=0||dw<=0||dh<=0)throw new IllegalArgumentException("Invalid mirror dimensions");
  float half=dw/2f,scale=Math.max(half/sw,dh/(float)sh);
  return new float[]{scale,half+(half-sw*scale)/2f,(dh-sh*scale)/2f};
 }
 // sx, sy, x, y, source crop left/top/right/bottom. Portrait is the original fit.
 static float[] preview(int sw,int sh,int dw,int dh){
  float[] original=fit(sw,sh,dw,dh);
  if(sw<=sh)return new float[]{original[0],original[0],original[1],original[2],0,0,sw,sh};
  throw new IllegalArgumentException("Landscape preview requires source and destination rotations");
 }
 static float[] fill(int sw,int sh,int dw,int dh){
  if(sw<=0||sh<=0||dw<=0||dh<=0)throw new IllegalArgumentException("Invalid mirror dimensions");
  float scale=Math.max(dw/(float)sw,dh/(float)sh);
  return new float[]{scale,(dw-sw*scale)/2f,(dh-sh*scale)/2f};
 }
}
