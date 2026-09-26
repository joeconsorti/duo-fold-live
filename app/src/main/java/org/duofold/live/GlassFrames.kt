package org.duofold.live
import android.graphics.Bitmap
import android.os.*
import android.view.SurfaceControl
import androidx.compose.runtime.*
import java.util.concurrent.Executors
internal data class GlassFrame(val bitmap:Bitmap,val width:Int,val height:Int,val stamp:Long,val levels:List<Bitmap> = emptyList())
internal object GlassFrames {
 var frame by mutableStateOf<GlassFrame?>(null);private set
 var status by mutableStateOf("Glass renderer ready");private set
 private val main=Handler(Looper.getMainLooper())
 private val executor=Executors.newSingleThreadExecutor()
 private val settingsExecutor=Executors.newSingleThreadExecutor()
 private var targetFps=120
 private var displays:android.hardware.display.DisplayManager?=null
 private var measuredStart=0L;private var measuredFrames=0;private var measuredFps=0f
 fun configure(context:android.content.Context){displays=context.applicationContext.getSystemService(android.hardware.display.DisplayManager::class.java);targetFps=RenderQuality.fps(context.getSharedPreferences("standalone",0).getInt("content_fps",120))}
 private fun levels(bitmap:Bitmap):List<Bitmap>{val out=ArrayList<Bitmap>();var level=bitmap;repeat(7){level=Bitmap.createScaledBitmap(level,maxOf(1,level.width/2),maxOf(1,level.height/2),true);out.add(level)};return out}
 private var suspended=false
 fun suspendCapture(){suspended=true;generation++;frame=null;main.removeCallbacks(tick)}
 fun resumeCapture(){if(suspended){suspended=false;resetMeasurement();if(clients>0)main.post(tick)}}
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
  val valid=surfaces.values.filter{it.isValid}.take(4)
  if(valid.isEmpty()){frame=null;main.postDelayed(this,retryDelay(100));return}
  val started=SystemClock.elapsedRealtimeNanos();val gen=generation;val captureDisplay=if(LiveAngles.continuityNative)1 else 0;val refreshHz=runCatching{displays?.getDisplay(captureDisplay)?.refreshRate ?: 60f}.getOrDefault(60f);val effectiveFps=RenderQuality.effectiveFps(targetFps,refreshHz);pending=true
  executor.execute{
   var result:Bundle?=null;var error="Glass frame unavailable"
   val p=Parcel.obtain();val r=Parcel.obtain()
   try{val remote=LiveAngles.captureBinder();p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(valid.size);valid.forEach{p.writeTypedObject(it,0)};p.writeInt(captureDisplay)
    remote.transact(1,p,r,0);r.readException();result=r.readBundle(Bitmap::class.java.classLoader);error=result?.getString("error")?:error
   }catch(e:Exception){error=e.message?:error}finally{p.recycle();r.recycle()}
   val response=result;val message=error
   val capturedBitmap=response?.getParcelable("bitmap",Bitmap::class.java)
   val pyramid=if(response?.getBoolean("ok")==true&&capturedBitmap!=null)runCatching{levels(capturedBitmap)}.getOrDefault(emptyList()) else emptyList()
   main.post{pending=false;if(gen==generation && clients>0 && !suspended && captureDisplay==(if(LiveAngles.continuityNative)1 else 0)){
    val bitmap=capturedBitmap
    if(response?.getBoolean("ok")==true && bitmap!=null){frame=GlassFrame(bitmap,response.getInt("width"),response.getInt("height"),response.getLong("stamp"),pyramid);val now=SystemClock.elapsedRealtime();if(measuredStart==0L)measuredStart=now;measuredFrames++;if(now-measuredStart>=1000){measuredFps=measuredFrames*1000f/(now-measuredStart);measuredFrames=0;measuredStart=now};status="Content selected $targetFps FPS · target $effectiveFps FPS ($refreshHz Hz display) · measured ${"%.1f".format(measuredFps)} captures/s · ${response.getString("backend") ?: "layer capture"}"}
    else{frame=null;status="Glass unavailable; debug-style fallback: $message"}
    main.postDelayed(this,if(frame==null)retryDelay(600L) else RenderQuality.delay(effectiveFps,SystemClock.elapsedRealtimeNanos()-started))
   }else if(clients>0 && !suspended)main.post(this)}
  }
 }}
 fun freeze(done:(GlassFrame?,String)->Unit){
  val requestedAt=SystemClock.elapsedRealtime()
  val valid=surfaces.values.filter{it.isValid}.take(4)
  if(valid.isEmpty()){done(null,"Waiting for overlay exclusion surface");return}
  executor.execute{
   val p=Parcel.obtain();val r=Parcel.obtain();var captured:GlassFrame?=null;var note="Outgoing capture unavailable"
   val captureStarted=SystemClock.elapsedRealtime()
   try{
    p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(valid.size);valid.forEach{p.writeTypedObject(it,0)}
    LiveAngles.captureBinder().transact(3,p,r,0);r.readException();val result=r.readBundle(Bitmap::class.java.classLoader)
    val bitmap=result?.getParcelable("bitmap",Bitmap::class.java)
    if(result?.getBoolean("ok")==true && bitmap!=null){captured=GlassFrame(bitmap,result.getInt("width"),result.getInt("height"),result.getLong("stamp"));note="capture ${SystemClock.elapsedRealtime()-captureStarted} ms; queue ${captureStarted-requestedAt} ms"}
    else note=result?.getString("error")?:note
   }catch(e:Exception){note=e.message?:note}finally{p.recycle();r.recycle()}
   val f=captured;val message=note;main.post{done(f,message)}
  }
 }
 fun foldSetting(action:String,original:String?,done:(Bundle)->Unit){settingsExecutor.execute{
  val p=Parcel.obtain();val r=Parcel.obtain();val response=try{
   p.writeInterfaceToken(GlassCapture.TOKEN);p.writeString(action);if(action=="restore")p.writeString(original)
   LiveAngles.captureBinder().transact(2,p,r,0);r.readException();r.readBundle(javaClass.classLoader)?:Bundle()
  }catch(e:Exception){Bundle().apply{putString("error",e.message)}}finally{p.recycle();r.recycle()}
  main.post{done(response)}
 }}
}
