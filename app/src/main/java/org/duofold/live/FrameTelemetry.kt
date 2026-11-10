package org.duofold.live
/** Live primary-surface submissions only. Duo has separate per-session buffer/callback counters. */
internal object FrameTelemetry {
 private class Panel {
  var owner:java.lang.ref.WeakReference<Any>?=null;var start=0L;var last=0L;var intervals=0;var total=0L
  var sampleAt=0L;var summary="No active sample"
 }
 private val panels=arrayOf(Panel(),Panel())
 @Synchronized fun record(inner:Boolean,owner:Any,now:Long,cost:Long,requested:Float){
  val p=panels[if(inner)1 else 0]
  if(p.owner?.get()!==owner || p.start==0L || now<=p.last || now-p.last>250_000_000L){
   p.owner=java.lang.ref.WeakReference(owner);p.start=now;p.last=now;p.intervals=0;p.total=0;return
  }
  p.last=now;p.intervals++;p.total+=cost
  if(now-p.start>=1_000_000_000L){
   p.summary=java.lang.String.format(java.util.Locale.ROOT,
    "%.1f submitted fps; %.2f ms draw; %.0f Hz requested; %d intervals / %d ms",
    p.intervals*1e9/(now-p.start),p.total/1e6/p.intervals,requested,p.intervals,(now-p.start)/1_000_000)
   p.sampleAt=now;p.start=now;p.intervals=0;p.total=0
  }
 }
 @Synchronized fun report(now:Long):String {
  fun sample(p:Panel)=p.summary+if(p.sampleAt>0)"; sampleEndedMs=${p.sampleAt/1_000_000}; ageMs=${((now-p.sampleAt)/1_000_000).coerceAtLeast(0)}" else ""
  return "Live Screen primary-surface samples (rolling windows, may precede mode switch; not panel FPS):\n"+
   "Cover: ${sample(panels[0])}\nInner: ${sample(panels[1])}\n"+
   "Duo: use the per-session submitted/presentation-callback FPS above; embedded painter calls are excluded here."
 }
}
