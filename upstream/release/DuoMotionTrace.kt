package com.mccal.folio

/** Release builds contain no active motion diagnostics. */
internal object DuoMotionTrace {
    const val enabled = false
    fun event(name: String, details: String) = Unit
    fun begin(anchor: Int, position: Float, x: Float, y: Float): Session? = null

    class Session {
        fun slop(dx: Float, dy: Float, position: Float) = Unit
        fun release(position: Float, velocity: Float, target: Int, canceled: Boolean) = Unit
        fun cancel(reason: String, position: Float) = Unit
        fun terminal(type: String, dx: Float, dy: Float, eventTime: Long, observedTime: Long,
            historical: Int, consumed: Boolean, position: Float) = Unit
        fun motionCanceled(position: Float, offset: Float) = Unit
        fun motionCompleted(position: Float, offset: Float) = Unit
    }
}
