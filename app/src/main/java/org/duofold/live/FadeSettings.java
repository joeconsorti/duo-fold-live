package org.duofold.live;
/** Persisted fade controls; zero gradualness preserves the original sharpest fade. */
final class FadeSettings {
 static final float DEFAULT_SMOOTHING=24f,DEFAULT_GRADUALNESS=.35f;
 static float smoothing(float v){return Float.isFinite(v)?Math.max(0,Math.min(120,v)):DEFAULT_SMOOTHING;}
 static float gradualness(float v){return Float.isFinite(v)?Math.max(0,Math.min(1,v)):DEFAULT_GRADUALNESS;}
 static float span(float v){return 10+30*gradualness(v);}
 static long reveal(float v){return Math.round(180+320*gradualness(v));}
}
