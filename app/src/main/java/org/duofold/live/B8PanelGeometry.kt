package org.duofold.live

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Point
import android.view.Display

/** Live geometry is measured on every call: identity/mode callbacks can precede final bounds.
 * Saved dimensions only pre-size panels that are not ON; layout and identity still gate commits. */
internal object B8PanelGeometry {
 private var prefs:SharedPreferences?=null
 fun initialize(context:Context){
  if(prefs!=null)return
  prefs=context.applicationContext.getSharedPreferences("b8_panel_geometry",0)
 }
 fun size(display:Display):Point {
  val measured=Point();display.getRealSize(measured)
  val physical=PanelIdentity.identity(display)
  if(!PanelIdentityPolicy.known(physical))return measured
  val mode=display.mode
  val key="$physical:${mode.physicalWidth}x${mode.physicalHeight}:${display.rotation}"
  // Never substitute a prior observation for ON geometry, even with the same
  // key. The WindowContext can finish resizing without another display event.
  if(display.state==Display.STATE_ON){
   if(measured.x>0&&measured.y>0){
    val value="${measured.x},${measured.y}"
    if(prefs?.getString(key,null)!=value)prefs?.edit()?.putString(key,value)?.apply()
   }
   return measured
  }
  // Saved geometry is exclusively a prewarm hint while this panel is not ON.
  val stored=prefs?.getString(key,null)?.split(',')
  val saved=stored?.takeIf{it.size==2}?.let{val w=it[0].toIntOrNull();val h=it[1].toIntOrNull();if(w!=null&&h!=null&&w>0&&h>0)Point(w,h) else null}
  return saved?:measured
 }
}
