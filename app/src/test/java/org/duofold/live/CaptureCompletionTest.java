package org.duofold.live;
import java.util.*;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptureCompletionTest {
 @Test public void transfersSuccessfulResultWithoutDisposal()throws Exception{
  List<String> freed=new ArrayList<>();
  try(CaptureCompletion<String> c=new CaptureCompletion<>(freed::add)){c.accept("frame",0);assertEquals("frame",c.await(0));}
  assertTrue(freed.isEmpty());
 }
 @Test public void errorReportsStatusAndReleasesBuffer()throws Exception{
  List<String> freed=new ArrayList<>();
  try(CaptureCompletion<String> c=new CaptureCompletion<>(freed::add)){
   c.accept("frame",-1);try{c.await(0);fail();}catch(IllegalStateException e){assertTrue(e.getMessage().contains("status=-1"));}
  }assertEquals(Collections.singletonList("frame"),freed);
 }
 @Test public void timeoutDisposesLateResult()throws Exception{
  List<String> freed=new ArrayList<>();CaptureCompletion<String> c=new CaptureCompletion<>(freed::add);
  try{c.await(0);fail();}catch(TimeoutException expected){}finally{c.close();}
  c.accept("late",0);assertEquals(Collections.singletonList("late"),freed);
 }
 @Test public void closedRequestDisposesAlreadyReceivedResult(){
  List<String> freed=new ArrayList<>();CaptureCompletion<String> c=new CaptureCompletion<>(freed::add);
  c.accept("frame",0);c.close();c.close();assertEquals(Collections.singletonList("frame"),freed);
 }
 @Test public void nullBufferReportsDistinctFailure()throws Exception{
  try(CaptureCompletion<String> c=new CaptureCompletion<>(x->{})){
   c.accept(null,0);try{c.await(0);fail();}catch(IllegalStateException e){assertTrue(e.getMessage().contains("empty buffer"));}
  }
 }
}
