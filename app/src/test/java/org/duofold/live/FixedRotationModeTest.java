package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class FixedRotationModeTest {
 @Test public void restoresAllFourSamsungPolicies(){assertEquals(0,FixedRotationMode.parse("default"));assertEquals(1,FixedRotationMode.parse("disabled"));assertEquals(2,FixedRotationMode.parse("enabled"));assertEquals(3,FixedRotationMode.parse("enabled_if_no_auto_rotation"));}
 @Test(expected=IllegalArgumentException.class) public void neverGuessesAfterShellFailure(){FixedRotationMode.parse("Permission denied");}
}
