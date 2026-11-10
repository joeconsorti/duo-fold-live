package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class B8SnapshotPolicyTest {
 private static final String INNER="local:4630947004648141459", COVER="local:4630947123231501204";
 @Test public void captureObservationBelongsToCurrentCheckAndMustStillBeFresh(){
  assertTrue(B8SnapshotPolicy.currentObservation(3,3,100,850,1100));
  assertFalse(B8SnapshotPolicy.currentObservation(3,4,100,850,1100));
  assertFalse(B8SnapshotPolicy.currentObservation(0,0,100,200,250));
  assertFalse(B8SnapshotPolicy.currentObservation(3,3,100,851,1100));
  assertFalse(B8SnapshotPolicy.currentObservation(3,3,100,850,1101));
  assertFalse(B8SnapshotPolicy.currentObservation(3,3,100,850,849));
  assertFalse(B8SnapshotPolicy.currentObservation(3,3,100,99,101));
  assertFalse(B8SnapshotPolicy.currentObservation(3,3,100,0,101));
 }
 @Test public void openingRetainsInnerImageAcrossLogicalRemap(){
  assertTrue(B8SnapshotPolicy.matches(INNER,2448,1848,0,INNER,2448,1848,0));
 }
 @Test public void closingRetainsCoverImageAcrossLogicalRemap(){
  assertTrue(B8SnapshotPolicy.matches(COVER,1248,1972,0,COVER,1248,1972,0));
 }
 @Test public void wrongPanelCannotAcknowledgeEvenWithMatchingDimensions(){
  assertFalse(B8SnapshotPolicy.matches(INNER,2448,1848,0,COVER,2448,1848,0));
 }
 @Test public void rotatedOrResizedImageCannotAcknowledge(){
  assertFalse(B8SnapshotPolicy.matches(INNER,1848,2448,1,INNER,2448,1848,0));
  assertFalse(B8SnapshotPolicy.matches(INNER,2448,1848,2,INNER,2448,1848,0));
  assertFalse(B8SnapshotPolicy.matches(COVER,1248,1972,0,COVER,1224,1972,0));
  assertTrue(B8SnapshotPolicy.matches(INNER,1848,2448,1,INNER,1848,2448,1));
 }
 @Test public void incompleteIdentityOrGeometryCannotAcknowledge(){
  assertFalse(B8SnapshotPolicy.matches("",2448,1848,0,"",2448,1848,0));
  assertFalse(B8SnapshotPolicy.matches(INNER,0,1848,0,INNER,0,1848,0));
  assertFalse(B8SnapshotPolicy.matches(INNER,2448,1848,-1,INNER,2448,1848,-1));
 }
}
