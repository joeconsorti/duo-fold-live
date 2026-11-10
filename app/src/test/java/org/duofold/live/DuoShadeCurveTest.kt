package org.duofold.live
import org.junit.Assert.*
import org.junit.Test
class DuoShadeCurveTest {
 @Test fun coverFadeFinishesAt110(){assertTrue(DuoShadeCurve.progress(90f,false)<1f);assertEquals(1f,DuoShadeCurve.progress(110f,false),.00001f)}
 @Test fun endpointVisibility(){assertEquals(0f,DuoShadeCurve.alpha(DuoShadeCurve.progress(0f,false),1f),.00001f);assertEquals(0f,DuoShadeCurve.alpha(DuoShadeCurve.progress(180f,true),1f),.00001f)}
 @Test fun referenceNumericalSamples(){assertEquals(.392292f,DuoShadeCurve.alpha(.5f,.6f),.0001f);assertEquals(1f,DuoShadeCurve.alpha(1f,1f),.00001f);assertEquals(0f,DuoShadeCurve.alpha(1f,.2f),.00001f)}
 @Test fun gradientKeepsFixedInnerHalfClear(){for(x in listOf(.5f,.7f,1f))assertEquals(0f,DuoShadeCurve.alpha(1f,1f-2f*x),.00001f)}
 @Test fun standardPresentationUsesSameCurveAndImmediateInnerEndpoint(){
  for(inner in listOf(false,true))for(angle in listOf(0f,30f,80f,100f,150f))
   assertEquals(DuoShadeCurve.progress(angle,inner,172f),DuoShadeCurve.presentationProgress(angle,angle,inner,172f),0f)
  assertEquals(0f,DuoShadeCurve.presentationProgress(160f,172f,true,172f),0f)
  assertEquals(DuoShadeCurve.progress(160f,true,175f),DuoShadeCurve.presentationProgress(160f,172f,true,175f),0f)
 }
}
