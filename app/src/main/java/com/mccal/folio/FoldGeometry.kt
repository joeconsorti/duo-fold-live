package com.mccal.folio

internal data class FoldGeometry(val horizontal: Boolean, val hingePx: Float, val movingAfterHinge: Boolean)

/**
 * In the natural orientation (unfolded landscape; cover portrait) the moving half, or the cover's hinge edge, is
 * on the left. Display rotation moves that edge: 90° to the bottom, 180° to the right, 270° to the top. A real
 * hinge from WindowManager gives the exact position; otherwise it's the middle.
 */
internal fun foldGeometry(rotation: Int, hinge: Hinge?, width: Float, height: Float): FoldGeometry {
    val horizontal = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270
    val after = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_180
    val middle = if (horizontal) height / 2f else width / 2f
    val position = hinge?.takeIf { it.vertical != horizontal }?.let { (it.startPx + it.endPx) / 2f } ?: middle
    return FoldGeometry(horizontal, position, after)
}
