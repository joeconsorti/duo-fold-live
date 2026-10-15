package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class HandoffFadePolicyTest {
 @Test public void gradualnessPreservesBlackThresholdsAndWidensApproach(){
  assertEquals(180,FadeSettings.reveal(0));assertEquals(500,FadeSettings.reveal(1));
  assertEquals(0,HandoffFadePolicy.approach(false,78,0,98),0);
  assertEquals(.5f,HandoffFadePolicy.approach(false,78,1,98),.001);
  assertEquals(.5f,HandoffFadePolicy.approach(true,114,1,98),.001);
  assertEquals(1,HandoffFadePolicy.approach(false,98,1,98),0);
  assertEquals(1,HandoffFadePolicy.approach(true,94,1,98),0);
 }
 @Test public void smoothingSoftensMovementButNeverDelaysFullBlack(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);p.settings(24,0);
  p.opacity(0,false,88,true,true,-1,false);
  float softened=p.opacity(8,false,93,true,true,-1,false);
  assertTrue(softened>0&&softened<.5f);
  assertEquals(1,p.opacity(16,false,98,true,true,-1,false),0);
  assertTrue(p.opacity(24,false,88,true,true,-1,false)<1);
 }
 @Test public void gradualRevealStillWaitsForDestinationDraw(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);p.settings(24,1);
  p.opacity(0,false,98,true,true,-1,false);
  assertEquals(1,p.opacity(10,true,98,true,true,-1,false),0);
  assertEquals(1,p.opacity(130,true,98,true,true,130,true),0);
  assertEquals(.5f,p.opacity(380,true,98,true,true,380,true),.001);
  assertEquals(0,p.opacity(630,true,98,true,true,630,true),0);
 }
 @Test public void invalidPreferencesUseSafeDefaults(){
  assertEquals(24,FadeSettings.smoothing(Float.NaN),0);
  assertEquals(.35f,FadeSettings.gradualness(Float.POSITIVE_INFINITY),0);
  assertEquals(0,FadeSettings.gradualness(-1),0);assertEquals(120,FadeSettings.smoothing(999),0);
 }
 @Test public void approachIsReversibleInBothDirections(){
  assertEquals(0,HandoffFadePolicy.approach(false,88,0,98),0);
  assertEquals(1,HandoffFadePolicy.approach(false,98,0,98),0);
  assertEquals(.5f,HandoffFadePolicy.approach(false,93,0,98),.001);
  assertEquals(0,HandoffFadePolicy.approach(true,104,0,98),0);
  assertEquals(1,HandoffFadePolicy.approach(true,94,0,98),0);
  HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);
  assertTrue(p.opacity(0,false,96,true,true,-1,false)>.8f);
  assertEquals(0,p.opacity(20,false,87,true,true,-1,false),0);
 }
 @Test public void waitsForStableOnAndDrawAfterSettleInBothDirections(){
  for(boolean destination:new boolean[]{true,false}){
   HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);float a=destination?98:94;
   p.opacity(0,!destination,a,true,true,-1,!destination);
   assertEquals(1,p.opacity(10,destination,a,true,false,-1,!destination),0);
   assertEquals(1,p.opacity(80,destination,a,true,true,5,destination),0);
   assertEquals(1,p.opacity(200,destination,a,true,true,111,destination),0);
   assertEquals(1,p.opacity(210,destination,a,true,true,205,!destination),0);
   assertEquals(1,p.opacity(220,destination,a,true,true,215,destination),0);
   assertEquals(.5f,p.opacity(310,destination,a,true,true,300,destination),.001);
   assertEquals(0,p.opacity(400,destination,a,true,true,390,destination),0);
   assertEquals(0,p.opacity(410,destination,a,true,true,400,destination),0);
  }
 }
 @Test public void offInterruptsRevealAndNextOnGetsFullFade(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);p.opacity(0,false,97,true,true,-1,false);
  p.opacity(10,true,98,true,true,10,true);
  p.opacity(130,true,98,true,true,130,true);
  assertEquals(.5f,p.opacity(220,true,98,true,true,210,true),.001);
  assertEquals(1,p.opacity(230,true,98,true,false,220,true),0);
  assertEquals(1,p.opacity(2000,true,98,true,false,220,true),0);
  assertEquals(1,p.opacity(2010,true,98,true,true,220,true),0);
  assertEquals(1,p.opacity(2130,true,98,true,true,2041,true),0);
  assertEquals(1,p.opacity(2140,true,98,true,true,2140,true),0);
  assertEquals(.5f,p.opacity(2230,true,98,true,true,2220,true),.001);
 }
 @Test public void drawTimeoutStartsFromOnNotMappingChange(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);p.opacity(0,false,97,true,true,-1,false);
  p.opacity(10,true,100,true,false,-1,false);
  assertEquals(1,p.opacity(2000,true,100,true,false,-1,false),0);
  assertEquals(1,p.opacity(2010,true,100,true,true,-1,false),0);
  assertEquals(1,p.opacity(2910,true,100,true,true,-1,false),0);
  assertEquals(0,p.opacity(3090,true,100,true,true,-1,false),0);
 }
 @Test public void physicalMappingChangeWorksBeforeGeometryCatchesUp(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);p.mapping("cover");p.opacity(0,false,98,true,true,-1,false);
  p.mapping("inner");assertEquals(1,p.opacity(10,false,98,true,false,-1,false),0);
  assertTrue(p.transitioning());
 }
 @Test public void freshDestinationCanRevealBeforeOld120msDelay(){
  for(boolean inner:new boolean[]{true,false}){
   HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);float angle=inner?98:94;
   p.opacity(0,!inner,angle,true,true,-1,!inner);
   assertEquals(1,p.opacity(10,inner,angle,true,true,-1,inner),0);
   assertEquals(1,p.opacity(42,inner,angle,true,true,42,inner),0);
   float alpha=p.opacity(60,inner,angle,true,true,60,inner);
   assertTrue(alpha<1&&alpha>0);
  }
 }
 @Test public void noSwitchTimesOutAndInvalidInputClears(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);p.opacity(0,false,100,true,true,-1,false);
  assertEquals(0,p.opacity(1201,false,100,true,true,-1,false),0);
  assertEquals(0,p.opacity(1210,false,100,false,true,-1,false),0);
  assertEquals(0,p.opacity(1220,false,Float.NaN,true,true,-1,false),0);
 }

 @Test public void fold7ReachesOpeningBlackAtMeasuredSwitchAngle(){
  // SM-F966U1 / One UI 8.5 measured at a 98 deg handoff: inner->cover switch at 92-94 deg, cover->inner at 101-102 deg.
  assertArrayEquals(new float[]{94,101},HandoffFadePolicy.blackAnglesFor("SM-F966U1",98),0);
  assertArrayEquals(new float[]{94,98},HandoffFadePolicy.blackAnglesFor("SM-F971U",98),0);
  assertArrayEquals(new float[]{94,98},HandoffFadePolicy.blackAnglesFor(null,98),0);
  // They follow the configurable handoff angle (85 by default; closing 4 below, Fold 7 opening 3 above).
  assertArrayEquals(new float[]{81,88},HandoffFadePolicy.blackAnglesFor("SM-F966U1",HandoffSettings.DEFAULT),0);
  assertArrayEquals(new float[]{81,85},HandoffFadePolicy.blackAnglesFor("SM-F971U",HandoffSettings.DEFAULT),0);
  assertEquals(1,HandoffFadePolicy.approach(false,101,0,94,101),0);
  assertTrue(HandoffFadePolicy.approach(false,98,0,94,101)<1);
  assertEquals(0,HandoffFadePolicy.approach(false,91,0,94,101),0);
  assertEquals(1,HandoffFadePolicy.approach(true,94,0,94,101),0);
  HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);p.coverLag(HandoffFadePolicy.coverLagFor("SM-F966U1"));p.settings(0,0);
  assertTrue(p.opacity(0,false,98,true,true,-1,false)<1);
  assertEquals(1,p.opacity(10,false,101,true,true,-1,false),0);
 }
 @Test public void handoffApproachMatchesExplicitBlackAngles(){
  for(float a=70;a<=120;a+=.5f)for(float g:new float[]{0,.35f,1})for(boolean inner:new boolean[]{true,false}){
   assertEquals(HandoffFadePolicy.approach(inner,a,g),HandoffFadePolicy.approach(inner,a,g,81,85),0);
   assertEquals(HandoffFadePolicy.approach(inner,a,g,98),HandoffFadePolicy.approach(inner,a,g,94,98),0);
  }
 }
 @Test public void invalidCoverLagFallsBackToNone(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.handoff(98);p.coverLag(Float.NaN);p.settings(0,0);
  assertEquals(1,p.opacity(0,false,98,true,true,-1,false),0);
 }

 @Test public void fold7RevealsShortlyAfterPanelOnWithoutWaitingForDraw(){
  assertEquals(40,HandoffFadePolicy.readyTimeoutFor("SM-F966U1"));
  assertEquals(HandoffFadePolicy.READY_TIMEOUT_MS,HandoffFadePolicy.readyTimeoutFor("SM-F971U"));
  assertEquals(100,HandoffFadePolicy.revealBaseFor("SM-F966U1"));
  assertEquals(180,HandoffFadePolicy.revealBaseFor("SM-F971U"));
  for(boolean destination:new boolean[]{true,false}){
   HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(40);p.revealBase(100);p.settings(0,0);
   float a=destination?101:94;
   p.opacity(0,!destination,a,true,true,-1,!destination);
   assertEquals(1,p.opacity(10,destination,a,true,false,-1,!destination),0);   // switched, panel OFF
   assertEquals(1,p.opacity(50,destination,a,true,true,-1,!destination),0);    // ON at 50, no draw yet
   assertEquals(1,p.opacity(85,destination,a,true,true,-1,!destination),0);    // 35 ms after ON: still black
   assertEquals(1,p.opacity(90,destination,a,true,true,-1,!destination),0);    // 40 ms: reveal begins
   assertEquals(.5f,p.opacity(140,destination,a,true,true,-1,!destination),.001); // halfway through 100 ms
   assertEquals(0,p.opacity(195,destination,a,true,true,-1,!destination),0);   // done
  }
 }
 @Test public void gradualnessStillWidensFold7Reveal(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.revealBase(100);
  assertEquals(100,p.revealMs(0));assertEquals(420,p.revealMs(1));
  HandoffFadePolicy d=new HandoffFadePolicy();
  assertEquals(FadeSettings.reveal(0),d.revealMs(0));assertEquals(FadeSettings.reveal(1),d.revealMs(1));
 }
 @Test public void fold7ChecksEveryFrameOnlyWhileSwitching(){
  assertEquals(16,HandoffFadePolicy.tickDelayMs(true,true));
  assertEquals(80,HandoffFadePolicy.tickDelayMs(true,false));
  assertEquals(80,HandoffFadePolicy.tickDelayMs(false,true));
  assertEquals(80,HandoffFadePolicy.tickDelayMs(false,false));
 }
 @Test public void invalidRevealBaseFallsBackToDefault(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.revealBase(5);assertEquals(180,p.revealMs(0));
  p.revealBase(10_000);assertEquals(180,p.revealMs(0));
 }
 @Test public void defaultReadyTimeoutUnchanged(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.settings(0,0);
  p.opacity(0,false,98,true,true,-1,false);
  p.opacity(10,true,98,true,true,-1,false);
  assertEquals(1,p.opacity(500,true,98,true,true,-1,false),0);
 }
 @Test public void invalidReadyTimeoutFallsBackToDefault(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(-5);p.settings(0,0);
  p.opacity(0,false,98,true,true,-1,false);
  p.opacity(10,true,98,true,true,-1,false);
  assertEquals(1,p.opacity(500,true,98,true,true,-1,false),0);
 }

 @Test public void fold7OpeningWaitsForInnerGlassSoCutOverIsInsideBlack(){
  assertEquals(600,HandoffFadePolicy.openingReadyTimeoutFor("SM-F966U1"));
  assertEquals(HandoffFadePolicy.READY_TIMEOUT_MS,HandoffFadePolicy.openingReadyTimeoutFor("SM-F971U"));
  HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(40);p.openingReadyTimeout(600);p.revealBase(100);p.settings(0,0);
  p.renderer(true,172);
  p.opacity(0,false,101,true,true,-1,false);                                   // cover primary
  assertEquals(1,p.opacity(10,true,101,true,false,-1,false),0);                // switched to inner, OFF
  assertEquals(1,p.opacity(50,true,101,true,true,-1,false),0);                 // ON at 50
  assertEquals(1,p.opacity(150,true,101,true,true,-1,false),0);                // 100 ms after ON: no fast reveal when opening
  int g=HandoffFadePolicy.GLASS_COMMITTED;
  assertEquals(1,p.opacity(400,true,101,true,true,390,true,g,380),0);          // glass committed at 390: reveal starts
  assertEquals(.5f,p.opacity(450,true,101,true,true,390,true,g,380),.001);     // half through 100 ms
  assertEquals(0,p.opacity(510,true,101,true,true,390,true,g,380),0);
 }
 @Test public void fold7OpeningStillHasABoundedEscape(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(40);p.openingReadyTimeout(600);p.settings(0,0);p.renderer(true,172);
  p.opacity(0,false,101,true,true,-1,false);
  p.opacity(10,true,101,true,true,-1,false);                                   // ON at 10
  assertEquals(1,p.opacity(600,true,101,true,true,-1,false),0);
  assertEquals(1,p.opacity(610,true,101,true,true,-1,false),0);                // escape fires at ON+600
  assertTrue(p.opacity(800,true,101,true,true,-1,false)<1);
 }
 @Test public void fold7ClosingKeepsFastReveal(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(40);p.openingReadyTimeout(600);p.settings(0,0);p.renderer(true,172);
  p.opacity(0,true,94,true,true,-1,true);                                      // inner primary
  p.opacity(10,false,94,true,true,-1,true);                                    // switched to cover, ON at 10
  assertEquals(1,p.opacity(50,false,94,true,true,-1,true),0);                  // reveal at ON+40
  assertTrue(p.opacity(120,false,94,true,true,-1,true)<1);
 }

 @Test public void fold7ClosingWaitsForCoverGlassSoRevealNeverLandsOnFallback(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(40);p.openingReadyTimeout(600);p.revealBase(100);p.settings(0,0);
  p.renderer(true,172);p.coverGlass(true);
  p.opacity(0,true,94,true,true,-1,true);                                      // inner primary
  p.opacity(10,false,94,true,true,-1,true);                                    // switched to cover, ON at 10
  assertEquals(1,p.opacity(60,false,94,true,true,-1,true),0);                  // no fast reveal at ON+40 any more
  int g=HandoffFadePolicy.GLASS_COMMITTED;
  assertEquals(1,p.opacity(200,false,94,true,true,190,true,g,180),0);          // an INNER commit is not evidence for the cover
  assertEquals(1,p.opacity(260,false,94,true,true,250,false,g,240),0);         // cover glass committed at 250: reveal starts
  assertEquals(.5f,p.opacity(310,false,94,true,true,250,false,g,240),.001);
 }
 @Test public void fold7ClosingCoverGlassStillHasBoundedEscape(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(40);p.openingReadyTimeout(600);p.closingReadyTimeout(300);p.settings(0,0);p.renderer(true,172);p.coverGlass(true);
  p.opacity(0,true,94,true,true,-1,true);
  p.opacity(10,false,94,true,true,-1,true);
  assertEquals(1,p.opacity(300,false,94,true,true,-1,true),0);
  assertEquals(1,p.opacity(310,false,94,true,true,-1,true),0);                 // closing escape at ON+300
  assertTrue(p.opacity(500,false,94,true,true,-1,true)<1);
 }

 @Test public void coverEndpointAtFullCloseCountsAsReady(){
  // Fully closed: the cover glass clears and the native screen is the destination; no capture is needed.
  HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(40);p.openingReadyTimeout(600);p.closingReadyTimeout(300);p.settings(0,0);p.renderer(true,172);p.coverGlass(true);
  p.opacity(0,true,94,true,true,-1,true);
  p.opacity(10,false,94,true,true,-1,true);                                    // ON at 10
  int e=HandoffFadePolicy.ENDPOINT_COMMITTED;
  assertEquals(1,p.opacity(90,false,0,true,true,80,false,e,-1),0);             // cover clear committed at 80
  assertTrue(p.opacity(150,false,0,true,true,80,false,e,-1)<1);
 }
 @Test public void slowCaptureIsStillFreshWithinWindow(){
  // Captures take 150-400 ms right after a switch; a frame whose capture started after ON must not be
  // rejected as stale just because the capture itself was slow.
  assertEquals(700,HandoffFadePolicy.freshWindowFor("SM-F966U1"));assertEquals(350,HandoffFadePolicy.freshWindowFor("SM-F971U"));
  HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(40);p.openingReadyTimeout(600);p.revealBase(100);p.settings(0,0);p.renderer(true,172);p.freshWindow(700);
  p.opacity(0,false,101,true,true,-1,false);
  p.opacity(10,true,101,true,true,-1,false);                                   // ON at 10
  int g=HandoffFadePolicy.GLASS_COMMITTED;
  assertEquals(1,p.opacity(520,true,101,true,true,510,true,g,20),0);           // captured at 20, committed at 510: 500 ms old
  assertTrue(p.opacity(600,true,101,true,true,510,true,g,20)<1);               // accepted
 }
 // Plan E8: pin every per-model closing cap default; other models keep the upstream 900 ms.
 @Test public void closingReadyTimeoutDefaultsArePinned(){
  assertEquals(380,HandoffFadePolicy.closingReadyTimeoutFor("SM-F966U1"));
  assertEquals(HandoffFadePolicy.READY_TIMEOUT_MS,HandoffFadePolicy.closingReadyTimeoutFor("SM-F971U"));
 }
 // Plan F4: readiness evidence carries the panel identity (inner/cover); a late frame from the OLD panel must never
 // count as the destination's readiness, however fresh it is.
 @Test public void lateFrameFromTheOldPanelNeverRevealsTheNewOne(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(40);p.openingReadyTimeout(600);p.closingReadyTimeout(380);p.settings(0,0);p.renderer(true,172);p.coverGlass(true);
  p.opacity(0,true,120,true,true,-1,true);                                              // inner primary
  assertEquals(1,p.opacity(10,false,94,true,false,-1,true),0);                          // switched to cover, panel OFF
  assertEquals(1,p.opacity(20,false,94,true,true,-1,true),0);                           // cover ON at 20
  int glass=HandoffFadePolicy.GLASS_COMMITTED;
  assertEquals(1,p.opacity(60,false,94,true,true,50,true,glass,40),0);                  // fresh INNER frame...
  assertEquals(1,p.opacity(160,false,94,true,true,50,true,glass,40),0);                 // ...still ignored 100 ms on
  assertEquals(1,p.opacity(161,false,94,true,true,161,false,glass,155),0);              // cover frame: readiness
  assertEquals(1,p.opacity(170,false,94,true,true,161,false,glass,155),0);              // reveal starts (0 % yet)
  assertTrue(p.opacity(220,false,94,true,true,161,false,glass,155)<1f);                 // and progresses
 }
 // Review (PR #18): the long opening cap is for waiting on inner GLASS; the no-glass path (Classic, debug) keeps the
 // short destination-draw cap instead of holding black for 600 ms.
 @Test public void openingCapAppliesOnlyWhenInnerGlassIsRequired(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.readyTimeout(40);p.openingReadyTimeout(600);p.closingReadyTimeout(380);p.settings(0,0);p.renderer(false,172);p.coverGlass(true);
  p.opacity(0,false,90,true,true,-1,false);                       // cover primary
  assertEquals(1,p.opacity(10,true,101,true,false,-1,false),0);   // switched to inner, OFF
  assertEquals(1,p.opacity(20,true,101,true,true,-1,false),0);    // inner ON at 20
  p.opacity(60,true,101,true,true,-1,false);                      // ON+40: cap reached on the no-glass path
  assertTrue(p.opacity(120,true,101,true,true,-1,false)<1f);      // revealing long before ON+600
 }
}
