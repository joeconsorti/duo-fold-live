package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class PollCadenceTest {
 @Test public void fastModeYieldsAfterEachCompletedPoll(){assertEquals(1,PollCadence.delay(true,4,false));assertEquals(4,PollCadence.delay(true,0,false));assertEquals(2,PollCadence.delay(true,2,false));}
 @Test public void slowCallsNeverCreateCatchupBursts(){assertEquals(1,PollCadence.delay(true,100,false));}
 @Test public void capturedFrameCanTriggerImmediateHandoff(){assertEquals(0,PollCadence.delay(true,4,true));}
 @Test public void screenOffUsesSlowCadence(){assertEquals(496,PollCadence.delay(false,4,true));}
}
