package org.duofold.live;
import org.duofold.live.experimental.b8.TaskLayoutPolicy;
import org.junit.Test;
import static org.junit.Assert.*;
public class TaskLayoutPolicyTest {
 @Test public void innerAndCoverRespectEffectiveDensity(){
  assertTrue(TaskLayoutPolicy.matches(2448,1848,933,704,420));
  assertTrue(TaskLayoutPolicy.matches(1248,1972,475,751,420));
  assertTrue(TaskLayoutPolicy.matches(2448,1848,1088,821,360));
 }
 @Test public void staleOppositePanelCannotReveal(){
  assertFalse(TaskLayoutPolicy.matches(2448,1848,475,751,420));
  assertFalse(TaskLayoutPolicy.matches(1248,1972,933,704,420));
 }
 @Test public void rotationAndInsetsAllowedButUnknownRejected(){
  assertTrue(TaskLayoutPolicy.matches(1848,2448,704,933,420));
  assertTrue(TaskLayoutPolicy.matches(1248,1900,475,751,420));
  assertFalse(TaskLayoutPolicy.matches(2448,1848,0,0,420));
  assertFalse(TaskLayoutPolicy.matches(2448,1848,933,704,0));
 }
}
