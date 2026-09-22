package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class SupportPromptPolicyTest {
 @Test public void requiresSuccessfulUseAndFullDay(){assertFalse(SupportPromptPolicy.due(200000000,0,0,true,true));assertFalse(SupportPromptPolicy.due(86400000,1,0,true,true));assertTrue(SupportPromptPolicy.due(86400001,1,0,true,true));}
 @Test public void dailyCapSurvivesRepeatedChecksAndClockRollback(){long d=SupportPromptPolicy.DAY;assertFalse(SupportPromptPolicy.due(2*d,1,d+1,true,true));assertTrue(SupportPromptPolicy.due(2*d+1,1,d+1,true,true));assertFalse(SupportPromptPolicy.due(d,1,2*d,true,true));}
 @Test public void noPromptWhenOptedOutOrDisconnected(){assertFalse(SupportPromptPolicy.due(200000000,1,0,false,true));assertFalse(SupportPromptPolicy.due(200000000,1,0,true,false));}
}
