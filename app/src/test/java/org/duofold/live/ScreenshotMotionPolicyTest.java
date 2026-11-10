package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenshotMotionPolicyTest {
 @Test public void screenshotGetsTiltWithoutChangingLiveEligibility(){
  for(boolean backdrop:new boolean[]{false,true})for(boolean preview:new boolean[]{false,true})for(boolean frozen:new boolean[]{false,true}){
   assertEquals(!backdrop&&!preview&&!frozen,ScreenshotMotionPolicy.usesMotion(false,backdrop,preview,frozen));
  }
  assertTrue(ScreenshotMotionPolicy.usesMotion(true,true,false,true));
  assertFalse(ScreenshotMotionPolicy.usesMotion(true,true,true,true));
 }
 @Test public void stationaryHingeStillConsumesNewestTiltAfterPendingFrame(){
  ScreenshotMotionPolicy p=new ScreenshotMotionPolicy();long submitted=p.revision();
  assertTrue(p.update(.1f,-.2f));assertTrue(p.update(.3f,-.4f));
  assertTrue(p.changedSince(submitted));
  long latest=p.revision();assertFalse(p.changedSince(latest));
  assertFalse(p.update(.3f,-.4f));assertFalse(p.changedSince(latest));
  assertTrue(p.update(0,0));assertTrue(p.changedSince(latest)); // disabling/recenter clears the last drawn tilt
 }
 @Test public void disabledPanelAndInvalidSamplesDoNotKeepDrawing(){
  ScreenshotMotionPolicy p=new ScreenshotMotionPolicy();
  for(int i=0;i<1000;i++)assertFalse(p.update(0,0));
  assertFalse(p.update(Float.NaN,0));assertFalse(p.update(0,Float.POSITIVE_INFINITY));
  assertEquals(0,p.revision());
 }
}
