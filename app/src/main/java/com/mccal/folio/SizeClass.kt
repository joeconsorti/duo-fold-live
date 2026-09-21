package com.mccal.folio

import android.content.res.Configuration
import android.util.DisplayMetrics

/**
 * How much to scale dp by to judge a size class at the phone's own screen density. Developer options' "Smallest
 * width" and Display size change how many dp a screen reports, but a phone-sized screen should still get the phone
 * layout: a Galaxy Z Fold8 cover set to 600dp was getting the unfolded one (bottom dock, wide margins).
 */
internal val Configuration.classScale: Float
    get() = classScale(densityDpi, DisplayMetrics.DENSITY_DEVICE_STABLE)

/** Regular size (the unfolded screen, tablets), judged at the phone's own density. */
internal fun Configuration.isRegular(): Boolean = isRegularSize(screenWidthDp.toFloat(), screenHeightDp.toFloat(), classScale)
