package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class SupportPromptPolicyTest {
 @Test public void requiresSuccessfulUseAndFullWeek(){long w=SupportPromptPolicy.WEEK;assertFalse(SupportPromptPolicy.due(w*2,0,0,true,true));assertFalse(SupportPromptPolicy.due(w,1,0,true,true));assertTrue(SupportPromptPolicy.due(w+1,1,0,true,true));}
 @Test public void weeklyCapAndClockRollback(){long w=SupportPromptPolicy.WEEK;assertFalse(SupportPromptPolicy.due(2*w,1,w+1,true,true));assertTrue(SupportPromptPolicy.due(2*w+1,1,w+1,true,true));assertFalse(SupportPromptPolicy.due(w,1,2*w,true,true));}
 @Test public void noPromptWhenOptedOutOrDisconnected(){long w=SupportPromptPolicy.WEEK;assertFalse(SupportPromptPolicy.due(w*2,1,0,false,true));assertFalse(SupportPromptPolicy.due(w*2,1,0,true,false));}
 @Test public void stopsAfterThirdWeeklyInvitation(){long w=SupportPromptPolicy.WEEK;assertTrue(SupportPromptPolicy.cycleAllowed(3*w+1,1,2*w+1,2,0,false,true));assertFalse(SupportPromptPolicy.cycleAllowed(4*w+1,1,3*w+1,3,0,false,true));}
 @Test public void bothChannelsCanShareFinalWeek(){long w=SupportPromptPolicy.WEEK;assertTrue(SupportPromptPolicy.cycleAllowed(3*w+2,1,3*w+1,3,0,false,true));}
 @Test public void snoozeAndRetirementWin(){long w=SupportPromptPolicy.WEEK;assertFalse(SupportPromptPolicy.cycleAllowed(2*w,1,w+1,1,4*w,false,true));assertFalse(SupportPromptPolicy.cycleAllowed(5*w,1,w+1,1,0,true,true));assertTrue(SupportPromptPolicy.cycleAllowed(4*w,1,w+1,1,4*w,false,true));}
}
