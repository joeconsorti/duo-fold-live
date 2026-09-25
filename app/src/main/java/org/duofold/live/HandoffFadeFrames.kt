package org.duofold.live
import android.os.Parcel
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
/** At most one Binder call and one replacement notification; never blocks rendering. */
internal object HandoffFadeFrames {
 private data class Notice(val inner:Boolean,val whenMs:Long,val kind:Int,val captured:Long)
 private val worker=Executors.newSingleThreadExecutor()
 private val pending=AtomicBoolean()
 private val latest=AtomicReference<Notice?>()
 private var lastUi=0L
 fun drawn(inner:Boolean){
  val now=SystemClock.elapsedRealtime()
  if(now-lastUi<50)return
  lastUi=now;offer(Notice(inner,now,HandoffFadePolicy.UI_DRAW,-1))
 }
 fun committed(inner:Boolean,captured:Long,endpoint:Boolean){
  offer(Notice(inner,SystemClock.elapsedRealtime(),if(endpoint)HandoffFadePolicy.ENDPOINT_COMMITTED else HandoffFadePolicy.GLASS_COMMITTED,captured))
 }
 private fun offer(n:Notice){latest.set(n);schedule()}
 private fun schedule(){
  if(!pending.compareAndSet(false,true))return
  worker.execute{
   try{
    val n=latest.getAndSet(null) ?: return@execute
    val p=Parcel.obtain();val r=Parcel.obtain()
    try{p.writeInterfaceToken(AngleReader.DESCRIPTOR);p.writeInt(if(n.inner)1 else 0);p.writeLong(n.whenMs);p.writeInt(n.kind);p.writeLong(n.captured);LiveAngles.previewCommand(9,p,r);r.readException()}
    catch(_:Exception){}finally{p.recycle();r.recycle()}
   }finally{pending.set(false);if(latest.get()!=null)schedule()}
  }
 }
}
