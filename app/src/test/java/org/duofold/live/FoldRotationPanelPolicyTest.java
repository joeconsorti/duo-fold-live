package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class FoldRotationPanelPolicyTest {
 @Test public void bothPanelsRetainTheirOwnOrientationAcrossRepeatedRemaps(){
  for(int cover=0;cover<4;cover++)for(int inner=0;inner<4;inner++){
   for(String[] order:new String[][]{{"local:cover","local:inner"},{"local:inner","local:cover"},{"local:cover","local:inner"}}){
    for(String physical:order)assertEquals(physical.equals("local:cover")?cover:inner,
     FoldRotationPanelPolicy.rotation(physical,"local:cover",cover,"local:inner",inner,-1));
   }
  }
 }
 @Test public void unsavedOrExternalDisplaysCannotAcquireSecondaryLock(){
  for(String id:new String[]{null,"","unknown","virtual:inner","local:new"})
   assertEquals(-1,FoldRotationPanelPolicy.rotation(id,"local:cover",0,"local:inner",3,-1));
 }
 @Test public void resumedSessionUsesNewOrientationAfterPause(){
  assertEquals(0,FoldRotationPanelPolicy.rotation("local:inner","local:cover",0,"local:inner",0,-1));
  assertEquals(3,FoldRotationPanelPolicy.rotation("local:inner","local:inner",3,"local:cover",1,-1));
 }
}
