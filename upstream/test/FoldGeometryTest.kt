package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldGeometryTest {
    @Test fun `moving half follows display rotation and the real hinge`() {
        // Unfolded landscape: hinge runs top to bottom, the left half moves.
        val natural = foldGeometry(0, null, 2448f, 1848f)
        assertFalse(natural.horizontal); assertFalse(natural.movingAfterHinge); assertEquals(1224f, natural.hingePx, .01f)
        // Upside down: the right half moves.
        assertTrue(foldGeometry(2, null, 2448f, 1848f).movingAfterHinge)
        // Portrait: the hinge runs side to side; 90° puts the moving half at the bottom, 270° at the top.
        val portrait = foldGeometry(1, null, 1848f, 2448f)
        assertTrue(portrait.horizontal); assertTrue(portrait.movingAfterHinge); assertEquals(1224f, portrait.hingePx, .01f)
        assertFalse(foldGeometry(3, null, 1848f, 2448f).movingAfterHinge)
        // A reported hinge wins over the middle, but only when it runs the same way.
        assertEquals(1230f, foldGeometry(0, Hinge(false, true, 1220, 1240), 2448f, 1848f).hingePx, .01f)
        assertEquals(1224f, foldGeometry(1, Hinge(false, true, 1220, 1240), 1848f, 2448f).hingePx, .01f)
    }
}
