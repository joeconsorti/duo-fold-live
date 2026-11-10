package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class PollCadenceTest {
 @Test public void fastModeYieldsAfterEachCompletedPoll(){assertEquals(1,PollCadence.delay(true,4,false));assertEquals(4,PollCadence.delay(true,0,false));assertEquals(2,PollCadence.delay(true,2,false));}
 @Test public void slowCallsNeverCreateCatchupBursts(){assertEquals(1,PollCadence.delay(true,100,false));}
 @Test public void capturedFrameCanTriggerImmediateHandoff(){assertEquals(0,PollCadence.delay(true,4,true));}
 @Test public void screenOffUsesSlowCadence(){assertEquals(496,PollCadence.delay(false,4,true));}
 @Test public void screenOnEndpointsKeepMotionDiscoveryWithin32ms(){
  PollCadence.Endpoint e=new PollCadence.Endpoint();
  assertEquals(32,e.period(0,179,172,true,false));
  assertEquals(32,e.period(749,178,172,true,false));
  assertEquals(32,e.period(750,180,172,true,false));
  for(int t=800;t<2000;t+=100)assertEquals(32,e.period(t,t%200==0?178:179,172,true,false));
  assertEquals(16,e.period(2000,171,172,true,false));
  assertEquals(16,e.period(2100,180,172,true,true));
  assertEquals(32,e.period(2200,180,172,true,false));
  assertEquals(32,e.period(2950,180,172,true,false));
  assertEquals(16,e.period(3000,180,172,false,false));
  assertEquals(32,e.period(3100,0,172,true,false));
  assertEquals(32,e.period(3850,1,172,true,false));
  assertEquals(16,e.period(3900,4,172,true,false));
 }
}
