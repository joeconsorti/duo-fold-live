package com.mccal.folio

import android.app.Activity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * The hinge as a reserved region, in window pixels. Present on a foldable's inner screen even when flat
 * ([active] false), which is enough to prefer even column counts; [active] only when partially folded.
 * [vertical] is a book-style fold; otherwise a laptop/tabletop fold.
 */
@Immutable
internal data class Hinge(val active: Boolean, val vertical: Boolean, val startPx: Int, val endPx: Int)

internal val LocalHinge = staticCompositionLocalOf<Hinge?> { null }

@Composable
internal fun rememberHinge(activity: Activity): Hinge? {
    val info by remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
        .collectAsStateWithLifecycle(initialValue = null)
    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull() ?: return null
    val vertical = fold.orientation == FoldingFeature.Orientation.VERTICAL
    return Hinge(fold.state == FoldingFeature.State.HALF_OPENED, vertical,
        if (vertical) fold.bounds.left else fold.bounds.top, if (vertical) fold.bounds.right else fold.bounds.bottom)
}

/** Grids on a screen with a hinge prefer an even number of columns, so no column sits on the fold. */
@Composable
internal fun evenColumnsOnHinge(columns: Int, min: Int): Int = evenColumns(columns, min, LocalHinge.current != null)

internal fun evenColumns(columns: Int, min: Int, hinge: Boolean): Int =
    if (hinge && columns % 2 == 1 && columns - 1 >= min) columns - 1 else columns

/** What an overlay is for, which decides where it goes when the device is partially folded. */
internal enum class FoldRole {
    /** Alerts and status: trailing half like a book (where they continue when closed), top half on a table (visible at a distance). */
    INFO,
    /** Sheets, menus and controls: trailing half like a book, bottom half on a table (a stable surface to tap). */
    CONTROLS,
}

/**
 * iPhone Duo-style displacement for overlays: flat, content uses the whole box; partially folded, it
 * springs into the region that suits its [role] instead of sitting on the curve. Continuous scrolling
 * content (lists, feeds) shouldn't use this; it adapts by scrolling.
 */
@Composable
internal fun FoldAvoidingBox(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.Center,
    role: FoldRole = FoldRole.CONTROLS, content: @Composable BoxScope.() -> Unit) {
    val hinge = LocalHinge.current?.takeIf { it.active }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gap = 12.dp
        val past = with(density) { (hinge?.endPx ?: 0).toDp() } + gap
        val before = with(density) { (hinge?.startPx ?: 0).toDp() } - gap
        val motion = spring<androidx.compose.ui.unit.Dp>(dampingRatio = .9f, stiffness = 380f)
        // Window coordinates: close enough for these full-window overlays.
        val start by animateDpAsState(if (hinge?.vertical == true) past.coerceAtMost(maxWidth / 2) else 0.dp, motion, label = "fold start")
        val top by animateDpAsState(if (hinge != null && !hinge.vertical && role == FoldRole.CONTROLS) past.coerceAtMost(maxHeight / 2) else 0.dp, motion, label = "fold top")
        val bottom by animateDpAsState(if (hinge != null && !hinge.vertical && role == FoldRole.INFO) (maxHeight - before).coerceIn(0.dp, maxHeight / 2) else 0.dp, motion, label = "fold bottom")
        Box(Modifier.fillMaxSize().padding(start = start, top = top, bottom = bottom), contentAlignment = contentAlignment, content = content)
    }
}
