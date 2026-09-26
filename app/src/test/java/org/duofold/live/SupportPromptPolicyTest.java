package org.duofold.live;
import org.junit.Test;import static org.junit.Assert.*;
public class SupportPromptPolicyTest {
 final long d=SupportPromptPolicy.DAY;
 @Test public void cadenceIsThreeSevenFourteenThenThirty(){assertEquals(3*d,SupportPromptPolicy.interval(0));assertEquals(7*d,SupportPromptPolicy.interval(1));assertEquals(14*d,SupportPromptPolicy.interval(2));assertEquals(30*d,SupportPromptPolicy.interval(3));assertEquals(30*d,SupportPromptPolicy.interval(100));}
 @Test public void eligibleUpgradeGetsOneOverduePrompt(){long first=1,next=SupportPromptPolicy.migratedDue(first,0,0);assertTrue(SupportPromptPolicy.due(4*d,first,next,0,0,false,true));long delivered=4*d;assertFalse(SupportPromptPolicy.due(delivered+1,first,delivered+SupportPromptPolicy.interval(1),delivered,0,false,true));}
 @Test public void migrationPreservesRecentPromptAndCompletedLegacyHistory(){assertEquals(12*d,SupportPromptPolicy.migratedDue(1,5*d,1));assertEquals(23*d,SupportPromptPolicy.migratedDue(1,9*d,2));assertEquals(51*d,SupportPromptPolicy.migratedDue(1,21*d,3));}
 @Test public void noPromptBeforeFirstUseOrWhenDeniedByState(){assertFalse(SupportPromptPolicy.due(50*d,0,0,0,0,false,true));assertFalse(SupportPromptPolicy.due(50*d,1,3*d,0,0,true,true));assertFalse(SupportPromptPolicy.due(50*d,1,3*d,0,0,false,false));assertFalse(SupportPromptPolicy.due(50*d,1,3*d,0,60*d,false,true));}
 @Test public void noClockRollbackOrMissedIntervalBurst(){assertFalse(SupportPromptPolicy.due(5*d,1,3*d,6*d,0,false,true));long now=100*d;long next=now+SupportPromptPolicy.interval(1);assertFalse(SupportPromptPolicy.due(now,1,next,now,0,false,true));assertTrue(SupportPromptPolicy.due(next,1,next,now,0,false,true));}
}
