package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewExpansionPolicyTest {
 @Test public void expansionStartsAtCoverGeometryAndCompletes(){
  assertEquals(0f,PreviewExpansionPolicy.progress(0),0);
  assertEquals(1f,PreviewExpansionPolicy.progress(240),0);
  assertTrue(PreviewExpansionPolicy.progress(120)>.5f);
 }
 @Test public void readyFrameStartsFadeWithoutExpansionDelay(){
  assertEquals(1f,PreviewExpansionPolicy.opacity(50,50),0);
  assertEquals(.5f,PreviewExpansionPolicy.opacity(110,50),.001f);
  assertEquals(0f,PreviewExpansionPolicy.opacity(170,50),0);
 }
 @Test public void missingInnerFrameHasBoundedFallback(){
  assertEquals(1f,PreviewExpansionPolicy.opacity(1000,-1),0);
  assertEquals(0f,PreviewExpansionPolicy.opacity(1500,-1),0);
 }
 @Test public void staleOrFutureFrameNeverArmsBridge(){
  assertFalse(PreviewExpansionPolicy.fresh(0,100));
  assertFalse(PreviewExpansionPolicy.fresh(100,99));
  assertFalse(PreviewExpansionPolicy.fresh(100,601));
  assertTrue(PreviewExpansionPolicy.fresh(100,600));
 }

 @Test public void zeroFadeDropsHoldAtReadinessWhileBlackStillCoversIt(){
  // Fold 7 unified: the reveal from black starts at readiness, so a cross-faded hold would double-expose.
  assertEquals(1f,PreviewExpansionPolicy.opacity(49,50,0),0);
  assertEquals(0f,PreviewExpansionPolicy.opacity(50,50,0),0);
  assertEquals(1f,PreviewExpansionPolicy.opacity(1000,-1,0),0);
  assertEquals(0f,PreviewExpansionPolicy.opacity(1500,-1,0),0);
  assertEquals(PreviewExpansionPolicy.opacity(110,50),PreviewExpansionPolicy.opacity(110,50,120),0);
 }
 // Plan E8: with zero fade but no readiness (timeout path) the hold still cross-fades over 120 ms.
 @Test public void noReadinessKeepsTheCrossFadeEvenWithZeroFade(){
  assertEquals(1f,PreviewExpansionPolicy.opacity(1200,-1,0),1e-6);
  assertEquals(.5f,PreviewExpansionPolicy.opacity(1260,-1,0),1e-6);
  assertEquals(0f,PreviewExpansionPolicy.opacity(1320,-1,0),1e-6);
  assertEquals(0f,PreviewExpansionPolicy.opacity(400,300,0),1e-6);   // with readiness: dropped at once
 }
 // Review (PR #18): hold readiness accepts the same per-model window the reveal uses; preparation keeps 500 ms.
 @Test public void readinessWindowMatchesTheRevealWindow(){
  assertFalse(PreviewExpansionPolicy.fresh(1000,1600));
  assertTrue(PreviewExpansionPolicy.fresh(1000,1600,700));
  assertFalse(PreviewExpansionPolicy.fresh(1000,1701,700));
  assertFalse(PreviewExpansionPolicy.fresh(0,100,700));
 }
}
