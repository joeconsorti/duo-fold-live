package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class DuoFrameRateTest {
 @Test public void insufficientSamplesAreNotZeroFps(){DuoFrameRate r=new DuoFrameRate();assertTrue(r.report().contains("unmeasured"));r.submitted(100);r.submitted(100);assertFalse(r.report().contains("Infinity"));assertTrue(r.report().contains("unmeasured"));}
 @Test public void submissionAndCompletionRatesAreIndependent(){DuoFrameRate r=new DuoFrameRate();for(int i=0;i<61;i++)r.submitted(100+i*1000/60);for(int i=0;i<31;i++)r.completed(150+i*1000/30);assertTrue(r.report().contains("submitted=60.0 FPS"));assertTrue(r.report().contains("presentation-callback=30.0 FPS"));}
 @Test public void stallsStayInRateAndOtherSessionsStartEmpty(){DuoFrameRate r=new DuoFrameRate();r.submitted(100);r.submitted(120);r.submitted(1100);assertTrue(r.report().contains("2.0 FPS (3 frames / 1000 ms"));assertTrue(new DuoFrameRate().report().contains("unmeasured"));}
 @Test public void clockRegressionDoesNotCorruptRate(){DuoFrameRate r=new DuoFrameRate();r.completed(100);r.completed(50);r.completed(200);assertTrue(r.report().contains("10.0 FPS (2 frames / 100 ms"));}
}
