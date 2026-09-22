package org.duofold.live
import android.os.Parcel
import android.os.SystemClock
import java.util.concurrent.Executors
/** A bounded draw notification; never blocks the animation/UI thread. */
internal object HandoffFadeFrames {
 private val worker=Executors.newSingleThreadExecutor()
 @Volatile private var pending=false
 private var last=0L
 fun drawn(inner:Boolean){
  val now=SystemClock.elapsedRealtime()
  if(pending||now-last<50)return
  pending=true;last=now
  worker.execute{
   val p=Parcel.obtain();val r=Parcel.obtain()
   try{p.writeInterfaceToken(AngleReader.DESCRIPTOR);p.writeInt(if(inner)1 else 0);p.writeLong(now);LiveAngles.previewCommand(9,p,r);r.readException()}
   catch(_:Exception){}finally{p.recycle();r.recycle();pending=false}
  }
 }
}
