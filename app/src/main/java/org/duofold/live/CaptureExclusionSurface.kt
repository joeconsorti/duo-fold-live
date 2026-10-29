package org.duofold.live

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Static transparent handle for the first B8 capture. No shader or frame loop. */
internal class CaptureExclusionSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
 init {
  setZOrderOnTop(true)
  holder.setFormat(PixelFormat.TRANSLUCENT)
  holder.setFixedSize(1, 1)
  holder.addCallback(this)
  importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
 }
 override fun surfaceCreated(holder: SurfaceHolder) {
  GlassFrames.surface(this, surfaceControl)
  runCatching { holder.surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
 }
 override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
  GlassFrames.surface(this, surfaceControl)
  runCatching {
   val canvas = holder.lockHardwareCanvas()
   try { canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) }
   finally { holder.unlockCanvasAndPost(canvas) }
  }
 }
 override fun surfaceDestroyed(holder: SurfaceHolder) { GlassFrames.surface(this, null) }
}
