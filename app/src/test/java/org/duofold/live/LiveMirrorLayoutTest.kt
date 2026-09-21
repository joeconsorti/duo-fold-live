package org.duofold.live
import org.junit.Assert.*
import org.junit.Test
class LiveMirrorLayoutTest {
 @Test fun portraitCoverFitsInnerWithoutStretching(){val fit=LiveMirrorLayout.fit(1248,1972,2448,1848);assertEquals(1848f/1972,fit[0],.0001f);assertTrue(fit[1]>0);assertEquals(2448f,fit[1]+1248f*fit[0],.001f);assertEquals(0f,fit[2],.001f)}
 @Test fun sameAspectHasNoBars(){assertArrayEquals(floatArrayOf(2f,0f,0f),LiveMirrorLayout.fit(100,200,200,400),.001f)}
 @Test(expected=IllegalArgumentException::class) fun invalidSurfaceRejected(){LiveMirrorLayout.fit(0,100,100,100)}
 @Test fun innerProjectionFillsCoverWithoutBarsOrStretch(){
  val fit=LiveMirrorLayout.fill(2448,1848,1248,1972)
  assertEquals(1972f/1848,fit[0],.0001f)
  assertTrue(fit[1]<0f);assertEquals(0f,fit[2],.001f)
  assertEquals(624f,fit[1]+1224f*fit[0],.001f)
  assertTrue(fit[1]+2448f*fit[0]>=1248f)
 }
 @Test fun rotatedInnerProjectionCoversEntireCover(){
  val fit=LiveMirrorLayout.fill(1848,2448,1248,1972)
  assertTrue(fit[1]<=0f && fit[2]<=0f)
  assertTrue(fit[1]+1848f*fit[0]>=1248f)
  assertTrue(fit[2]+2448f*fit[0]>=1972f)
 }
 @Test fun normalizedGlassCropMatchesCompositorMirror(){
  val fit=LiveMirrorLayout.fill(2448,1848,1248,1972)
  val ew=2448f*fit[0]
  for(x in listOf(0f,624f,1248f)){
   val shaderSource=(x/1248f)*(1248f/ew)-fit[1]/ew
   val mirrorSource=(x-fit[1])/fit[0]/2448f
   assertEquals(mirrorSource,shaderSource,.00001f)
  }
 }
}
