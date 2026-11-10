package org.duofold.live;
import org.junit.Test;import static org.junit.Assert.*;
public class DisplayPowerEvidenceTest {
 @Test public void remappingDoesNotInventPowerChanges(){DisplayPowerEvidence p=new DisplayPowerEvidence();p.observe(1,"local:1",2,"local:2",1);assertTrue(p.observe(2,"local:2",1,"local:1",2).isEmpty());}
 @Test public void realOffOnCarriesReceiptSpan(){DisplayPowerEvidence p=new DisplayPowerEvidence();p.observe(1,"local:1",2,"local:2",2);assertTrue(p.observe(10,"local:1",1,"local:2",2).get(0).contains("OFF_OBSERVED"));assertTrue(p.observe(80,"local:1",2,"local:2",2).get(0).contains("offReceiptSpanMs=70"));}
 @Test public void missingIdentityBreaksContinuity(){DisplayPowerEvidence p=new DisplayPowerEvidence();p.observe(1,"local:1",2,"local:2",2);p.observe(2,"local:1",1,"local:2",2);p.observe(3,"unknown",1,"local:2",2);assertTrue(p.observe(4,"local:1",2,"local:2",2).isEmpty());}
 @Test public void duplicatesCannotProveBothOff(){DisplayPowerEvidence p=new DisplayPowerEvidence();assertTrue(p.observe(1,"local:1",1,"local:1",1).isEmpty());assertTrue(p.report().contains("both-OFF episodes=0"));}
 @Test public void bothOffIsDeduplicatedAndStaleSamplesIgnored(){DisplayPowerEvidence p=new DisplayPowerEvidence();assertEquals(1,p.observe(10,"local:1",1,"local:2",1).size());assertTrue(p.observe(20,"local:1",1,"local:2",1).isEmpty());assertTrue(p.observe(15,"local:1",2,"local:2",2).isEmpty());assertTrue(p.report().contains("both-OFF episodes=1"));}
 @Test public void dozeEndsOffSpanWithoutCountingItAsOn(){DisplayPowerEvidence p=new DisplayPowerEvidence();p.observe(1,"local:1",2,"local:2",2);p.observe(10,"local:1",1,"local:2",2);assertTrue(p.observe(20,"local:1",3,"local:2",2).get(0).contains("offReceiptSpanMs=10 exitState=3"));assertFalse(p.observe(30,"local:1",2,"local:2",2).get(0).contains("offReceiptSpanMs"));}
}
