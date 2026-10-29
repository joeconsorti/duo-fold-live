package org.duofold.live.wallpaperlayer;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class WakeEligibilityPolicyTest {
 @Test public void emptyTeardownKeepsRecentHomeButDoesNotRefreshItsAge(){Map<Integer,Long> m=new HashMap<>();m.put(0,100L);WakeEligibilityPolicy.remember(m,Set.of(),Set.of(),1000);assertEquals(Long.valueOf(100),m.get(0));assertTrue(WakeEligibilityPolicy.sleeping(m,Set.of(),1000).contains(0));assertTrue(WakeEligibilityPolicy.sleeping(m,Set.of(),1600).isEmpty());}
 @Test public void knownOtherAppClearsHome(){Map<Integer,Long> m=new HashMap<>();m.put(0,100L);WakeEligibilityPolicy.remember(m,Set.of(),Set.of(0),110);assertTrue(WakeEligibilityPolicy.sleeping(m,Set.of(),120).isEmpty());}
 @Test public void interruptedHomeWakeSurvivesSecondSleep(){assertEquals(Set.of(0),WakeEligibilityPolicy.sleeping(Map.of(),Set.of(0),220222499));}
 @Test public void noHomeEvidenceNeverArmsAnAppWake(){assertTrue(WakeEligibilityPolicy.sleeping(Map.of(),Set.of(),100).isEmpty());assertTrue(WakeEligibilityPolicy.sleeping(Map.of(0,101L),Set.of(),100).isEmpty());}
}
