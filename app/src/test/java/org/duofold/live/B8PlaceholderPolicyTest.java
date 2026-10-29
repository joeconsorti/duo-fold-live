package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class B8PlaceholderPolicyTest {
 @Test public void innerPlaceholderCoversTallCover(){float s=B8PlaceholderPolicy.scale(2448,1848,1248,1972,true);assertTrue(2448*s>=1248);assertEquals(1972,1848*s,.01f);}
 @Test public void coverPlaceholderCoversWideInner(){float s=B8PlaceholderPolicy.scale(1248,1972,2448,1848,true);assertEquals(2448,1248*s,.01f);assertTrue(1972*s>=1848);}
 @Test public void nativeImageDoesNotCrop(){assertEquals(1f,B8PlaceholderPolicy.scale(1248,1972,1248,1972,false),0f);assertEquals(1248f/2448,B8PlaceholderPolicy.scale(2448,1848,1248,1972,false),0f);}
}
