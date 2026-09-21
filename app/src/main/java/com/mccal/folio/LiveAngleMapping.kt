package com.mccal.folio

/** Linear phase into Folio’s shader, which applies smoothstep; physical 180 degrees = flat. */
internal object LiveAngleMapping {
    fun strength(angle: Float, expanded: Boolean): Float {
        val phase = (if (expanded) (180f - angle) / 90f else angle / 90f).coerceIn(0f, 1f)
        return phase
    }
}
