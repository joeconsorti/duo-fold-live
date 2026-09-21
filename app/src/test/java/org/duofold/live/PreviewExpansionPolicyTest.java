package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewExpansionPolicyTest {
 @Test public void expansionStartsAtCoverGeometryAndCompletes(){
  assertEquals(0f,PreviewExpansionPolicy.progress(0),0);
  assertEquals(1f,PreviewExpansionPolicy.progress(240),0);
  assertTrue(PreviewExpansionPolicy.progress(120)>.5f);
 }
 @Test public void readyFrameCannotCutExpansionShort(){
  assertEquals(1f,PreviewExpansionPolicy.opacity(100,50),0);
  assertEquals(1f,PreviewExpansionPolicy.opacity(240,50),0);
  assertEquals(.5f,PreviewExpansionPolicy.opacity(330,50),.001f);
  assertEquals(0f,PreviewExpansionPolicy.opacity(420,50),0);
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
