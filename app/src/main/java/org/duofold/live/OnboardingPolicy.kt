package org.duofold.live
/** 0 unseen; 1 started; 2 complete; 3 preserved upgrade. No wallpaper writes here. */
object OnboardingPolicy {
 fun needsSetup(marker:Int, legacyData:Boolean, packageWasUpdated:Boolean):Boolean =
  when(marker) { 1 -> true; 2,3 -> false; else -> !legacyData && !packageWasUpdated }
}
