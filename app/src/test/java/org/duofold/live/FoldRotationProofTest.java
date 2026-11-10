package org.duofold.live;

import org.junit.Test;
import static org.junit.Assert.*;

public class FoldRotationProofTest {
 @Test public void bindsSessionMappingSavedAnglesAndObservationTime(){
  FoldRotationProof p=new FoldRotationProof(100,110,"inner",3,"cover",1);
  assertTrue(p.matches(100,360,"inner",3,"cover",1));
  assertFalse(p.matches(99,110,"inner",3,"cover",1));
  assertFalse(p.matches(100,110,"cover",1,"inner",3));
  assertFalse(p.matches(100,110,"inner",0,"cover",1));
  assertFalse(p.matches(100,110,"inner",3,"cover",0));
  assertFalse(p.matches(100,361,"inner",3,"cover",1));
  assertFalse(p.matches(100,105,"inner",3,"cover",1));
 }
}
