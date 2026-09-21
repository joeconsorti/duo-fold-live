package com.mccal.folio;
import org.junit.Test;
import static org.junit.Assert.*;
public class FreezePolicyTest {
 @Test public void coverCaptureCannotBeUsedForInner(){assertTrue(FreezePolicy.accepts(false,1248,1972,1000,1100));assertFalse(FreezePolicy.accepts(true,1248,1972,1000,1100));}
 @Test public void innerCaptureCannotBeUsedForCover(){assertTrue(FreezePolicy.accepts(true,2448,1848,1000,1100));assertFalse(FreezePolicy.accepts(false,2448,1848,1000,1100));}
 @Test public void rotationPreservesPhysicalPanelType(){assertTrue(FreezePolicy.accepts(true,1848,2448,1000,1100));assertTrue(FreezePolicy.accepts(false,1972,1248,1000,1100));}
 @Test public void staleOrInvalidCaptureCannotArmSwitch(){assertFalse(FreezePolicy.accepts(false,1248,1972,1000,1501));assertFalse(FreezePolicy.accepts(false,1248,1972,1100,1000));assertFalse(FreezePolicy.accepts(false,0,1972,1000,1100));}
 @Test public void switchRequiresCaptureFromCurrentPrimary(){assertFalse(FreezePolicy.canSwitch(true,-1));assertFalse(FreezePolicy.canSwitch(false,-1));assertFalse(FreezePolicy.canSwitch(true,0));assertFalse(FreezePolicy.canSwitch(false,1));assertTrue(FreezePolicy.canSwitch(true,1));assertTrue(FreezePolicy.canSwitch(false,0));}
 @Test public void incomingPrimaryDependsOnOutgoingPanel(){assertTrue(FreezePolicy.targetInner(0));assertFalse(FreezePolicy.targetInner(1));}
 @Test(expected=IllegalArgumentException.class) public void noDefaultDirectionWithoutCapture(){FreezePolicy.targetInner(-1);}
}
