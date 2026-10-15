package org.duofold.live;
/** The honest black-gradient fallback drawn when no usable glass frame exists. */
final class GlassFallbackPolicy {
 private GlassFallbackPolicy(){}
 /** Below this glass amount a fallback gradient is invisible; drawing it only churns frames (plan F1). */
 static final float MIN_VISIBLE_AMOUNT=.06f;
 static boolean draws(float amount){return amount>=MIN_VISIBLE_AMOUNT;}
 /** Unified glass caps edge darkening (UnifiedTuning.maxDarken); the fallback must match it (plan B6). */
 static float alpha(float raw,boolean unifiedCap,float maxDarken){return unifiedCap?Math.min(raw,maxDarken):raw;}
}
