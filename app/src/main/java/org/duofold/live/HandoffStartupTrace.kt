package org.duofold.live

import android.os.SystemClock
import android.view.Display

/** Passive bounded observations. Display ON and transaction commit are not optical evidence. */
internal object HandoffStartupTrace {
 private data class Episode(val id:Int,val physical:String,val start:Long,val events:MutableList<String> = mutableListOf(),val seen:MutableSet<String> = mutableSetOf())
 private val history=ArrayDeque<Episode>()
 private var current:Episode?=null
 private var physical=""
 private var serial=0
 private var snapshot=""
 @Synchronized fun observe(d:Display){
  val id=PanelIdentity.identity(d)
  if(!PanelIdentityPolicy.known(id))return
  val mode=d.mode
  if(id!=physical){
   physical=id
   current=Episode(++serial,id,SystemClock.elapsedRealtime()).also{history.addLast(it)}
   while(history.size>12)history.removeFirst()
   snapshot=""
   event("PHYSICAL_PRIMARY", "identity=$id; mode=${mode.physicalWidth}x${mode.physicalHeight}; initial=${serial==1}")
  }
  val next="state=${d.state}; mode=${mode.physicalWidth}x${mode.physicalHeight}; rotation=${d.rotation}"
  if(next!=snapshot){snapshot=next;event("DISPLAY",next,false)}
 }
 @Synchronized fun matches(id:Int,f:GlassFrame)=current?.let{it.id==id && it.physical==f.physicalId}==true
 @Synchronized fun token()=current?.id ?: 0
 @Synchronized fun event(stage:String,note:String,once:Boolean=true,id:Int=token()){
  val e=history.firstOrNull{it.id==id}?:return
  if(SystemClock.elapsedRealtime()-e.start>2500 || e.events.size>=32)return
  if(once && !e.seen.add(stage))return
  val now=SystemClock.elapsedRealtime()
  e.events.add("$now +${now-e.start}ms $stage $note")
 }
 @Synchronized fun capture(f:GlassFrame){
  val e=current?:return
  if(f.physicalId!=e.physical){event("CAPTURE_OTHER","physical=${f.physicalId}; size=${f.width}x${f.height}");return}
  event("CAPTURE_RECEIVED","physical=${f.physicalId}; stamp=${f.stamp}; age=${SystemClock.elapsedRealtime()-f.stamp}ms; size=${f.width}x${f.height}")
 }
 @Synchronized fun report()="Handoff startup diagnostic (physical identity; both directions; initial attachment labelled):\nSoftware callback times only, not visible pixels. Missing GLASS_COMMIT means unmeasured, never zero latency.\n"+history.joinToString("\n"){e->"episode=${e.id} physical=${e.physical}\n"+e.events.joinToString("\n")}
}
