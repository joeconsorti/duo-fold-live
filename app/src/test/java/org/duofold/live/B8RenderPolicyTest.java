package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class B8RenderPolicyTest {
 @Test public void drawnAngleCannotRefreshAnOlderCommittedPose(){
  // The renderer may have drawn 140 degrees while its committed frame is still 100.
  assertFalse(B8RenderPolicy.committedPoseCurrent(true,100,140,1000,1300));
  assertTrue(B8RenderPolicy.committedPoseCurrent(true,100,140,1000,1250));
  assertTrue(B8RenderPolicy.committedPoseCurrent(true,140,140,1000,5000));
  assertTrue(B8RenderPolicy.committedPoseCurrent(false,100,140,1000,5000));
  assertFalse(B8RenderPolicy.committedPoseCurrent(true,Float.NaN,140,1000,1100));
 }
 @Test public void remapRetainsBufferUntilIdentityLayoutAndExclusionsAgree(){
  assertTrue(B8RenderPolicy.canAcquire(true,true,true));
  assertFalse(B8RenderPolicy.canAcquire(false,true,true));
  assertFalse(B8RenderPolicy.canAcquire(true,false,true));
  assertFalse(B8RenderPolicy.canAcquire(true,true,false));
 }
 @Test public void frameBudgetUsesElapsedTimeWithoutBuildingAQueue(){
  assertEquals(0,B8RenderPolicy.frameDelay(100,0));assertEquals(17,B8RenderPolicy.frameDelay(100,100));
  assertEquals(9,B8RenderPolicy.frameDelay(108,100));assertEquals(0,B8RenderPolicy.frameDelay(117,100));
  assertEquals(0,B8RenderPolicy.frameDelay(600,100));assertEquals(0,B8RenderPolicy.frameDelay(99,100));
 }
 @Test public void restingBetweenFoldsIsNotReportedAsAStalledAnimation(){
  assertEquals(0,B8RenderPolicy.movingGap(5000,100,false));
  assertEquals(0,B8RenderPolicy.movingGap(100,0,true));
  assertEquals(500,B8RenderPolicy.movingGap(600,100,true));
 }
 @Test public void animationStopsAtRest(){assertFalse(B8RenderPolicy.needsAnimation(179,179));assertTrue(B8RenderPolicy.needsAnimation(170,179));assertFalse(B8RenderPolicy.needsAnimation(Float.NaN,179));}
 @Test public void staticPixelsSurviveIdleButAnimatedCommitsExpire(){
  assertTrue(B8RenderPolicy.commitCurrent(false,100,5000));
  assertFalse(B8RenderPolicy.commitCurrent(true,100,351));
  assertTrue(B8RenderPolicy.commitCurrent(true,100,350));
  assertFalse(B8RenderPolicy.commitCurrent(false,0,5000));
  assertFalse(B8RenderPolicy.commitCurrent(false,100,99));
 }
 @Test public void staticAngleUpdatesAndIdleDoNotRepaint(){
  assertFalse(B8RenderPolicy.shouldSubmit(false,true,true,false,1000,false));
  assertFalse(B8RenderPolicy.shouldSubmit(false,false,true,false,1000,false));
  assertTrue(B8RenderPolicy.shouldSubmit(true,true,true,false,1,false));
 }
 @Test public void invalidatedImageOrSurfaceRequiresSubmission(){
  assertTrue(B8RenderPolicy.shouldSubmit(false,false,false,false,0,false));
  assertFalse(B8RenderPolicy.shouldSubmit(false,true,false,true,0,false));
 }
 @Test public void primaryHandoffKeepsFreshCommitsWithoutFloodingPendingFrames(){
  assertFalse(B8RenderPolicy.shouldSubmit(false,false,true,false,149,true));
  assertTrue(B8RenderPolicy.shouldSubmit(false,false,true,false,150,true));
  assertFalse(B8RenderPolicy.shouldSubmit(false,false,true,true,500,true));
 }
 @Test public void remapCannotAcceptOldCoverLayoutForInnerPanel(){
  assertFalse(B8RenderPolicy.matchesPanelShape(1848,2448,1248,1972));
  assertTrue(B8RenderPolicy.matchesPanelShape(1848,2448,2448,1848));
  assertTrue(B8RenderPolicy.matchesPanelShape(1848,2448,1848,2448));
 }
 @Test public void remapCannotAcceptOldInnerLayoutForCoverPanel(){
  assertFalse(B8RenderPolicy.matchesPanelShape(1248,1972,2448,1848));
  assertTrue(B8RenderPolicy.matchesPanelShape(1248,1972,1248,1972));
  assertFalse(B8RenderPolicy.matchesPanelShape(0,0,0,0));
 }
}
