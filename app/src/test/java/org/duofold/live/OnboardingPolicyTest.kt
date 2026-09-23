package org.duofold.live
import org.junit.Assert.*
import org.junit.Test
class OnboardingPolicyTest {
 @Test fun startupDefaultsDoNotSkipFreshSetup(){
  val keys=setOf("auto_keep_cover_awake","cover_preview","dual","defaults_170_applied","animation_mode","preview_default_203")
  assertFalse(OnboardingPolicy.hasLegacySettings(keys))
  assertTrue(OnboardingPolicy.needsSetup(0,OnboardingPolicy.hasLegacySettings(keys),false))
 }
 @Test fun actualUserSettingsRemainLegacy(){assertTrue(OnboardingPolicy.hasLegacySettings(setOf("enabled","auto_keep_cover_awake")))}
 @Test fun skippedUnverifiedInstallMustRecover(){assertTrue(OnboardingPolicy.needsRecovery(3,false))}
 @Test fun verifiedUpgradeDoesNotRepeat(){assertFalse(OnboardingPolicy.needsRecovery(3,true))}
 @Test fun completedWizardDoesNotRepeat(){assertFalse(OnboardingPolicy.needsRecovery(2,true))}
 @Test fun interruptedRecoveryUsesNormalResume(){assertFalse(OnboardingPolicy.needsRecovery(1,false));assertTrue(OnboardingPolicy.needsSetup(1,true,true))}
 @Test fun freshInstallMustSetUp(){assertTrue(OnboardingPolicy.needsSetup(0,false,false))}
 @Test fun updatingExistingPackageSkipsEvenWithoutPreferences(){assertFalse(OnboardingPolicy.needsSetup(0,false,true))}
 @Test fun legacySettingsOrPhotoSkip(){assertFalse(OnboardingPolicy.needsSetup(0,true,false))}
 @Test fun incompleteSetupResumesDespitePhotoAndUpdate(){assertTrue(OnboardingPolicy.needsSetup(1,true,true))}
 @Test fun completedSetupNeverRepeats(){assertFalse(OnboardingPolicy.needsSetup(2,false,false))}
 @Test fun preservedUpgradeNeverRepeats(){assertFalse(OnboardingPolicy.needsSetup(3,false,false))}
}
