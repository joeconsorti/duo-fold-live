package org.duofold.live;

/** Choose the app texture resolution from the active panel raster, including supersampling. */
final class AppCaptureRaster {
 private static final int MAX_SIDE=1440;
 static int side(int logicalSide,int percent){
  int ceiling=Math.min(MAX_SIDE,logicalSide);
  if(percent<=0 || percent>=100)return ceiling;
  return Math.min(ceiling,Math.max(320,(int)((long)logicalSide*percent/100)));
 }
 static float blurScale(int logicalSide,int captureSide){
  return captureSide/(float)Math.min(MAX_SIDE,logicalSide);
 }
}
