package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class ScreenshotStartupPolicyTest {
 @Test public void captureMustMatchCommittedFrame(){assertFalse(ScreenshotStartupPolicy.presented(0,0));assertFalse(ScreenshotStartupPolicy.presented(123,122));assertTrue(ScreenshotStartupPolicy.presented(123,123));}
 @Test public void openingKeepsCoverPrimary(){assertFalse(ScreenshotStartupPolicy.keepInnerPrimary(0));}
 @Test public void closingKeepsInnerPrimary(){assertTrue(ScreenshotStartupPolicy.keepInnerPrimary(1));}
 @Test public void attachmentIsRequiredBeforeRequest(){assertFalse(ScreenshotStartupPolicy.canBegin(false,0,false));assertTrue(ScreenshotStartupPolicy.canBegin(false,0,true));}
 @Test public void staleDirectionCannotStart(){assertFalse(ScreenshotStartupPolicy.canBegin(true,0,true));assertFalse(ScreenshotStartupPolicy.canBegin(false,1,true));assertFalse(ScreenshotStartupPolicy.canBegin(false,-1,true));}
 @Test(expected=IllegalArgumentException.class) public void missingCaptureHasNoDefaultPrimary(){ScreenshotStartupPolicy.keepInnerPrimary(-1);}
}
