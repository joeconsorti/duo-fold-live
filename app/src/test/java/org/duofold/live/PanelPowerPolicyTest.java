package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class PanelPowerPolicyTest {
 @Test public void expiresEvenIfLeaseContinues(){assertTrue(PanelPowerPolicy.allowed(1649,1000,1649,true,true));assertFalse(PanelPowerPolicy.allowed(1650,1000,1650,true,true));}
 @Test public void staleLeaseCannotKeepPanelPowered(){assertTrue(PanelPowerPolicy.allowed(1249,1000,1000,true,true));assertFalse(PanelPowerPolicy.allowed(1250,1000,1000,true,true));}
 @Test public void lockOrDisableEndsExperiment(){assertFalse(PanelPowerPolicy.allowed(1100,1000,1100,false,true));assertFalse(PanelPowerPolicy.allowed(1100,1000,1100,true,false));}
 @Test public void invalidSessionCannotRun(){assertFalse(PanelPowerPolicy.allowed(1000,0,1000,true,true));assertFalse(PanelPowerPolicy.allowed(900,1000,900,true,true));assertFalse(PanelPowerPolicy.allowed(1100,1000,1200,true,true));}
 @Test public void restoreUsesCurrentSystemState(){assertEquals(0,PanelPowerPolicy.restoreMode(1));assertEquals(2,PanelPowerPolicy.restoreMode(2));assertEquals(1,PanelPowerPolicy.restoreMode(3));assertEquals(3,PanelPowerPolicy.restoreMode(4));assertEquals(-1,PanelPowerPolicy.restoreMode(0));}
}
