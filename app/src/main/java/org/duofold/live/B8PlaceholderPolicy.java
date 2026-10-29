package org.duofold.live;
/** Fill only cross-panel placeholders. Native screenshots keep their existing fit policy. */
final class B8PlaceholderPolicy {
 static float scale(int sourceWidth,int sourceHeight,int width,int height,boolean placeholder){
  float x=(float)width/sourceWidth,y=(float)height/sourceHeight;
  return placeholder?Math.max(x,y):Math.min(x,y);
 }
}
