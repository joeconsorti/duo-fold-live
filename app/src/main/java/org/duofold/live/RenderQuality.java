package org.duofold.live;
/** Bounded, device-independent rendering settings. */
final class RenderQuality {
 static int fps(int value){return value==120?120:value==12?12:60;}
 static float blur(float value){return Float.isFinite(value)?Math.max(0,Math.min(3,value)):1.5f;}
 static float seam(float value){return Float.isFinite(value)?Math.max(0,Math.min(.06f,value)):.02f;}
 static long delay(int fps,long elapsedNanos){return Math.max(0,(long)Math.ceil((1_000_000_000.0/fps(fps)-Math.max(0,elapsedNanos))/1_000_000.0));}
}
