package com.mccal.folio

import android.content.Intent

/** Debug source set supplies the opt-in implementation. */
internal object FoldRenderExperiment {
    fun attach(activity: MainActivity) = Unit
    fun onNewIntent(activity: MainActivity, intent: Intent) = Unit
}
