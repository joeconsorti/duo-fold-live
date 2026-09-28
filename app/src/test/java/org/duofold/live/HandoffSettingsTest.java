package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class HandoffSettingsTest {
 @Test public void boundsAndInvalidValues(){
  assertEquals(98f,HandoffSettings.angle(Float.NaN),0f);
  assertEquals(98f,HandoffSettings.angle(Float.POSITIVE_INFINITY),0f);
  assertEquals(83f,HandoffSettings.angle(0),0f);
  assertEquals(113f,HandoffSettings.angle(180),0f);
 }
 @Test public void switchReturnGapAndFadeMoveTogether(){
  for(float opening:new float[]{83,98,113}){
   float closing=opening-4;
   HandoffPolicy policy=new HandoffPolicy();policy.handoff(opening);
   assertEquals(1,policy.update(closing,true,true));
   assertEquals(0,policy.update(opening-.1f,true,true));
   assertEquals(-1,policy.update(opening,true,true));
   assertEquals(0,policy.update(closing+.1f,true,true));
   assertEquals(1,policy.update(closing,true,true));
   assertEquals(DirectHandoffPolicy.INNER,DirectHandoffPolicy.next(false,opening,true,true,true,172,opening));
   assertEquals(DirectHandoffPolicy.COVER,DirectHandoffPolicy.next(true,closing,true,true,true,172,opening));
   for(float gradual:new float[]{0,.5f,1}){
    assertEquals(1f,HandoffFadePolicy.approach(false,opening,gradual,opening),.00001f);
    assertEquals(1f,HandoffFadePolicy.approach(true,closing,gradual,opening),.00001f);
    assertEquals(HandoffFadePolicy.approach(false,96,gradual),HandoffFadePolicy.approach(false,opening-2,gradual,opening),.00001f);
   }
  }
 }
 @Test public void defaultStillUsesOriginalAngles(){
  HandoffPolicy p=new HandoffPolicy();assertEquals(1,p.update(94,true,true));assertEquals(0,p.update(97,true,true));assertEquals(-1,p.update(98,true,true));
 }
}
