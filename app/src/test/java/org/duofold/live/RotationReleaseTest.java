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
}
