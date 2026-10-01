package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class HandoffTracePolicyTest {
 @Test public void oneStartAndOneTailPerMotion(){HandoffTracePolicy p=new HandoffTracePolicy();assertEquals("",p.update(0,true,true,172,0));assertTrue(p.update(3,true,true,172,10).startsWith("MOTION"));assertEquals("",p.update(85,true,true,172,20));assertTrue(p.update(178,true,true,172,30).startsWith("ENDPOINT"));assertEquals("",p.update(178,true,true,172,40));assertTrue(p.update(160,true,true,172,50).startsWith("MOTION"));}
 @Test public void lingerAndLostHostCannotStartInfiniteTraces(){HandoffTracePolicy p=new HandoffTracePolicy();p.update(80,true,true,172,0);assertTrue(p.update(80,true,true,172,30000).startsWith("STOP"));assertEquals("",p.update(80,true,true,172,31000));p.update(0,true,true,172,32000);assertTrue(p.update(5,true,true,172,33000).startsWith("MOTION"));assertTrue(p.update(6,false,true,172,33010).startsWith("STOP"));assertEquals("",p.update(8,true,true,172,33020));}
 @Test public void disabledAndInvalidAnglesDoNotArm(){HandoffTracePolicy p=new HandoffTracePolicy();assertEquals("",p.update(50,true,false,172,1));assertEquals("",p.update(Float.NaN,true,true,172,2));}
}
