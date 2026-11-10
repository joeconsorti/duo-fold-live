package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class HingePausePolicyTest {
 private final HingePausePolicy p=new HingePausePolicy();
 private void sample(long at,float angle,int panel){p.update(true,at,at,angle,172,panel,2);}
 private void hold(float angle,int panel){for(long t=1000;t<=2500;t+=50)sample(t,angle,panel);assertTrue(p.held());}
 @Test public void full1500MillisecondsRequired(){for(int t=1000;t<2500;t+=50)sample(t,120,1);sample(2499,120,1);assertFalse(p.held());sample(2500,120,1);assertTrue(p.held());assertFalse(p.settled(2779));assertTrue(p.settled(2780));}
 @Test public void selectionUsesConfirmationAngleAcrossAllRoutes(){
  for(int mode=0;mode<=2;mode++)for(int origin=1;origin<=2;origin++)for(float a:new float[]{40,89,91,125}){
   p.reset();for(long t=1000;t<=2500;t+=50)p.update(true,t,t,a,172,origin,mode);
   assertTrue(p.held());assertEquals(a<90?2:1,p.sourcePrimary());
  }
 }
 @Test public void earlierEndpointCannotVetoLaterStationaryScreen(){
  for(boolean opening:new boolean[]{false,true}){p.reset();int origin=opening?2:1;sample(1000,opening?0:172,3-origin);
   for(int t=1050;t<=6000;t+=50)sample(t,opening?125:15,3-origin);
   assertTrue(p.held());assertEquals(3-origin,p.sourcePrimary());}
 }
 @Test public void duoOutgoingHoldSurvivesEarlyPrimaryRemap(){
  for(boolean opening:new boolean[]{false,true}){p.reset();int origin=opening?2:1;sample(1000,opening?0:172,origin);
   for(int t=1050;t<=2550;t+=50)sample(t,opening?40:130,3-origin);
   assertTrue(p.held());assertEquals(origin,p.sourcePrimary());}
 }
 @Test public void movementToOtherSideRestartsFullHold(){
  sample(1000,172,1);for(int t=1050;t<=2000;t+=50)sample(t,20,2);
  for(int t=2050;t<3550;t+=50)sample(t,120,2);assertFalse(p.held());sample(3550,120,2);assertTrue(p.held());assertEquals(1,p.sourcePrimary());
 }
 @Test public void alpha100BackAndForthThen128DegreeHold(){
  long at=1000;sample(at,0,2);
  for(float angle:new float[]{7,12,23,41,71,107,127,134,118,111,89,70,48,35,41,58,94,110,125,129,128}){
   for(int i=0;i<4;i++)sample(at+=50,angle,angle<90?1:2);
   assertFalse(p.held());
  }
  for(int i=0;i<40;i++)sample(at+=50,i%3==0?127:128,1);
  assertTrue(p.held());assertEquals(1,p.sourcePrimary());
 }
 @Test public void slowClosingDoesNotBecomeTimedPause(){
  sample(1000,172,1);for(int t=1050;t<=10000;t+=50){sample(t,170-(t-1050)*.018f,t<3000?1:2);assertFalse(p.held());}
 }
 @Test public void crossingAfterHoldCannotRearmOnIncomingPanel(){
  hold(120,1);sample(2550,89,2);for(int t=2600;t<=5000;t+=50)sample(t,20,2);assertTrue(p.held());assertEquals(2,p.sourcePrimary());
  sample(5050,0,2);for(int t=5100;t<=6600;t+=50)sample(t,20,1);assertTrue(p.held());assertEquals(2,p.sourcePrimary());
 }
 @Test public void exactlyNinetyKeepsCurrentPanel(){for(int panel=1;panel<=2;panel++){p.reset();hold(90,panel);assertEquals(panel,p.sourcePrimary());sample(2550,90,3-panel);assertTrue(p.held());assertEquals(panel,p.sourcePrimary());}}
 @Test public void openingStaysNativeUntilOpenAndNextClosingLeg(){hold(120,1);long held=p.startedAt();for(int a=121;a<172;a++){sample(2550+(a-121)*50,a,1);assertEquals(held,p.startedAt());}sample(5200,172,1);assertFalse(p.held());sample(5250,165,1);assertFalse(p.held());}
 @Test public void coverStaysNativeUntilClosedAndNextOpeningLeg(){hold(60,2);long held=p.startedAt();for(int a=59;a>0;a--){sample(2550+(59-a)*50,a,2);assertEquals(held,p.startedAt());}sample(5600,0,2);assertFalse(p.held());sample(5650,6,2);assertFalse(p.held());}
 @Test public void innerContinuationSwitchesNativePanelBelowNinety(){hold(120,1);sample(2550,100,1);assertTrue(p.held());sample(2600,90,1);assertTrue(p.held());sample(2650,89.9f,1);assertTrue(p.held());assertEquals(2,p.sourcePrimary());}
 @Test public void coverContinuationSwitchesNativePanelAboveNinety(){hold(60,2);sample(2550,85,2);assertTrue(p.held());sample(2600,90,2);assertTrue(p.held());sample(2650,90.1f,2);assertTrue(p.held());assertEquals(1,p.sourcePrimary());}
 @Test public void largeSameSideJitterDoesNotRestart(){hold(125,1);for(int t=2550;t<8000;t+=50){sample(t,t%100==0?91:169,1);assertTrue(p.held());}}
 @Test public void optionalMovementModeRetainsFiveDegreeRuleInBothDirections(){
  for(int d:new int[]{-1,1}){p.reset();for(int t=1000;t<=2500;t+=50)p.update(true,t,t,120,172,1,2,true,true);
   p.update(true,2550,2550,120+d*4.99f,172,1,2,true,true);assertTrue(p.held());
   p.update(true,2600,2600,120+d*5,172,1,2,true,true);assertFalse(p.held());}
 }
 @Test public void optionalModeSwitchResetsObservation(){hold(120,1);p.update(true,2550,2550,120,172,1,2,true,true);assertFalse(p.held());}
 @Test public void smallSuccessiveMovesUseOriginalHoldAnchorInMovementMode(){for(int t=1000;t<=2500;t+=50)p.update(true,t,t,60,172,2,2,true,true);for(int n=1;n<5;n++){p.update(true,2500+n*50,2500+n*50,60+n,172,2,2,true,true);assertTrue(p.held());}p.update(true,2750,2750,65,172,2,2,true,true);assertFalse(p.held());}
 @Test public void inputAndPowerGapsCannotRearmDefault(){hold(120,1);long at=p.startedAt();p.update(true,4000,2500,120,172,1,2);assertEquals(at,p.startedAt());p.update(true,4100,4100,130,172,1,2,false,false);assertEquals(at,p.startedAt());sample(4150,150,1);assertEquals(at,p.startedAt());sample(4200,89,1);assertEquals(at,p.startedAt());assertEquals(2,p.sourcePrimary());}
 @Test public void explicitDisableOrRouteChangeClearsLatch(){hold(120,1);p.update(false,2550,2550,120,172,1,2);assertFalse(p.held());p.reset();hold(120,1);p.update(true,2550,2550,120,172,1,0);assertFalse(p.held());}
 @Test public void freshEndpointCanClearLatchWhileIneligible(){hold(120,1);p.update(true,2550,2550,172,172,1,2,false,false);assertFalse(p.held());}
 @Test public void invalidAndFutureRepliesCannotRearmDefault(){for(float a:new float[]{Float.NaN,Float.POSITIVE_INFINITY}){p.reset();hold(120,1);sample(2550,a,1);assertTrue(p.held());}p.update(true,2600,2700,0,172,2,2);assertTrue(p.held());}
 @Test public void repeatedRepliesCannotManufactureHold(){sample(1000,60,2);for(int now=1050;now<=3000;now+=50)p.update(true,now,1000,60,172,2,2);assertFalse(p.held());sample(3050,60,2);assertFalse(p.held());}
 @Test public void gapsRestartFullObservation(){for(int t=1000;t<=1600;t+=50)sample(t,60,2);sample(1950,60,2);for(int t=2000;t<3450;t+=50)sample(t,60,2);assertFalse(p.held());sample(3450,60,2);assertTrue(p.held());}
 @Test public void ineligibleTimeCannotCountAsHold(){sample(1000,120,1);for(int t=1050;t<=4000;t+=50)p.update(true,t,t,120,172,1,2,false,false);sample(4050,120,1);assertFalse(p.held());}
 @Test public void cumulativeMovementRestartsObservation(){for(int d:new int[]{-1,1}){p.reset();for(int t=1000;t<5000;t+=100)sample(t,80+d*(t-1000)/100f,1);assertFalse(p.held());}}
 @Test public void upToFiveDegreesOfJitterAllowsHold(){sample(1000,70,2);for(int t=1050;t<=2800;t+=50)sample(t,70+(t/50)%6,2);assertTrue(p.held());}
 @Test public void greaterThanFiveRestartsFullTimer(){for(int t=1000;t<=2450;t+=50)sample(t,70,2);sample(2500,75.01f,2);assertFalse(p.held());for(int t=2550;t<4000;t+=50)sample(t,75.01f,2);assertFalse(p.held());sample(4000,75.01f,2);assertTrue(p.held());}
 @Test public void slowOneDegreeStepsDoNotCountAsStationaryInEitherDirection(){
  for(boolean opening:new boolean[]{false,true}){p.reset();sample(1000,opening?0:172,opening?2:1);
   for(int t=1050;t<=16000;t+=50){float a=opening?30+(t-1050)/1000:140-(t-1050)/1000;sample(t,a,opening?1:2);assertFalse(p.held());}
   for(int t=16050;t<=17550;t+=50)sample(t,opening?44:126,opening?1:2);
   assertTrue(p.held());
  }
 }
 @Test public void completedDuoHoldSurvivesBothMidpointCrossingsUntilEndpoint(){
  hold(120,1);long at=p.startedAt();for(float a:new float[]{80,30,110,60,150}){sample((at+=50),a,a<90?2:1);assertTrue(p.held());}
  sample(at+50,0,2);assertFalse(p.held());
 }
 @Test public void legacyMidpointResumeIsPreserved(){
  for(int mode=0;mode<2;mode++){p.reset();for(int t=1000;t<=2500;t+=50)p.update(true,t,t,120,172,1,mode);
   assertTrue(p.held());p.update(true,2550,2550,89,172,1,mode);assertFalse(p.held());}
 }
 @Test public void easingHasExactEndpointsAndMonotonicPoses(){assertEquals(0,HingePausePolicy.fraction(0,2000,280),0);assertEquals(.5f,HingePausePolicy.fraction(1000,1140,280),.0001f);float last=0;for(int t=1000;t<=1280;t+=10){float f=HingePausePolicy.fraction(1000,t,280);assertTrue(f>=last);last=f;}assertEquals(1,last,0);assertEquals(0,HingePausePolicy.pose(60,false,172,last),0);assertEquals(172,HingePausePolicy.pose(120,true,172,last),0);}
}
