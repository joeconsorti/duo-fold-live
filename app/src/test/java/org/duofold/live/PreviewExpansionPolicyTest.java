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
}
