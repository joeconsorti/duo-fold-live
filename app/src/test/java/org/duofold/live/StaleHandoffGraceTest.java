package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class StaleHandoffGraceTest {
 @Test public void reportedGapRetainsButExpires(){assertTrue(h(true,false,true,true,69,1887));assertFalse(h(true,false,true,true,69,3000));}
 @Test public void neverAcquiresOrOverridesShutdown(){assertFalse(h(false,false,true,true,69,1887));assertFalse(h(true,false,false,true,69,1887));assertFalse(h(true,false,true,false,69,1887));}
 @Test public void freshAndEndpointsUseNormalPolicy(){assertFalse(h(true,true,true,true,69,1887));assertFalse(h(true,false,true,true,0,1887));assertFalse(h(true,false,true,true,172,1887));assertFalse(h(true,false,true,true,Float.NaN,1887));}
 private boolean h(boolean o,boolean f,boolean i,boolean d,float a,long n){return StaleHandoffGrace.hold(o,f,i,d,a,172,1000,n);}
}
