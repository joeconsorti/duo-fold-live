package org.duofold.live;
/** Bounded, device-independent rendering settings. */
final class RenderQuality {
 static int fps(int value){return value==60?60:value==12?12:120;}
 static int effectiveFps(int requested,float refreshHz){return Math.min(fps(requested),Float.isFinite(refreshHz)&&refreshHz>0?Math.max(1,Math.round(refreshHz)):60);}
 static float blur(float value){return Float.isFinite(value)?Math.max(0,Math.min(3,value)):.3f;}
 static float seam(float value){return Float.isFinite(value)?Math.max(0,Math.min(.15f,value)):.07f;}
 // The location moves the end of the blur; the feather stays 5% of panel width.
 static float seamBlend(float distance,float offset){float end=seam(offset);if(end<=0)return 0;float t=Math.max(0,Math.min(1,(end-distance)/Math.min(.05f,end)));return t*t*(3-2*t);}
 static float motion(float angle,float open){if(!Float.isFinite(angle))return 0;float t=Math.max(0,Math.min(1,(open-angle)/Math.max(1,open-90)));return t*t*(3-2*t);}
 static long delay(int fps,long elapsedNanos){return Math.max(0,(long)Math.ceil((1_000_000_000.0/Math.max(1,Math.min(120,fps))-Math.max(0,elapsedNanos))/1_000_000.0));}
}
