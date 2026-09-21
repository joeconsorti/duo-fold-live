package com.mccal.folio
/** Submitted frames, not a measurement of photons or guaranteed panel FPS. */
internal object FrameTelemetry {
 private class Panel {var start=0L;var last=0L;var count=0;var total=0L;var summary="No active sample"}
 private val panels=arrayOf(Panel(),Panel())
 fun record(inner:Boolean,now:Long,cost:Long,requested:Float){
  val p=panels[if(inner)1 else 0]
  if(p.start==0L || now-p.last>250_000_000L){p.start=now;p.count=0;p.total=0}
  p.last=now;p.count++;p.total+=cost
  if(now-p.start>=1_000_000_000L){p.summary="%.1f submitted fps; %.2f ms draw; %.0f Hz requested".format((p.count-1)*1e9/(now-p.start),p.total/1e6/p.count,requested);p.start=now;p.count=0;p.total=0}
 }
 fun report()="Last active renderer samples (not guaranteed display FPS):\nCover: ${panels[0].summary}\nInner: ${panels[1].summary}"
}
