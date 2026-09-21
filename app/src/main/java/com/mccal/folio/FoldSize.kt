package com.mccal.folio

/** Shortest height that still counts as regular size (the unfolded screen in either rotation; the cover's landscape is ~475dp). */
const val REGULAR_MIN_HEIGHT_DP = 560f

/**
 * Regular size class in both dimensions, like iOS size classes: never a device, display or orientation check.
 * [classScale] converts to dp at the phone's own density (see [classScale]), so a changed display size can't turn a
 * phone-sized screen into a tablet one.
 */
fun isRegularSize(widthDp: Float, heightDp: Float, classScale: Float = 1f) =
    widthDp * classScale >= 600f && heightDp * classScale >= REGULAR_MIN_HEIGHT_DP

/** Current density over the device's own ([stableDpi]); 1 when either is unknown. */
fun classScale(densityDpi: Int, stableDpi: Int): Float =
    if (densityDpi <= 0 || stableDpi <= 0) 1f else densityDpi.toFloat() / stableDpi

