package org.duofold.live;
/** Bounded, device-independent rendering settings. */
final class RenderQuality {
 static final float DEFAULT_BLUR=.75f;
 static final float DEFAULT_GLASS=.5f;
 static final int DEFAULT_RESOLUTION=100;
 static int resolution(int value){return value==25||value==50||value==75||value==100?value:DEFAULT_RESOLUTION;}
 static int bufferDimension(int logical,int percent,boolean aa,int method){
  // Percent is per dimension. Existing 4x AA doubles each selected dimension.
  long pixels=(long)Math.max(1,logical)*resolution(percent)*(aa&&aaMode(method)==2?2:1)/100;
  return (int)Math.max(1,Math.min(Integer.MAX_VALUE,pixels));
 }
 static int fps(int value){return value==60?60:value==12?12:120;}
 static int aaMode(int value){return value>=0&&value<=2?value:0;}
 static float antialias(float value){return Float.isFinite(value)?Math.max(.2f,Math.min(.5f,value)):.35f;}
 static float blur(float value){return Float.isFinite(value)?Math.max(0,Math.min(3,value)):DEFAULT_BLUR;}
 static float seam(float value){return Float.isFinite(value)?Math.max(0,Math.min(.15f,value)):.07f;}
 // The location moves the end of the blur; the feather stays 5% of panel width.
 static float seamBlend(float distance,float offset){float end=seam(offset);if(end<=0)return 0;float t=Math.max(0,Math.min(1,(end-distance)/Math.min(.05f,end)));return t*t*(3-2*t);}
 static float motion(float angle,float open){if(!Float.isFinite(angle))return 0;float t=Math.max(0,Math.min(1,(open-angle)/Math.max(1,open-90)));return t*t*(3-2*t);}
 static long delay(int fps,long elapsedNanos){return Math.max(0,(long)Math.ceil((1_000_000_000.0/Math.max(1,Math.min(120,fps))-Math.max(0,elapsedNanos))/1_000_000.0));}
}
