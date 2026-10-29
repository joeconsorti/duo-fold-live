package org.duofold.live;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RotationReleaseTest {
 @Test public void failedFixedPolicyCannotPreventThawOrPostureRestoration(){
  List<String> calls=new ArrayList<>();
  try{RotationRelease.all(Arrays.asList(()->{calls.add("fixed");throw new Exception("fixed failed");},()->calls.add("thaw both"),()->calls.add("postures")));fail();}
  catch(Exception e){assertEquals("fixed failed",e.getMessage());}
  assertEquals(Arrays.asList("fixed","thaw both","postures"),calls);
 }
 @Test public void onePanelFailureCannotSkipOtherPanel(){
  List<Integer> calls=new ArrayList<>();
  try{RotationRelease.all(Arrays.asList(()->{calls.add(1);throw new Exception("missing");},()->calls.add(0)));fail();}
  catch(Exception expected){}
  assertEquals(Arrays.asList(1,0),calls);
 }
 @Test public void allFailuresAreRetained(){
  try{RotationRelease.all(Arrays.asList(()->{throw new Exception("one");},()->{throw new Exception("two");}));fail();}
  catch(Exception e){assertEquals("one",e.getMessage());assertEquals("two",e.getSuppressed()[0].getMessage());}
 }
 @Test public void retryRunsEveryOperationAgain(){
  int[] attempts={0,0};
  List<RotationRelease.Step> steps=Arrays.asList(()->{if(attempts[0]++==0)throw new Exception("transient");},()->{attempts[1]++;});
  try{RotationRelease.all(steps);}catch(Exception expected){}
  try{RotationRelease.all(steps);}catch(Exception e){fail(e.toString());}
  assertArrayEquals(new int[]{2,2},attempts);
 }
 @Test public void destinationPostureWinsOverOutgoingLock(){
  assertFalse(RotationRelease.locked(2,true));
  assertTrue(RotationRelease.locked(1,false));
 }
 @Test public void ignoredOrAbsentPosturePreservesOriginal(){
  assertTrue(RotationRelease.locked(0,true));assertFalse(RotationRelease.locked(null,false));
 }
 @Test public void savedPreferencesAndLocksPrecedeFixedRelease()throws Exception{
  List<String> calls=new ArrayList<>();
  RotationRelease.restore(()->calls.add("locks"),()->calls.add("postures"),()->calls.add("user"),()->calls.add("fixed"));
  assertEquals(Arrays.asList("locks","postures","user","fixed"),calls);
 }
 @Test public void eachRestorationFailureStillReleasesProtection(){
  for(int failed=0;failed<4;failed++){
   final int failureIndex=failed;List<Integer> calls=new ArrayList<>();
   RotationRelease.Step[] steps=new RotationRelease.Step[4];
   for(int i=0;i<4;i++){final int index=i;steps[i]=()->{calls.add(index);if(index==failureIndex)throw new Exception("phase "+index);};}
   try{RotationRelease.restore(steps[0],steps[1],steps[2],steps[3]);fail();}
   catch(Exception expected){assertEquals("phase "+failed,expected.getMessage());}
   assertEquals(Arrays.asList(0,1,2,3),calls);
  }
 }
}
