package org.duofold.live

import android.os.SystemClock
import android.graphics.Point
import android.view.Display

/** Temporary projection of recent outgoing content, never destination readiness. Main thread. */
internal object CoverBridge {
 private var recent:GlassFrame?=null
 private var held:GlassFrame?=null
 private var target=""
 private var started=0L
 fun remember(f:GlassFrame){recent=f}
 fun clear(){recent=null;held=null;target="";started=0L}
 fun prepare(d:Display){
  held=null;started=0L;target=""
  val m=d.mode
  if(minOf(m.physicalWidth,m.physicalHeight).toFloat()/maxOf(m.physicalWidth,m.physicalHeight)>.7f)return
  val f=recent?:return
  val id=identity(d);val now=SystemClock.elapsedRealtime()
  if(id=="unknown" || f.physicalId==id || f.physicalId=="unknown" || f.rotation!=d.rotation || !CoverBridgePolicy.freshSource(now,f.stamp))return
  if(minOf(f.width,f.height).toFloat()/maxOf(f.width,f.height)<=.7f)return
  held=f;target=id;started=now
  HandoffStartupTrace.event("BRIDGE_PREPARED","outgoing=${f.physicalId}; sourceAge=${now-f.stamp}ms; maxHold=600ms; projection, not cover capture")
 }
 fun forDisplay(d:Display?):GlassFrame?{
  if(d==null || identity(d)!=target || !CoverBridgePolicy.active(SystemClock.elapsedRealtime(),started)){held=null;return null}
  return held?.takeIf{it.rotation==d.rotation}
 }
 fun identity(d:Display?)=PanelIdentity.identity(d)
 fun size(d:Display?,point:Point){
  if(d==null)return
  d.getRealSize(point)
  val m=d.mode;val v=LivePanelGeometry.resolve(m.physicalWidth,m.physicalHeight,d.rotation,point.x,point.y)
  point.set(v[0],v[1])
 }
}
