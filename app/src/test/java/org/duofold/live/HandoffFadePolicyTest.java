package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class HandoffFadePolicyTest {
 @Test public void approachIsReversibleInBothDirections(){
  assertEquals(0,HandoffFadePolicy.approach(false,88),0);
  assertEquals(1,HandoffFadePolicy.approach(false,98),0);
  assertEquals(.5f,HandoffFadePolicy.approach(false,93),.001);
  assertEquals(0,HandoffFadePolicy.approach(true,104),0);
  assertEquals(1,HandoffFadePolicy.approach(true,94),0);
  HandoffFadePolicy p=new HandoffFadePolicy();
  assertTrue(p.opacity(0,false,96,true,true,-1,false)>.8f);
  assertEquals(0,p.opacity(20,false,87,true,true,-1,false),0);
 }
 @Test public void waitsForStableOnAndDrawAfterSettleInBothDirections(){
  for(boolean destination:new boolean[]{true,false}){
   HandoffFadePolicy p=new HandoffFadePolicy();float a=destination?98:94;
   p.opacity(0,!destination,a,true,true,-1,!destination);
   assertEquals(1,p.opacity(10,destination,a,true,false,-1,!destination),0);
   assertEquals(1,p.opacity(80,destination,a,true,true,5,destination),0);
   assertEquals(1,p.opacity(200,destination,a,true,true,199,destination),0);
   assertEquals(1,p.opacity(210,destination,a,true,true,205,!destination),0);
   assertEquals(1,p.opacity(220,destination,a,true,true,215,destination),0);
   assertEquals(.5f,p.opacity(310,destination,a,true,true,300,destination),.001);
   assertEquals(0,p.opacity(400,destination,a,true,true,390,destination),0);
   assertEquals(0,p.opacity(410,destination,a,true,true,400,destination),0);
  }
 }
 @Test public void offInterruptsRevealAndNextOnGetsFullFade(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.opacity(0,false,97,true,true,-1,false);
  p.opacity(10,true,98,true,true,10,true);
  p.opacity(130,true,98,true,true,130,true);
  assertEquals(.5f,p.opacity(220,true,98,true,true,210,true),.001);
  assertEquals(1,p.opacity(230,true,98,true,false,220,true),0);
  assertEquals(1,p.opacity(2000,true,98,true,false,220,true),0);
  assertEquals(1,p.opacity(2010,true,98,true,true,220,true),0);
  assertEquals(1,p.opacity(2130,true,98,true,true,2129,true),0);
  assertEquals(1,p.opacity(2140,true,98,true,true,2140,true),0);
  assertEquals(.5f,p.opacity(2230,true,98,true,true,2220,true),.001);
 }
 @Test public void drawTimeoutStartsFromOnNotMappingChange(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.opacity(0,false,97,true,true,-1,false);
  p.opacity(10,true,100,true,false,-1,false);
  assertEquals(1,p.opacity(2000,true,100,true,false,-1,false),0);
  assertEquals(1,p.opacity(2010,true,100,true,true,-1,false),0);
  assertEquals(1,p.opacity(2910,true,100,true,true,-1,false),0);
  assertEquals(0,p.opacity(3090,true,100,true,true,-1,false),0);
 }
 @Test public void physicalMappingChangeWorksBeforeGeometryCatchesUp(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.mapping("cover");p.opacity(0,false,98,true,true,-1,false);
  p.mapping("inner");assertEquals(1,p.opacity(10,false,98,true,false,-1,false),0);
  assertTrue(p.transitioning());
 }
 @Test public void noSwitchTimesOutAndInvalidInputClears(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.opacity(0,false,100,true,true,-1,false);
  assertEquals(0,p.opacity(1201,false,100,true,true,-1,false),0);
  assertEquals(0,p.opacity(1210,false,100,false,true,-1,false),0);
  assertEquals(0,p.opacity(1220,false,Float.NaN,true,true,-1,false),0);
 }
}
