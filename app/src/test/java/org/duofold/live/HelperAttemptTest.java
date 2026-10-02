package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class HelperAttemptTest {
 @Test public void timeoutRejectsLateConnectAndDuplicateCleanup(){
  HelperAttempt old=new HelperAttempt();
  assertTrue(old.finish());assertFalse(old.connect());assertFalse(old.finish());
  HelperAttempt retry=new HelperAttempt();assertTrue(retry.connect());assertTrue(retry.open());
 }
 @Test public void duplicateConnectCannotStartSecondPollLoop(){
  HelperAttempt attempt=new HelperAttempt();assertTrue(attempt.connect());assertFalse(attempt.connect());
  assertTrue(attempt.connected());assertTrue(attempt.finish());assertFalse(attempt.open());assertFalse(attempt.connect());
 }
}
