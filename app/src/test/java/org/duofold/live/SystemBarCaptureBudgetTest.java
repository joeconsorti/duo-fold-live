package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class SystemBarCaptureBudgetTest {
 @Test public void oneInFlightAndTwoAttemptsPerPanelRole(){
  var b=new SystemBarCaptureBudget();assertTrue(b.start("inner:D1",1000));assertFalse(b.start("cover:D0",1000));
  b.finish("inner:D1",1020,false);assertFalse(b.start("inner:D1",1169));assertTrue(b.start("inner:D1",1170));b.finish("inner:D1",1200,false);
  assertFalse(b.start("inner:D1",2000));assertTrue(b.start("inner:D0",2000));
 }
 @Test public void closureRejectsLateCompletionAndAnyFurtherRequests(){
  var b=new SystemBarCaptureBudget();assertTrue(b.start("cover:D0",100));b.close();assertFalse(b.finish("cover:D0",120,true));assertFalse(b.start("inner:D0",1000));
 }
 @Test public void successfulPartialCaptureAlsoHasCooldownAndBoundedRetries(){
  var b=new SystemBarCaptureBudget(3,250);assertTrue(b.start("inner:D0",1000));b.finish("inner:D0",1100,true);
  assertFalse(b.start("inner:D0",1349));assertTrue(b.start("inner:D0",1350));b.finish("inner:D0",1400,false);
  assertTrue(b.start("inner:D0",1650));b.finish("inner:D0",1700,true);assertFalse(b.start("inner:D0",2000));
 }
 @Test public void newSessionHasNoInheritedCooldownOrFailure(){
  var old=new SystemBarCaptureBudget();old.start("inner:D0",100);old.close();
  assertTrue(new SystemBarCaptureBudget().start("inner:D0",101));
 }
}
