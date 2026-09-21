package com.mccal.folio;
final class FoldThreshold {
 static final float DEFAULT=172f;
 static float sanitize(float value){return Float.isFinite(value)?Math.max(165f,Math.min(179f,value)):DEFAULT;}
 static boolean canStart(float angle,float threshold){return Float.isFinite(angle)&&angle>=2f&&angle<sanitize(threshold);}
 static boolean endpoint(float angle,float threshold){return Float.isFinite(angle)&&(angle<=1f||angle>=sanitize(threshold));}
}
