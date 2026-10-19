package org.duofold.live

/** One clock for the renderer and its host; independent of sensor/frame rate. */
internal class InnerMotionReturn {
 var lastAmount=0f; private set
 private var started=-1L
 val pending get()=started>=0L
 fun observe(amount:Float){if(!pending)lastAmount=amount.coerceIn(0f,1f)}
 fun begin(now:Long):Boolean {
  if(pending || lastAmount<=.003f)return false
  started=now;return true
 }
 fun amount(now:Long):Float {
  if(!pending)return lastAmount
  val t=((now-started)/300f).coerceIn(0f,1f)
  return lastAmount*(1f-t*t*(3f-2f*t))
 }
 fun finished(now:Long)=pending && now-started>=300L
 fun cancel(now:Long){lastAmount=amount(now);started=-1L}
 fun clear(){lastAmount=0f;started=-1L}
}
