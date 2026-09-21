package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class CoverPreviewPolicyTest {
 @Test public void previewOnlyDuringNormalCoverSession(){
  assertTrue(CoverPreviewPolicy.allowed(true,false,false,true,true));
  assertFalse(CoverPreviewPolicy.allowed(true,true,false,true,true));
  assertFalse(CoverPreviewPolicy.allowed(true,false,true,true,true));
  assertFalse(CoverPreviewPolicy.allowed(true,false,false,false,true));
  assertFalse(CoverPreviewPolicy.allowed(true,false,false,true,false));
  assertFalse(CoverPreviewPolicy.allowed(false,false,false,true,true));
 }
}
