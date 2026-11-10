package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;

public class B8BufferCapacityTest {
    @Test public void repeatedPanelExchangesDoNotResizeEitherBuffer() {
        for (boolean coverFirst : new boolean[]{true, false}) {
            B8BufferCapacity c = new B8BufferCapacity();
            c.include(coverFirst ? 312 : 612, coverFirst ? 493 : 462);
            c.include(coverFirst ? 612 : 312, coverFirst ? 462 : 493);
            assertEquals(612, c.width()); assertEquals(493, c.height());
            for (int fold = 0; fold < 20; fold++) {
                assertFalse(c.include(312, 493)); assertFalse(c.include(612, 462));
            }
        }
    }
    @Test public void rotationOrHigherResolutionGrowsOnceWithoutShrinkingOnReturn() {
        B8BufferCapacity c = new B8BufferCapacity();
        c.include(612, 493);
        assertTrue(c.include(462, 612));
        assertEquals(612, c.width()); assertEquals(612, c.height());
        assertFalse(c.include(612, 493));
        assertTrue(c.include(1224, 924));
        assertEquals(1224, c.width()); assertEquals(924, c.height());
        assertFalse(c.include(312, 493));
    }
    @Test public void missingDisplayCannotPoisonCapacityOrMakeItShrink() {
        B8BufferCapacity c = new B8BufferCapacity();
        assertFalse(c.include(0, 493)); assertFalse(c.include(612, -1));
        assertEquals(0, c.width()); assertEquals(0, c.height());
        c.include(612, 493);
        assertFalse(c.include(-1, -1)); assertFalse(c.include(0, 0));
        assertEquals(612, c.width()); assertEquals(493, c.height());
        assertEquals(0, new B8BufferCapacity().width()); // no capacity retained after session disposal
    }
}
