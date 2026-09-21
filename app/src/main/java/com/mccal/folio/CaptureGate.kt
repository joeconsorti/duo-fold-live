package com.mccal.folio
/** Rate limit from actual API submission, not from scheduling the two-frame wait. */
internal class CaptureGate {
 var intervalMs=500L; private set
 private var nextAt=0L
 fun delay(now:Long)=(nextAt-now).coerceAtLeast(0L)
 fun submitted(now:Long){nextAt=now+intervalMs}
 fun succeeded()=Unit // Retain the interval Samsung has accepted for this service lifetime.
 fun failed(now:Long,code:Int){
  if(code==3)intervalMs=(intervalMs*2).coerceAtMost(4000L)
  nextAt=maxOf(nextAt,now+if(code==3)intervalMs else 1500L)
 }
}
