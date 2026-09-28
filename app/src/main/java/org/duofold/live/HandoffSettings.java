package org.duofold.live;
/** User-facing opening angle; preserve the original four-degree return gap. */
final class HandoffSettings {
 static final float DEFAULT=85f,MIN=75f,MAX=115f;
 static float angle(float value){return Float.isFinite(value)?Math.max(MIN,Math.min(MAX,value)):DEFAULT;}
 static float closing(float value){return angle(value)-4f;}
}
