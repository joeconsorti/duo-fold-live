package com.mccal.folio

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/** Opt-in gesture diagnostics. Enable temporarily with: setprop log.tag.DuoMotion DEBUG */
internal object DuoMotionTrace {
    private const val TAG = "DuoMotion"
    private val nextGesture = AtomicLong()

    val enabled: Boolean get() = Log.isLoggable(TAG, Log.DEBUG)

    fun event(name: String, details: String) {
        if (enabled) Log.d(TAG, "$name $details")
    }

    fun begin(anchor: Int, position: Float, x: Float, y: Float): Session? {
        if (!enabled) return null
        return Session(nextGesture.incrementAndGet()).also {
            it.write("down", "anchor=$anchor position=$position x=$x y=$y")
        }
    }

    class Session internal constructor(private val id: Long) {
        fun slop(dx: Float, dy: Float, position: Float) =
            write("slop", "dx=$dx dy=$dy position=$position")

        fun release(position: Float, velocity: Float, target: Int, canceled: Boolean) =
            write("release", "position=$position velocity=$velocity target=$target canceled=$canceled")

        fun cancel(reason: String, position: Float) =
            write("input_cancel", "reason=$reason position=$position")

        fun terminal(type: String, dx: Float, dy: Float, eventTime: Long, observedTime: Long,
            historical: Int, consumed: Boolean, position: Float) =
            write("terminal", "type=$type dx=$dx dy=$dy eventTime=$eventTime observedTime=$observedTime " +
                "lag=${observedTime - eventTime} historical=$historical consumed=$consumed position=$position")

        fun motionCanceled(position: Float, offset: Float) =
            write("motion_canceled", "position=$position offset=$offset")

        fun motionCompleted(position: Float, offset: Float) =
            write("motion_completed", "position=$position offset=$offset")

        internal fun write(name: String, details: String) {
            Log.d(TAG, "gesture=$id $name $details")
        }
    }
}
