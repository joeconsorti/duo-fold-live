package org.duofold.live
import org.junit.Assert.*
import org.junit.Test
class OnboardingPolicyTest {
 @Test fun freshInstallMustSetUp(){assertTrue(OnboardingPolicy.needsSetup(0,false,false))}
 @Test fun updatingExistingPackageSkipsEvenWithoutPreferences(){assertFalse(OnboardingPolicy.needsSetup(0,false,true))}
 @Test fun legacySettingsOrPhotoSkip(){assertFalse(OnboardingPolicy.needsSetup(0,true,false))}
 @Test fun incompleteSetupResumesDespitePhotoAndUpdate(){assertTrue(OnboardingPolicy.needsSetup(1,true,true))}
 @Test fun completedSetupNeverRepeats(){assertFalse(OnboardingPolicy.needsSetup(2,false,false))}
 @Test fun preservedUpgradeNeverRepeats(){assertFalse(OnboardingPolicy.needsSetup(3,false,false))}
}
