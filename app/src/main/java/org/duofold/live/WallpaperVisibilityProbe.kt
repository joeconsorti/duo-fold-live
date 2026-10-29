package org.duofold.live
import android.graphics.Bitmap
import android.os.*
import android.view.SurfaceControl
import java.util.concurrent.Executors
import org.duofold.live.wallpaperlayer.WallpaperProbeSurfaces
/** At most one three-frame probe in flight. No surface transactions or visible changes. */
internal object WallpaperVisibilityProbe {
 private val worker=Executors.newSingleThreadExecutor()
 private val main=Handler(Looper.getMainLooper())
 private var busy=false
 private val rows=ArrayDeque<String>()
 private fun record(s:String){val row="${SystemClock.elapsedRealtime()} $s";rows.addLast(row);while(rows.size>32)rows.removeFirst();RecoveryLog.add("WALLPAPER_PIXEL $row")}
 fun report()="Wallpaper pixel diagnostic: A/B/A capture exclusion; central 80% only; capture evidence, not optical proof. Contribution may also be legitimate transparency. Busy/failed/unstable samples are not passes.\n"+rows.joinToString("\n")
 fun skipped(session:Long,stage:String,reason:String){record("session=$session stage=$stage SKIPPED $reason")}
 fun sample(session:Long,stage:String,task:Int,physical:String,display:Int,underPresentation:Boolean=false,done:()->Unit={}){
  if(busy){record("session=$session stage=$stage SKIPPED busy");done();return}
  val photos=try{WallpaperProbeSurfaces.snapshot()}catch(e:Exception){record("session=$session stage=$stage UNAVAILABLE ${e.message}");done();return}
  val base=if(underPresentation)GlassFrames.probeExclusions() else emptyArray()
  if(base.size+photos.size>64){record("session=$session stage=$stage UNAVAILABLE too many exclusions");done();return}
  busy=true
  worker.execute{
   val started=SystemClock.elapsedRealtime();val frames=ArrayList<GlassFrame>()
   val result=try{
    for(exclusions in listOf(base,base+photos,base)){
     if(SystemClock.elapsedRealtime()-started>1500)error("probe time bound exceeded")
     val p=Parcel.obtain();val r=Parcel.obtain()
     try{p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(exclusions.size);exclusions.forEach{p.writeTypedObject(it,0)};p.writeInt(display);p.writeInt(task);p.writeString(physical)
      LiveAngles.captureBinder().transact(6,p,r,0);r.readException();val b=r.readBundle(Bitmap::class.java.classLoader)?:error("Empty capture")
      val bitmap=b.getParcelable("bitmap",Bitmap::class.java)
      if(!b.getBoolean("ok")||bitmap==null){bitmap?.recycle();error(b.getString("error")?:"Capture unavailable")}
      frames.add(GlassFrame(bitmap,b.getInt("width"),b.getInt("height"),b.getLong("stamp"),physicalId=b.getString("physicalId")?:"",displayId=display,rotation=b.getInt("rotation")))
     }finally{p.recycle();r.recycle()}
    }
    val a=frames.first();check(frames.all{it.width==a.width&&it.height==a.height&&it.rotation==a.rotation&&it.physicalId==physical&&it.bitmap.width==a.bitmap.width&&it.bitmap.height==a.bitmap.height}){"Mapping changed between samples"}
    check(SystemClock.elapsedRealtime()-started<=1500){"probe time bound exceeded"}
    val pixels=frames.map{f->val w=f.bitmap.width;val h=f.bitmap.height;val x=w/10;val y=h/10;val cw=w-2*x;val ch=h-2*y;IntArray(cw*ch).also{f.bitmap.getPixels(it,0,cw,x,y,cw,ch)}}
    WallpaperPixelEvidence.analyze(pixels[0],pixels[1],pixels[2])+" rotation=${a.rotation}"
   }catch(e:Exception){"INCONCLUSIVE ${e.message}"}finally{frames.forEach{it.bitmap.recycle()}}
   val span=SystemClock.elapsedRealtime()-started
   main.post{busy=false;record("session=$session stage=$stage D$display physical=$physical excludedPhotoLayers=${photos.size} spanMs=$span underPresentation=$underPresentation $result");done()}
  }
 }
}
