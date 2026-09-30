package org.duofold.live

import android.app.Dialog
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.view.*

/** Window-first activation anchor only. Incoming content is never a screenshot.
 * Alpha zero keeps this attached window invisible and permits touch-through. */
internal class ScreenshotStartupWindow(context:Context,display:Display){
 private val ctx=context.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null)
 private val dialog=Dialog(ctx)
 private val view=View(ctx)
 var draws=0;private set
 val attached:Boolean get()=dialog.isShowing&&view.isAttachedToWindow
 init{
  view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  view.viewTreeObserver.addOnDrawListener{draws++}
  dialog.window?.apply{
   setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
   setFormat(PixelFormat.TRANSLUCENT)
   setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
   clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
   addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
   attributes=attributes.apply{alpha=0f;dimAmount=0f;setFitInsetsTypes(0);layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;title="Duo incoming live-content diagnostic anchor"}
  }
  dialog.setContentView(view)
 }
 fun show(){dialog.show();dialog.window?.setLayout(-1,-1)}
 fun close(){runCatching{dialog.dismiss()}}
}
