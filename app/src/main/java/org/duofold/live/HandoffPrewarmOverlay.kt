package org.duofold.live

import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Transparent destination window attached before the developer concurrent-state request. */
internal class HandoffPrewarmOverlay(service:StandaloneService,display:Display){
 private val context=service.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null)
 private val wm=context.getSystemService(WindowManager::class.java)
 private val view=View(context).apply{
  setBackgroundColor(Color.TRANSPARENT)
  importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
 }
 var attached=false;private set
 fun show(){
  if(attached)return
  val p=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT)
  p.gravity=Gravity.TOP or Gravity.LEFT
  p.setFitInsetsTypes(0)
  p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
  p.title="Duo live handoff prewarm"
  wm.addView(view,p);attached=true
 }
 fun dismiss(){
  if(attached)runCatching{wm.removeViewImmediate(view)}
  attached=false
 }
}
