package org.duofold.live
import android.graphics.Bitmap
import android.os.*
import android.view.SurfaceControl
import androidx.compose.runtime.*
import java.util.concurrent.Executors
internal data class GlassFrame(val bitmap:Bitmap,val width:Int,val height:Int,val stamp:Long,val levels:List<Bitmap> = emptyList(),val physicalId:String="unknown",val displayId:Int=-1)
internal object GlassFrames {
 var frame by mutableStateOf<GlassFrame?>(null);private set
 var status by mutableStateOf("Glass renderer ready");private set
 private val main=Handler(Looper.getMainLooper())
 private val executor=Executors.newSingleThreadExecutor()
 private val settingsExecutor=Executors.newSingleThreadExecutor()
 private var screenshotMode=false
 private fun targetCaptureDisplay()=ScreenshotRenderPolicy.captureDisplay(LiveAngles.continuityNative,screenshotMode,LiveAngles.dualActive)
 private var targetFps=120
 private var measuredStart=0L;private var measuredFrames=0;private var measuredFps=0f
 fun configure(context:android.content.Context){screenshotMode=context.getSharedPreferences("standalone",0).getBoolean("dual",false)&&context.getSharedPreferences("standalone",0).getBoolean("screenshot_camera_startup",false);targetFps=RenderQuality.fps(context.getSharedPreferences("standalone",0).getInt("content_fps",120))}
 private fun levels(bitmap:Bitmap):List<Bitmap>{val out=ArrayList<Bitmap>();var level=bitmap;repeat(7){level=Bitmap.createScaledBitmap(level,maxOf(1,level.width/2),maxOf(1,level.height/2),true);out.add(level)};return out}

 /** Turns the helper's reply into a software Bitmap (see GlassCapture: the buffer is shipped, the copy happens here). */
 internal fun decode(b:Bundle):Bitmap?{
  b.getParcelable("bitmap",Bitmap::class.java)?.let{return it}
  val buffer=b.getParcelable("buffer",android.hardware.HardwareBuffer::class.java)?:return null
  return try{
   val id=b.getInt("colorSpace",-1);val named=android.graphics.ColorSpace.Named.values()
   val space=android.graphics.ColorSpace.get(if(id>=0&&id<named.size)named[id] else android.graphics.ColorSpace.Named.SRGB)
   val hardware=Bitmap.wrapHardwareBuffer(buffer,space)
   val copy=hardware?.copy(Bitmap.Config.ARGB_8888,false)
   hardware?.recycle();copy
  }finally{buffer.close()}
 }
 private var suspended=false
 private var recoveryStarted=0L
 private var recoveryFailure=""
 fun suspendCapture(){
  if(!suspended)RecoveryLog.add("Glass capture suspended for screen-off")
  suspended=true;generation++;frame=null;recoveryStarted=0L;main.removeCallbacks(tick)
 }
 fun resumeCapture(){
  if(!suspended)return
  suspended=false;resetMeasurement();recoveryStarted=SystemClock.elapsedRealtime();recoveryFailure=""
  RecoveryLog.add("Glass capture resumed after screen-on and unlock; requesting fresh frame")
  // One bounded warm-up client, paired even if another sleep happens meanwhile.
  // Normal renderer clients continue independently after the warm-up expires.
  acquire();requestFreshCapture();urgentUntil=SystemClock.elapsedRealtime()+1500
  main.postDelayed({release()},1500)
 }
 private var clients=0;private var generation=0;private var pending=false
 private val surfaces=LinkedHashMap<Any,SurfaceControl>()
 fun surface(key:Any,sc:SurfaceControl?){if(sc==null)surfaces.remove(key) else surfaces[key]=sc}
 private fun resetMeasurement(){measuredStart=SystemClock.elapsedRealtime();measuredFrames=0;measuredFps=0f}
 fun acquire(){clients++;if(clients==1){resetMeasurement();generation++;main.post(tick)}}
 fun release(){clients=(clients-1).coerceAtLeast(0);if(clients==0){generation++;frame=null;main.removeCallbacks(tick)}}
 private var urgentUntil=0L
 fun requestFreshCapture(){
  generation++;frame=null;urgentUntil=SystemClock.elapsedRealtime()+900
  main.removeCallbacks(tick)
  if(clients>0 && !suspended)main.post(tick)
 }
 private fun retryDelay(normal:Long)=if(SystemClock.elapsedRealtime()<urgentUntil)16L else normal
 private val tick=object:Runnable{override fun run(){
  if(clients==0 || suspended)return
  if(pending){main.postDelayed(this,retryDelay(50));return}
  val valid=surfaces.values.filter{it.isValid}.take(8)
  if(valid.isEmpty()){if(recoveryStarted>0L && recoveryFailure!="no exclusion surface"){recoveryFailure="no exclusion surface";RecoveryLog.add("Glass wake recovery waiting for exclusion surface")};frame=null;main.postDelayed(this,retryDelay(24));return}
  val started=SystemClock.elapsedRealtimeNanos();val gen=generation;val captureDisplay=targetCaptureDisplay();pending=true
  executor.execute{
   var result:Bundle?=null;var error="Glass frame unavailable"
   val p=Parcel.obtain();val r=Parcel.obtain()
   try{val remote=LiveAngles.captureBinder();p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(valid.size);valid.forEach{p.writeTypedObject(it,0)};p.writeInt(captureDisplay)
    remote.transact(1,p,r,0);r.readException();result=r.readBundle(Bitmap::class.java.classLoader);error=result?.getString("error")?:error
   }catch(e:Exception){error=e.message?:error}finally{p.recycle();r.recycle()}
   val response=result;val message=error
   val capturedBitmap=response?.let{decode(it)}
   val pyramid=if(response?.getBoolean("ok")==true&&capturedBitmap!=null)runCatching{levels(capturedBitmap)}.getOrDefault(emptyList()) else emptyList()
   main.post{pending=false;if(gen==generation && clients>0 && !suspended && captureDisplay==targetCaptureDisplay()){
    val bitmap=capturedBitmap
    if(response?.getBoolean("ok")==true && bitmap!=null){if(recoveryStarted>0L){RecoveryLog.add("Glass wake recovery first capture: D$captureDisplay ${response.getInt("width")}x${response.getInt("height")}; +${SystemClock.elapsedRealtime()-recoveryStarted} ms");recoveryStarted=0L;recoveryFailure=""};frame=GlassFrame(bitmap,response.getInt("width"),response.getInt("height"),response.getLong("stamp"),pyramid,displayId=captureDisplay);val now=SystemClock.elapsedRealtime();if(measuredStart==0L)measuredStart=now;measuredFrames++;if(now-measuredStart>=1000){measuredFps=measuredFrames*1000f/(now-measuredStart);measuredFrames=0;measuredStart=now};status="Content target $targetFps FPS · measured ${"%.1f".format(measuredFps)} captures/s · D$captureDisplay · ${response.getString("backend") ?: "layer capture"}"}
    else{frame=null;status="Glass unavailable; debug-style fallback: $message";if(recoveryStarted>0L && recoveryFailure!=message){recoveryFailure=message;RecoveryLog.add("Glass wake recovery capture failed: $message")}}
    main.postDelayed(this,if(frame==null)retryDelay(150L) else RenderQuality.delay(targetFps,SystemClock.elapsedRealtimeNanos()-started))
   }else if(clients>0 && !suspended)main.post(this)}
  }
 }}
 fun freeze(labelled:Boolean=false,done:(GlassFrame?,String)->Unit){
  val requestedAt=SystemClock.elapsedRealtime()
  val valid=surfaces.values.filter{it.isValid}.take(8)
  if(valid.isEmpty()){done(null,"Waiting for overlay exclusion surface");return}
  executor.execute{
   val p=Parcel.obtain();val r=Parcel.obtain();var captured:GlassFrame?=null;var note="Outgoing capture unavailable"
   val captureStarted=SystemClock.elapsedRealtime()
   try{
    p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(valid.size);valid.forEach{p.writeTypedObject(it,0)}
    LiveAngles.captureBinder().transact(3,p,r,0);r.readException();val result=r.readBundle(Bitmap::class.java.classLoader)
    val bitmap=result?.let{decode(it)}
    if(result?.getBoolean("ok")==true && bitmap!=null){val source=result.getString("physicalId")?:"unknown"
     val w=result.getInt("width");val h=result.getInt("height");val stamp=result.getLong("stamp")
     val tagged=if(labelled)bitmap.copy(Bitmap.Config.ARGB_8888,true).also{copy->
      val canvas=android.graphics.Canvas(copy);val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
      val size=(copy.width/28f).coerceAtLeast(16f);val bar=size*2.7f
      val panel=if(minOf(w,h).toFloat()/maxOf(w,h)>.7f)"INNER" else "COVER"
      for(y in listOf(copy.height*.16f,copy.height*.78f)){
       paint.color=android.graphics.Color.MAGENTA;canvas.drawRect(0f,y,copy.width.toFloat(),y+bar,paint)
       paint.color=android.graphics.Color.BLACK;paint.textSize=size;paint.typeface=android.graphics.Typeface.DEFAULT_BOLD
       canvas.drawText("FROZEN $panel #${stamp%100000}",8f,y+size,paint)
       paint.textSize=size*.62f;canvas.drawText("src D${result.getInt("displayId")} / ${source.takeLast(8)} / ${w}x${h}",8f,y+size*2f,paint)
      }
      bitmap.recycle()
     } else bitmap
     captured=GlassFrame(tagged,w,h,stamp,physicalId=source,displayId=result.getInt("displayId"));note="source D${result.getInt("displayId")} physical=$source; label=$labelled; "+"capture ${SystemClock.elapsedRealtime()-captureStarted} ms; queue ${captureStarted-requestedAt} ms"}
    else note=result?.getString("error")?:note
   }catch(e:Exception){note=e.message?:note}finally{p.recycle();r.recycle()}
   val f=captured;val message=note;main.post{done(f,message)}
  }
 }
 fun incoming(task:Int,physical:String,done:(GlassFrame?,String)->Unit){
  val valid=surfaces.values.filter{it.isValid}.take(8)
  if(valid.isEmpty()){done(null,"No exclusion surface");return}
  settingsExecutor.execute{
   val p=Parcel.obtain();val r=Parcel.obtain();var frame:GlassFrame?=null;var note="Capture unavailable"
   try{p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(valid.size);valid.forEach{p.writeTypedObject(it,0)};p.writeInt(1);p.writeInt(task);p.writeString(physical)
    LiveAngles.captureBinder().transact(5,p,r,0);r.readException();val b=r.readBundle(Bitmap::class.java.classLoader)
    val bitmap=b?.let{decode(it)}
    if(b?.getBoolean("ok")==true&&bitmap!=null)frame=GlassFrame(bitmap,b.getInt("width"),b.getInt("height"),b.getLong("stamp"),physicalId=b.getString("physicalId")?:"",displayId=1)
    note=b?.getString("error")?:"Exact app capture complete"
   }catch(e:Exception){note=e.message?:note}finally{p.recycle();r.recycle()}
   val f=frame;val message=note;main.post{done(f,message)}
  }
 }
 fun incomingReady(task:Int,physical:String,width:Int,height:Int,done:(Boolean,String)->Unit){settingsExecutor.execute{
  val p=Parcel.obtain();val r=Parcel.obtain();var ready=false;var note="Unavailable"
  try{p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(task);p.writeString(physical);p.writeInt(width);p.writeInt(height);LiveAngles.captureBinder().transact(4,p,r,0);r.readException();val b=r.readBundle();ready=b?.getBoolean("ok")==true;note=b?.getString("error")?:note}
  catch(e:Exception){note=e.message?:note}finally{p.recycle();r.recycle()}
  val ok=ready;val message=note;main.post{done(ok,message)}
 }}
 fun foldSetting(action:String,original:String?,done:(Bundle)->Unit){settingsExecutor.execute{
  val p=Parcel.obtain();val r=Parcel.obtain();val response=try{
   p.writeInterfaceToken(GlassCapture.TOKEN);p.writeString(action);if(action=="restore")p.writeString(original)
   LiveAngles.captureBinder().transact(2,p,r,0);r.readException();r.readBundle(javaClass.classLoader)?:Bundle()
  }catch(e:Exception){Bundle().apply{putString("error",e.message)}}finally{p.recycle();r.recycle()}
  main.post{done(response)}
 }}
}
