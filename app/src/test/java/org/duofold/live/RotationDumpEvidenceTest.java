package org.duofold.live;
import org.junit.Test;import static org.junit.Assert.*;
public class RotationDumpEvidenceTest {
 @Test public void retainsDecisionsButNotWindowTitles(){String s=RotationDumpEvidence.filter("Display: mDisplayId=0\n mRotation=ROTATION_270 mUserRotationMode=USER_ROTATION_FREE\n mProposedRotation=1 mFixedToUserRotation=2\n mCurrentFocus=Window{private user document}\n");assertTrue(s.contains("mProposedRotation=1"));assertTrue(s.contains("mUserRotationMode=USER_ROTATION_FREE"));assertFalse(s.contains("private"));}
 @Test public void missingFieldsAreNotHealthy(){assertTrue(RotationDumpEvidence.filter("vendor changed format").startsWith("UNAVAILABLE"));}
 @Test public void boundedEvenForManyDisplays(){StringBuilder s=new StringBuilder();for(int i=0;i<10000;i++)s.append("mRotation=1\n");assertTrue(RotationDumpEvidence.filter(s.toString()).length()<2400);}
 @Test public void rejectsEmbeddedAndPartialNames(){assertFalse(RotationDumpEvidence.filter("window-mRotation=privateData mRotation=ROTATION_90private").contains("ROTATION_90"));}
}
