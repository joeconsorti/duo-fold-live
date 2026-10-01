package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class ClosingAppReturnPolicyTest {
 @Test public void onlyActsBeforeClosingEndpoint(){
  assertTrue(ClosingAppReturnPolicy.shouldReturn(true,true,true,true,true,true,false,15));
  assertFalse(ClosingAppReturnPolicy.shouldReturn(true,true,true,true,true,true,false,0));
  assertFalse(ClosingAppReturnPolicy.shouldReturn(true,true,true,true,true,true,false,16));
  assertFalse(ClosingAppReturnPolicy.shouldReturn(true,true,true,true,true,true,false,Float.NaN));
 }
 @Test public void excludesOpeningStaleLockedAndRepeatedRequests(){
  assertFalse(ClosingAppReturnPolicy.shouldReturn(true,true,false,true,true,true,false,10));
  assertFalse(ClosingAppReturnPolicy.shouldReturn(true,true,true,true,false,true,false,10));
  assertFalse(ClosingAppReturnPolicy.shouldReturn(true,true,true,true,true,false,false,10));
  assertFalse(ClosingAppReturnPolicy.shouldReturn(true,true,true,true,true,true,true,10));
  assertFalse(ClosingAppReturnPolicy.shouldReturn(false,true,true,true,true,true,false,10));
  assertFalse(ClosingAppReturnPolicy.shouldReturn(true,false,true,true,true,true,false,10));
 }
}
