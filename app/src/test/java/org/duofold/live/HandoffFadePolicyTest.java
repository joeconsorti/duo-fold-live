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
 @Test public void switchWaitsForDestinationDrawThenRevealsWithoutRedimming(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.opacity(0,false,97,true,true,-1,false);
  assertEquals(1,p.opacity(10,true,98,true,false,-1,false),0);
  assertEquals(1,p.opacity(80,true,98,true,true,5,true),0);
  assertEquals(1,p.opacity(90,true,98,true,true,85,false),0);
  assertEquals(1,p.opacity(100,true,98,true,true,95,true),0);
  assertEquals(.5f,p.opacity(190,true,98,true,true,180,true),.001);
  assertEquals(0,p.opacity(280,true,98,true,true,270,true),0);
  assertEquals(0,p.opacity(300,true,98,true,true,290,true),0);
  assertTrue(p.opacity(310,true,96,true,true,300,true)>.8f);
 }
 @Test public void returnToCoverAlsoStartsBlack(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.opacity(0,true,95,true,true,0,true);
  assertEquals(1,p.opacity(10,false,94,true,true,0,true),0);
  assertEquals(1,p.opacity(70,false,94,true,true,60,false),0);
  assertEquals(0,p.opacity(250,false,94,true,true,240,false),0);
 }
 @Test public void missingDrawAndMissingSwitchCannotLeaveScreenBlack(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.opacity(0,false,97,true,true,-1,false);
  p.opacity(10,true,100,true,true,-1,false);
  assertEquals(1,p.opacity(910,true,100,true,true,-1,false),0);
  assertEquals(0,p.opacity(1090,true,100,true,true,-1,false),0); // timeout starts on first sampled deadline
 }
 @Test public void noSwitchTimesOutAndInvalidInputClears(){
  HandoffFadePolicy p=new HandoffFadePolicy();p.opacity(0,false,100,true,true,-1,false);
  assertEquals(0,p.opacity(1201,false,100,true,true,-1,false),0);
  assertEquals(0,p.opacity(1210,false,100,false,true,-1,false),0);
  assertEquals(0,p.opacity(1220,false,Float.NaN,true,true,-1,false),0);
 }
}
