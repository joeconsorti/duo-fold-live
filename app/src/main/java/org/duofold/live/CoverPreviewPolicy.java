package org.duofold.live;
/** Preview follows the existing cover request; it never controls display angles. */
final class CoverPreviewPolicy {
 static boolean allowed(boolean enabled,boolean screenshotHandoff,boolean primaryInner,boolean unlocked,boolean coverRequestActive){
  return enabled && !screenshotHandoff && !primaryInner && unlocked && coverRequestActive;
 }
}
