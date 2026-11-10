package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class PanelIdentityPolicyTest {
 @Test public void validFreshMapping(){assertTrue(PanelIdentityPolicy.usable("local:123",32,1248,1972,0,1248,1972,0));}
 @Test public void unknownIsNeverIdentity(){for(String s:new String[]{null,"","null","unknown","unavailable"})assertFalse(PanelIdentityPolicy.usable(s,0,1,2,0,1,2,0));}
 @Test public void staleAndFutureRejected(){for(long age:new long[]{-1,251,1000})assertFalse(PanelIdentityPolicy.usable("local:123",age,1,2,0,1,2,0));}
 @Test public void remapGeometryRejected(){assertFalse(PanelIdentityPolicy.usable("local:123",1,1248,1972,0,2448,1848,0));assertFalse(PanelIdentityPolicy.usable("local:123",1,1,2,0,1,2,1));}
}
