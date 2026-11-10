package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class WorkspaceBufferGeometryTest {
 @Test public void insetPaneAndFullWindowKeepExactPlacement(){assertTrue(WorkspaceBufferGeometry.exact(1000,800,20,40,1020,840,2448,1848));assertTrue(WorkspaceBufferGeometry.exact(1248,1972,0,0,1248,1972,1248,1972));}
 @Test public void rejectsClippedOrStretchedWindow(){assertFalse(WorkspaceBufferGeometry.exact(2448,1848,0,0,1200,1848,2448,1848));assertFalse(WorkspaceBufferGeometry.exact(1200,1848,-1,0,1199,1848,2448,1848));assertFalse(WorkspaceBufferGeometry.exact(1248,1972,0,0,1248,1972,1248,1848));}
 @Test public void innerWorkspaceCoversEntireCoverDuringProjection(){float[] p=HomeProjectionPolicy.layout(2448,1848,1248,1972);assertTrue(p[1]<=0);assertTrue(p[2]<=0);assertTrue(p[1]+2448*p[0]>=1248);assertTrue(p[2]+1848*p[0]>=1972);}
 @Test public void coverWorkspacePreservesBothPanesBeforeNativeInnerArrives(){float[] p=HomeProjectionPolicy.layout(1248,1972,2448,1848);assertEquals(1848f,p[0]*1972,.001f);assertTrue(p[1]>=0);assertEquals(2448f,p[1]+p[0]*1248,.001f);}
}
