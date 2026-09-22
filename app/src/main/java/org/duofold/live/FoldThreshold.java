package org.duofold.live;
final class FoldThreshold {
 static final float DEFAULT=172f;
 static float sanitizeClosed(float value){return Float.isFinite(value)?Math.max(1f,Math.min(10f,value)):2f;}
 static float effectiveAngle(float angle,float closed){return Float.isFinite(angle)&&angle<=sanitizeClosed(closed)?0f:angle;}
 static float sanitize(float value){return Float.isFinite(value)?Math.max(165f,Math.min(179f,value)):DEFAULT;}
 static boolean canStart(float angle,float threshold){return Float.isFinite(angle)&&angle>=2f&&angle<sanitize(threshold);}
 static boolean endpoint(float angle,float threshold){return Float.isFinite(angle)&&(angle<=1f||angle>=sanitize(threshold));}
}
