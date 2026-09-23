package org.duofold.live
/** 0 unseen; 1 started; 2 complete; 3 preserved upgrade. No wallpaper writes here. */
object OnboardingPolicy {
 // Application startup writes these before MainActivity; they are not user setup.
 private val automaticKeys=setOf("auto_keep_cover_awake","cover_preview","dual","defaults_170_applied","animation_mode","preview_default_203")
 fun hasLegacySettings(keys:Set<String>):Boolean = keys.any { it !in automaticKeys }
 fun needsRecovery(marker:Int, wallpaperVerified:Boolean):Boolean = marker==3 && !wallpaperVerified
 fun needsSetup(marker:Int, legacyData:Boolean, packageWasUpdated:Boolean):Boolean =
  when(marker) { 1 -> true; 2,3 -> false; else -> !legacyData && !packageWasUpdated }
}
