package com.mccal.folio;
final class LiveMirrorLayout {
 static float[] fit(int sw,int sh,int dw,int dh){
  if(sw<=0||sh<=0||dw<=0||dh<=0)throw new IllegalArgumentException("Invalid mirror dimensions");
  float scale=Math.min(dw/(float)sw,dh/(float)sh);
  return new float[]{scale,dw-sw*scale,(dh-sh*scale)/2f};
 }
 static float[] fill(int sw,int sh,int dw,int dh){
  if(sw<=0||sh<=0||dw<=0||dh<=0)throw new IllegalArgumentException("Invalid mirror dimensions");
  float scale=Math.max(dw/(float)sw,dh/(float)sh);
  return new float[]{scale,(dw-sw*scale)/2f,(dh-sh*scale)/2f};
 }
}
