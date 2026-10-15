package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class MainGlassSurfacesTest {
 // Plan B1: skip-screenshot on Duo's main glass is also its exclusion from the live cover->inner mirror.
 @Test public void normalUseAlwaysSkips(){
  assertTrue(MainGlassSurfaces.skipFor(false,false));
  assertTrue(MainGlassSurfaces.skipFor(false,true));
 }
 @Test public void recordModeSkipsOnlyWhileTheMirrorIsLive(){
  assertTrue(MainGlassSurfaces.skipFor(true,true));    // un-skipping would mirror the cover glass onto the inner
  assertFalse(MainGlassSurfaces.skipFor(true,false));  // otherwise visible to captures (and excluded from glass capture)
 }
 @Test public void keepsABoundedNumberOfHandles(){assertTrue(MainGlassSurfaces.MAX_SURFACES>=2&&MainGlassSurfaces.MAX_SURFACES<=8);}
}
