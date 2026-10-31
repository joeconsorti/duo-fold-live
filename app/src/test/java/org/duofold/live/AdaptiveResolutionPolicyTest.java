package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdaptiveResolutionPolicyTest {
 @Test public void firstThermalStepSkipsFiftyAndStaysLowWhileWarm(){
  AdaptiveResolutionPolicy p=new AdaptiveResolutionPolicy(false);
  p.observe(0,0);assertEquals(100,p.effective(100,true));
  for(int status=1;status<=6;status++){p.observe(status,status*1000);assertEquals(25,p.effective(100,true));}
  p.observe(1,900000);assertEquals(25,p.cap());assertEquals(-1,p.remaining(900000));
 }
 @Test public void fiftyLastsTenContinuousCoolMinutesNotTenMinutesSinceHeating(){
  AdaptiveResolutionPolicy p=new AdaptiveResolutionPolicy(false);
  p.observe(1,0);p.observe(0,600000);assertEquals(50,p.cap());
  p.observe(0,1199999);assertEquals(50,p.cap());assertEquals(1,p.remaining(1199999));
  p.observe(0,1200000);assertEquals(100,p.cap());assertFalse(p.recovering());
 }
 @Test public void repeatedCoolReadingsDoNotRestartRecovery(){
  AdaptiveResolutionPolicy p=new AdaptiveResolutionPolicy(false);
  p.observe(2,0);p.observe(0,1000);
  for(int i=1;i<600;i++){p.observe(0,1000+i*1000);assertEquals(50,p.cap());}
  p.observe(0,601000);assertEquals(100,p.cap());
 }
 @Test public void ReheatingEvenBrieflyRestartsTheEntireCooldown(){
  AdaptiveResolutionPolicy p=new AdaptiveResolutionPolicy(false);
  p.observe(1,0);p.observe(0,1000);p.observe(1,600999);assertEquals(25,p.cap());
  p.observe(0,601000);p.observe(0,1200999);assertEquals(50,p.cap());
  p.observe(0,1201000);assertEquals(100,p.cap());
 }
 @Test public void droppingFromModerateToLightDoesNotRestoreQuality(){
  AdaptiveResolutionPolicy p=new AdaptiveResolutionPolicy(false);
  p.observe(2,0);p.observe(1,1000);assertEquals(25,p.cap());
  p.observe(0,2000);assertEquals(50,p.cap());
 }
 @Test public void disablingBypassesCapButDoesNotEraseCoolingHistory(){
  AdaptiveResolutionPolicy p=new AdaptiveResolutionPolicy(false);
  p.observe(1,0);assertEquals(100,p.effective(100,false));assertEquals(25,p.effective(100,true));
  p.observe(0,1000);assertEquals(100,p.effective(100,false));assertEquals(50,p.effective(100,true));
 }
 @Test public void adaptiveNeverExceedsManualMaximum(){
  for(int selected:new int[]{25,50,75,100}){
   AdaptiveResolutionPolicy p=new AdaptiveResolutionPolicy(false);
   assertEquals(selected,p.effective(selected,true));p.observe(1,0);assertEquals(25,p.effective(selected,true));
   p.observe(0,1);assertEquals(Math.min(selected,50),p.effective(selected,true));
   p.observe(0,600001);assertEquals(selected,p.effective(selected,true));
  }
 }
 @Test public void processRestartWithPendingRecoveryCannotJumpToFullQuality(){
  AdaptiveResolutionPolicy p=new AdaptiveResolutionPolicy(true);
  assertEquals(25,p.cap());p.observe(0,9999999);assertEquals(50,p.cap());
  assertEquals(600000,p.remaining(9999999));
 }
 @Test public void failedThermalReadDoesNotCountAsCool(){
  AdaptiveResolutionPolicy p=new AdaptiveResolutionPolicy(false);
  p.observe(1,0);p.observe(0,1);p.observe(-1,600001);assertEquals(25,p.cap());
  p.observe(0,600002);assertEquals(50,p.cap());assertEquals(600000,p.remaining(600002));
 }
 @Test public void eachThermalStageProducesActualPanelRasters(){
  AdaptiveResolutionPolicy p=new AdaptiveResolutionPolicy(false);
  assertEquals(2448,RenderQuality.bufferDimension(2448,p.effective(100,true),true,0));
  p.observe(1,0);assertEquals(612,RenderQuality.bufferDimension(2448,p.effective(100,true),true,0));
  assertEquals(493,RenderQuality.bufferDimension(1972,p.effective(100,true),true,0));
  p.observe(0,1);assertEquals(1224,RenderQuality.bufferDimension(2448,p.effective(100,true),true,0));
  p.observe(0,600001);assertEquals(2448,RenderQuality.bufferDimension(2448,p.effective(100,true),true,0));
 }
 @Test public void requestedAppearanceDefaults(){assertEquals(100,RenderQuality.DEFAULT_RESOLUTION);assertEquals(.5f,RenderQuality.DEFAULT_GLASS,0);assertEquals(.75f,RenderQuality.DEFAULT_BLUR,0);}
}
