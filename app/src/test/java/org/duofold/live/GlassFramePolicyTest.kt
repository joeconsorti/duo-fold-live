package org.duofold.live
import org.junit.Assert.*
import org.junit.Test
class GlassFramePolicyTest {
 @Test fun freshMatchingPanelAccepted(){assertTrue(GlassFramePolicy.usable(1000,1350,1248,1972,1248,1972))}
 @Test fun staleFrameCannotPersist(){assertFalse(GlassFramePolicy.usable(1000,1351,1248,1972,1248,1972))}
 @Test fun coverFrameRejectedAfterInnerMapping(){assertFalse(GlassFramePolicy.usable(1000,1100,1248,1972,2448,1848))}
 @Test fun rotationAndFutureTimestampsRejected(){assertFalse(GlassFramePolicy.usable(1000,1100,1248,1972,1972,1248));assertFalse(GlassFramePolicy.usable(1100,1000,1248,1972,1248,1972))}
}
