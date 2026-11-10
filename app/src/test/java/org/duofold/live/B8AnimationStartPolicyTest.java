package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class B8AnimationStartPolicyTest {
 @Test public void bothPanelsCanStartRegardlessOfTravelBeforeFirstFrame(){
  B8AnimationStartPolicy cover=new B8AnimationStartPolicy(true),inner=new B8AnimationStartPolicy(true);
  assertTrue(cover.committed(14,true));assertTrue(inner.forFrame(130));
  assertTrue(inner.committed(145,true));assertEquals("animated",inner.status());
 }
 @Test public void largeTravelDuringFirstCommitDoesNotDiscardFrame(){
  B8AnimationStartPolicy p=new B8AnimationStartPolicy(true);
  assertTrue(p.forFrame(4));assertTrue(p.committed(150,true));
 }
 @Test public void quickClosingDoesNotBecomePermanentlyPlain(){
  B8AnimationStartPolicy p=new B8AnimationStartPolicy(true);
  assertTrue(p.forFrame(172));assertTrue(p.forFrame(100));assertTrue(p.committed(20,true));
 }
 @Test public void reversalBeforeFirstCommitRemainsAnimated(){
  B8AnimationStartPolicy p=new B8AnimationStartPolicy(true);
  assertTrue(p.forFrame(4));assertTrue(p.forFrame(90));assertTrue(p.forFrame(4));assertTrue(p.committed(6,true));
 }
 @Test public void activePanelSurvivesReversalAndRemap(){
  B8AnimationStartPolicy p=new B8AnimationStartPolicy(true);
  assertTrue(p.committed(160,true));assertTrue(p.forFrame(30));assertTrue(p.forFrame(140));
 }
 @Test public void endpointsDoNotLatchAStartupVeto(){
  B8AnimationStartPolicy p=new B8AnimationStartPolicy(true);
  assertTrue(p.committed(0,true));assertTrue(p.forFrame(172));assertTrue(p.forFrame(50));
 }
 @Test public void invalidSampleDoesNotSuppressLaterValidAnimation(){
  B8AnimationStartPolicy p=new B8AnimationStartPolicy(true);
  assertFalse(p.forFrame(Float.NaN));assertFalse(p.forFrame(Float.POSITIVE_INFINITY));
  assertFalse(p.committed(Float.NaN,true));assertTrue(p.forFrame(90));assertTrue(p.committed(91,true));
 }
 @Test public void userDisabledAnimationStillStaysDisabled(){
  B8AnimationStartPolicy p=new B8AnimationStartPolicy(false);
  assertFalse(p.forFrame(90));assertTrue(p.committed(90,false));assertFalse(p.committed(90,true));assertEquals("disabled",p.status());
 }
}
