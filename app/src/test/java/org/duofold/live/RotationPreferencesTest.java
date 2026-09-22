package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class RotationPreferencesTest {
 @Test public void preservesDifferentFoldedAndUnfoldedPreferences(){assertEquals(Integer.valueOf(1),RotationPreferences.parse("0:1:1:0:2:2").get(0));assertEquals(Integer.valueOf(2),RotationPreferences.parse("0:1:1:0:2:2").get(2));assertEquals(Integer.valueOf(0),RotationPreferences.parse("0:1:1:0:2:2").get(1));}
 @Test public void comparesMapsWithoutDependingOnSerializationOrder(){assertEquals(RotationPreferences.parse("0:1:2:2"),RotationPreferences.parse("2:2:0:1"));}
 @Test(expected=IllegalArgumentException.class) public void refusesMissingPreferences(){RotationPreferences.parse(null);}
 @Test(expected=IllegalArgumentException.class) public void refusesUnknownMode(){RotationPreferences.parse("0:7");}
 @Test(expected=IllegalArgumentException.class) public void refusesTruncation(){RotationPreferences.parse("0:1:2");}
 @Test(expected=IllegalArgumentException.class) public void refusesDuplicates(){RotationPreferences.parse("0:1:0:2");}
}
