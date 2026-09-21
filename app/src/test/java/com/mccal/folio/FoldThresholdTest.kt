package com.mccal.folio
import org.junit.Assert.*
import org.junit.Test
class FoldThresholdTest {
 @Test fun defaultClearsBeforeMechanicalStop(){for(angle in listOf(172f,177f,179f,180f)){assertEquals(0f,DuoShadeCurve.progress(angle,true),.00001f);assertTrue(FoldThreshold.endpoint(angle,172f));assertFalse(FoldThreshold.canStart(angle,172f))}}
 @Test fun closingStartsBelowSameThreshold(){assertTrue(FoldThreshold.canStart(171.9f,172f));assertTrue(DuoShadeCurve.progress(171.9f,true)>0f);assertFalse(FoldThreshold.canStart(172f,172f))}
 @Test fun customThresholdControlsBothAnimationAndSwitch(){for(t in listOf(165f,170f,175f,179f)){assertEquals(0f,DuoShadeCurve.progress(t,true,t),.00001f);assertTrue(FoldThreshold.endpoint(t,t));assertTrue(FoldThreshold.canStart(t-1,t));assertTrue(DuoShadeCurve.progress(t-1,true,t)>0)}}
 @Test fun invalidThresholdIsBounded(){assertEquals(172f,FoldThreshold.sanitize(Float.NaN),0f);assertEquals(165f,FoldThreshold.sanitize(30f),0f);assertEquals(179f,FoldThreshold.sanitize(200f),0f)}
 @Test fun closedAndNonfiniteAnglesNeverArm(){assertFalse(FoldThreshold.canStart(0f,172f));assertFalse(FoldThreshold.canStart(Float.NaN,172f));assertTrue(FoldThreshold.endpoint(1f,172f));assertTrue(FoldThreshold.canStart(2f,172f))}
}
