package com.mccal.folio

import androidx.compose.runtime.staticCompositionLocalOf

/** Hosting notifications only. All motion decisions remain in Folio's FoldTimeline. */
internal interface StandaloneFoldHost {
    fun onMovement()
    fun onFrame(active: Boolean, strength: Float)
}
internal val LocalStandaloneFoldHost = staticCompositionLocalOf<StandaloneFoldHost?> { null }

/** Same FoldingFeature mapping as rememberHinge, for a non-activity window context. */
@androidx.compose.runtime.Composable
internal fun rememberWindowHinge(context: android.content.Context): Hinge? {
    val state = androidx.compose.runtime.produceState<Hinge?>(initialValue = null, context) {
        try {
            androidx.window.layout.WindowInfoTracker.getOrCreate(context).windowLayoutInfo(context).collect { info ->
                val fold = info.displayFeatures.filterIsInstance<androidx.window.layout.FoldingFeature>().firstOrNull()
                value = fold?.let {
                    val vertical = it.orientation == androidx.window.layout.FoldingFeature.Orientation.VERTICAL
                    Hinge(it.state == androidx.window.layout.FoldingFeature.State.HALF_OPENED, vertical,
                        if (vertical) it.bounds.left else it.bounds.top, if (vertical) it.bounds.right else it.bounds.bottom)
                }
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { value = null } // Folio's existing centered-hinge fallback.
    }
    return state.value
}
