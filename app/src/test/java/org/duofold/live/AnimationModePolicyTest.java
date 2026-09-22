package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnimationModePolicyTest {
 @Test public void defaultNeverChangesExistingDirectionBehavior(){AnimationModePolicy p=new AnimationModePolicy();for(float a:new float[]{0,60,98,179,130,94,0,Float.NaN})assertTrue(p.update("windowed",a,true));assertTrue(AnimationModePolicy.mirrors("windowed"));assertEquals("windowed",AnimationModePolicy.sanitize(null));}
 @Test public void classicKeepsNativeAnimationWithoutMirror(){AnimationModePolicy p=new AnimationModePolicy();assertTrue(p.update("duo_classic",110,true));assertFalse(AnimationModePolicy.mirrors("duo_classic"));}
 @Test public void foldOnlyTracksClosingAndIgnoresSmallJitter(){AnimationModePolicy p=new AnimationModePolicy();assertFalse(p.update("fold_only",0,true));assertFalse(p.update("fold_only",100,true));assertTrue(p.update("fold_only",179,true));assertTrue(p.update("fold_only",120,true));assertTrue(p.update("fold_only",121,true));assertFalse(p.update("fold_only",123,true));}
 @Test public void unfoldOnlyAndStaleSamples(){AnimationModePolicy p=new AnimationModePolicy();assertTrue(p.update("unfold_only",0,true));assertTrue(p.update("unfold_only",98,true));assertTrue(p.update("unfold_only",40,false));assertFalse(p.update("unfold_only",94,true));assertTrue(p.update("unfold_only",0,true));}
}
