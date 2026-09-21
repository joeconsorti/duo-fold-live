package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldDisplacementTest {
    private val g = homeGeometry(704f, 930f, LayoutPreset(), true)

    @Test fun `rows in the fold move below it and widgets are never split`() {
        val cells = HomeCellLayout.forPage(g, listOf(0 to 2))
        val gridTop = 100f
        // Hinge across row 3.
        val hingeTop = gridTop + cells.y(3) + 20f
        val (row, shift) = foldDisplacement(cells, GRID_ROWS, gridTop, hingeTop, hingeTop + 30f, listOf(0 to 2))!!
        assertEquals(3, row)
        assertTrue(gridTop + cells.y(3) + shift >= hingeTop + 30f)
        // Hinge through the widget row: the move starts at the widget's first row.
        val widgetHinge = gridTop + cells.y(1) + 5f
        assertEquals(0, foldDisplacement(cells, GRID_ROWS, gridTop, widgetHinge, widgetHinge + 30f, listOf(0 to 2))!!.first)
        // Hinge below the page, or a two-column page: nothing moves.
        assertNull(foldDisplacement(cells, GRID_ROWS, gridTop, gridTop + 2000f, gridTop + 2030f, listOf(0 to 2)))
        val wide = homeGeometry(751f, 459f, LayoutPreset(), true)
        assertNull(foldDisplacement(HomeCellLayout.forPage(wide, emptyList()), GRID_ROWS, 0f, 100f, 130f, emptyList()))
    }
}
