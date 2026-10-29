package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class HingePausePolicyTest {
 private HingePausePolicy p=new HingePausePolicy();
 private void sample(long at,float angle,int panel){p.update(true,at,at,angle,172,panel,2);}
 private void hold(long from,float angle,int panel){for(long t=from;t<=from+1250;t+=50)sample(t,angle,panel);}
 @Test public void pauseChoosesByAngleRegardlessOfStartingPanelOrMode(){
  for(int mode:new int[]{0,1,2})for(int origin:new int[]{1,2})for(float angle:new float[]{40,89,91,125}){
   p.reset();p.update(true,1000,1000,origin==1?180:0,172,origin,mode);
   for(long t=1050;t<=2300;t+=50)p.update(true,t,t,angle,172,origin,mode);
   assertTrue(p.held());assertEquals(angle<90?2:1,p.sourcePrimary());
   assertFalse(p.settled(2579));assertTrue(p.settled(2580));
  }
 }
 @Test public void exactlyNinetyKeepsActivePanelAtConfirmation(){
  for(int active:new int[]{1,2}){
   p.reset();sample(1000,active==1?0:180,3-active);hold(1050,90,active);
   assertTrue(p.held());assertEquals(active,p.sourcePrimary());
  }
 }
 @Test public void selectionUsesConfirmationAngleThenLatchesAcrossBoundaryJitter(){
  for(int direction:new int[]{-1,1}){
   p.reset();sample(1000,90-direction,1);
   for(int t=1050;t<=2250;t+=50)sample(t,90+direction,1);
   assertTrue(p.held());int selected=direction<0?2:1;assertEquals(selected,p.sourcePrimary());
   for(int t=2300;t<=4000;t+=50)sample(t,90+(t%100==0?direction:-direction),3-selected);
   assertTrue(p.held());assertEquals(selected,p.sourcePrimary());
  }
 }
 @Test public void doesNotSettleBeforeFull1250Milliseconds(){
  for(int t=1000;t<2250;t+=50)sample(t,80,2);
  sample(2249,80,2);assertFalse(p.held());sample(2250,80,2);assertTrue(p.held());
 }
 @Test public void cumulativeMovementResetsTimerInBothDirections(){
  for(int direction:new int[]{-1,1}){
   p.reset();for(int t=1000;t<5000;t+=100)sample(t,80+direction*(t-1000)/100f,1);assertFalse(p.held());
  }
 }
 @Test public void jitterUpToFiveDegreesDoesNotRestartObservation(){
  for(int mode:new int[]{0,1,2})for(int direction:new int[]{-1,1}){
   p.reset();p.update(true,1000,1000,80,172,2,mode);
   for(int t=1050;t<=2250;t+=50)p.update(true,t,t,80+direction*((t/50)%6),172,2,mode);
   assertTrue(p.held());assertEquals(2250,p.startedAt());
  }
 }
 @Test public void moreThanFiveDegreesRestartsFullObservation(){
  for(int direction:new int[]{-1,1}){
   p.reset();for(int t=1000;t<=2200;t+=50)sample(t,80,2);
   sample(2250,80+direction*5.01f,2);assertFalse(p.held());
   for(int t=2300;t<3500;t+=50)sample(t,80+direction*5.01f,2);
   assertFalse(p.held());sample(3500,80+direction*5.01f,2);assertTrue(p.held());
  }
 }
 @Test public void fiveDegreesRearmsInEitherDirectionAndReevaluatesPanel(){
  for(int mode:new int[]{0,1,2})for(int direction:new int[]{-1,1}){
   p.reset();float angle=direction<0?91:89;
   for(long t=1000;t<=2250;t+=50)p.update(true,t,t,angle,172,2,mode);
   long first=p.startedAt();int selected=p.sourcePrimary();
   for(int n=1;n<=4;n++)p.update(true,2250+n*50,2250+n*50,angle+direction*n,172,3-selected,mode);
   p.update(true,2500,2500,angle+direction*4.99f,172,3-selected,mode);
   assertTrue(p.held());assertEquals(first,p.startedAt());assertEquals(selected,p.sourcePrimary());
   p.update(true,2550,2550,angle+direction*5,172,selected,mode);assertFalse(p.held());
   for(int t=2600;t<=3800;t+=50)p.update(true,t,t,angle+direction*5,172,selected,mode);
   assertTrue(p.startedAt()>first);assertEquals(3-selected,p.sourcePrimary());
  }
 }
 @Test public void heldAnchorDoesNotDriftWithSmallSuccessiveMoves(){
  hold(1000,60,2);for(int n=1;n<5;n++){sample(2250+n*50,60+n,2);assertTrue(p.held());}
  sample(2500,65,2);assertFalse(p.held());
 }
 @Test public void repeatedRepliesCannotManufactureStationarySensorTime(){
  sample(1000,60,2);for(int now=1050;now<1700;now+=50)p.update(true,now,1000,60,172,2,2);
  assertFalse(p.held());sample(1700,60,2);assertFalse(p.held());
 }
 @Test public void droppedSamplesRestart1250MillisecondObservation(){
  for(int t=1000;t<=1600;t+=50)sample(t,60,2);sample(1950,60,2);
  for(int t=2000;t<3200;t+=50)sample(t,60,2);assertFalse(p.held());sample(3200,60,2);assertTrue(p.held());
 }
 @Test public void endpointsClearHoldAndFastLegsRemainEligible(){
  hold(1000,60,2);sample(2300,180,1);assertFalse(p.held());sample(2350,10,1);assertFalse(p.held());
  sample(2400,0,2);assertFalse(p.held());sample(2450,150,2);assertFalse(p.held());
 }
 @Test public void disableLockStaleAndModeSwitchResetState(){
  hold(1000,60,2);p.update(false,2300,2300,60,172,2,2);assertFalse(p.held());
  hold(2400,60,2);p.update(true,4500,3650,60,172,2,2);assertFalse(p.held());
  hold(4600,60,2);p.update(true,5900,5900,60,172,1,0);assertFalse(p.held());assertEquals(1,p.sourcePrimary());
 }
 @Test public void invalidAndFutureSamplesDoNotTriggerGesture(){
  for(float angle:new float[]{Float.NaN,Float.POSITIVE_INFINITY}){p.reset();hold(1000,60,2);sample(2300,angle,2);assertFalse(p.held());}
  p.update(true,2400,2500,60,172,2,2);assertFalse(p.held());
 }
 @Test public void easingHasExactEndpointsAndMonotonicIntermediatePoses(){
  assertEquals(0,HingePausePolicy.fraction(0,2000,280),0);
  assertEquals(0,HingePausePolicy.fraction(1000,1000,280),0);
  assertEquals(.5f,HingePausePolicy.fraction(1000,1140,280),.0001f);
  float last=0;for(int now=1000;now<=1280;now+=10){float f=HingePausePolicy.fraction(1000,now,280);assertTrue(f>=last);last=f;}
  assertEquals(1,last,0);assertEquals(0,HingePausePolicy.pose(60,false,172,last),0);assertEquals(172,HingePausePolicy.pose(120,true,172,last),0);
 }
}
