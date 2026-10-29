package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class B8SnapshotPolicyTest {
 private static final String INNER="local:test-inner", COVER="local:test-cover";
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
