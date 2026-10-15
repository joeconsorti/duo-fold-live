package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class GlassFallbackPolicyTest {
 // Plan B6: the no-frame fallback must not darken past the unified glass cap (it reached solid black vs 0.55).
 @Test public void unifiedFallbackRespectsTheDarkeningCap(){
  assertEquals(.55f,GlassFallbackPolicy.alpha(1f,true,.55f),1e-6);
  assertEquals(.3f,GlassFallbackPolicy.alpha(.3f,true,.55f),1e-6);
  assertEquals(1f,GlassFallbackPolicy.alpha(1f,false,.55f),1e-6);   // classic / non-unified keep upstream
 }
 // Plan F1: below a visible effect amount the fallback draws nothing (endpoint frames at 0.4-5 % effect).
 @Test public void invisibleAmountsDrawNothing(){
  assertFalse(GlassFallbackPolicy.draws(.05f));
  assertTrue(GlassFallbackPolicy.draws(GlassFallbackPolicy.MIN_VISIBLE_AMOUNT));
  assertTrue(GlassFallbackPolicy.draws(.5f));
 }
}
