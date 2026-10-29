package org.duofold.live
import android.graphics.Bitmap
import android.os.*
import android.view.SurfaceControl
import androidx.compose.runtime.*
import java.util.concurrent.Executors
internal data class GlassFrame(val bitmap:Bitmap,val width:Int,val height:Int,val stamp:Long,val levels:List<Bitmap> = emptyList(),val physicalId:String="unknown",val displayId:Int=-1,val rotation:Int=0,val exclusions:String="not recorded",val darkSamples:Int=-1,val homeContent:HomeContentPolicy.Sample?=null,val homeLayer:Bitmap?=null)
internal object GlassFrames {
 var frame by mutableStateOf<GlassFrame?>(null);private set
 var status by mutableStateOf("Glass renderer ready");private set
 private val main=Handler(Looper.getMainLooper())
 private val executor=Executors.newSingleThreadExecutor()
 private val exactExecutor=Executors.newSingleThreadExecutor()
 private val settingsExecutor=Executors.newSingleThreadExecutor()
 private val readinessExecutor=Executors.newSingleThreadExecutor()
 private var screenshotMode=false
 private var snapshotOnly=false
 private fun targetCaptureDisplay()=if(screenshotMode&&LiveAngles.dualActive) IncomingAnimationFrame.captureDisplay else ScreenshotRenderPolicy.captureDisplay(LiveAngles.continuityNative,false,LiveAngles.dualActive)
 private var targetFps=120
 private var discarded=0L;private var coalesced=0L
 private var lastMipMs=0L;private var lastDeliveryMs=0L
 fun pipelineReport()="CPU logical processors=${Runtime.getRuntime().availableProcessors()}; capture + mip worker, exact-app worker; GPU hardware Canvas/RuntimeShader. Capture processing: last mip build=${lastMipMs}ms; last main delivery queue=${lastDeliveryMs}ms; obsolete captures discarded=$discarded; fresh requests coalesced=$coalesced"
 private var statusAt=0L;private var statusDisplay=-1
 private var measuredStart=0L;private var measuredFrames=0;private var measuredFps=0f
 fun configure(context:android.content.Context){
  val prefs=context.getSharedPreferences("standalone",0)
  val snapshots=FoldingModeSettings.screenshot(prefs)
  if(snapshots!=snapshotOnly){snapshotOnly=snapshots;epoch.invalidate();frame=null;main.removeCallbacks(tick);resetMeasurement();if(!snapshots&&clients>0)main.post(tick)}
  if(snapshotOnly)status="Duo Mode: continuous capture OFF; exact app snapshots only"
 screenshotMode=context.getSharedPreferences("standalone",0).getBoolean("dual",false)&&context.getSharedPreferences("standalone",0).getBoolean("screenshot_camera_startup",false);targetFps=RenderQuality.fps(context.getSharedPreferences("standalone",0).getInt("content_fps",120))}
 private fun levels(bitmap:Bitmap,gen:Int?=null):List<Bitmap>{
  val out=ArrayList<Bitmap>();var level=bitmap
  repeat(7){
   if(gen!=null&&gen!=epoch.current()){out.filter{it!==bitmap}.distinct().forEach{it.recycle()};return emptyList()}
   level=Bitmap.createScaledBitmap(level,maxOf(1,level.width/2),maxOf(1,level.height/2),true);out.add(level)
  }
  return out
 }
 private val listeners=LinkedHashSet<()->Unit>()
 fun listen(listener:()->Unit){listeners.add(listener)}
 fun unlisten(listener:()->Unit){listeners.remove(listener)}
 private val primarySource=CaptureSourceState()
 fun primaryChanged(display:android.view.Display){
  if(screenshotMode)return
  val identity=CoverBridge.identity(display)
  if(!primarySource.change(identity,display.rotation))return
  val key=identity+":"+display.rotation
  HandoffStartupTrace.event("CAPTURE_SOURCE_CHANGE","source=$key")
  requestFreshCapture()
 }
 // Output resizing does not change captured content. Wake the producer without
 // invalidating a valid frame or the source capture already in flight.
 fun outputReady(){
  if(clients>0 && !suspended && !pending){main.removeCallbacks(tick);main.post(tick)}
 }
 private var b8Paused=false
 fun b8CapturePause(value:Boolean){
  if(b8Paused==value)return
  b8Paused=value;epoch.invalidate();frame=null;main.removeCallbacks(tick)
  if(!value&&clients>0&&!suspended)main.post(tick)
 }
 fun b8CaptureQuiescent()=b8Paused&&!pending
 private var suspended=false
 private var recoveryStarted=0L
 private var recoveryFailure=""
 fun suspendCapture(){
  CoverBridge.clear()
  if(!suspended)RecoveryLog.add("Glass capture suspended for screen-off")
  suspended=true;epoch.invalidate();frame=null;recoveryStarted=0L;main.removeCallbacks(tick)
 }
 fun resumeCapture(){
  if(!suspended)return
  suspended=false
  if(snapshotOnly){recoveryStarted=0L;return}
  resetMeasurement();recoveryStarted=SystemClock.elapsedRealtime();recoveryFailure=""
  RecoveryLog.add("Glass capture resumed after screen-on and unlock; requesting fresh frame")
  // One bounded warm-up client, paired even if another sleep happens meanwhile.
  // Normal renderer clients continue independently after the warm-up expires.
  acquire();requestFreshCapture();urgentUntil=SystemClock.elapsedRealtime()+1500
  main.postDelayed({release()},1500)
 }
 private var clients=0;private val epoch=CaptureEpoch();private var pending=false
 fun probeExclusions()=surfaces.values.filter{it.isValid}.toTypedArray()
 private val surfaces=LinkedHashMap<Any,SurfaceControl>()
 fun surface(key:Any,sc:SurfaceControl?){if(sc==null)surfaces.remove(key) else surfaces[key]=sc}
 private fun resetMeasurement(){measuredStart=SystemClock.elapsedRealtime();measuredFrames=0;measuredFps=0f}
 fun acquire(){clients++;if(clients==1){resetMeasurement();epoch.invalidate();main.post(tick)}}
 fun release(){clients=(clients-1).coerceAtLeast(0);if(clients==0){CoverBridge.clear();epoch.invalidate();frame=null;main.removeCallbacks(tick)}}
 private var urgentUntil=0L
 fun requestFreshCapture(){
  if(!epoch.requestFresh()){coalesced++;return}
  frame=null;urgentUntil=SystemClock.elapsedRealtime()+900
  main.removeCallbacks(tick)
  if(clients>0 && !suspended)main.post(tick)
 }
 private fun retryDelay(normal:Long)=if(SystemClock.elapsedRealtime()<urgentUntil)16L else normal
 private val tick=object:Runnable{override fun run(){
  if(snapshotOnly || clients==0 || suspended || b8Paused)return
  if(pending){main.postDelayed(this,retryDelay(50));return}
  val valid=surfaces.values.filter{it.isValid}.take(8)
  if(valid.isEmpty()){if(recoveryStarted>0L && recoveryFailure!="no exclusion surface"){recoveryFailure="no exclusion surface";RecoveryLog.add("Glass wake recovery waiting for exclusion surface")};frame=null;main.postDelayed(this,retryDelay(100));return}
  val started=SystemClock.elapsedRealtimeNanos();val gen=epoch.start();val captureDisplay=targetCaptureDisplay();pending=true
  executor.execute{
   var result:Bundle?=null;var error="Glass frame unavailable"
   val p=Parcel.obtain();val r=Parcel.obtain()
   try{val remote=LiveAngles.captureBinder();p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(valid.size);valid.forEach{p.writeTypedObject(it,0)};p.writeInt(captureDisplay)
    remote.transact(1,p,r,0);r.readException();result=r.readBundle(Bitmap::class.java.classLoader);error=result?.getString("error")?:error
   }catch(e:Exception){error=e.message?:error}finally{p.recycle();r.recycle()}
   val response=result;val message=error
   val capturedBitmap=response?.getParcelable("bitmap",Bitmap::class.java)
   val captureDone=SystemClock.elapsedRealtime();val mipStarted=captureDone
   val pyramid=if(gen==epoch.current()&&response?.getBoolean("ok")==true&&capturedBitmap!=null)runCatching{levels(capturedBitmap,gen)}.getOrDefault(emptyList()) else emptyList()
   val mipMs=SystemClock.elapsedRealtime()-mipStarted;val deliveredAt=SystemClock.elapsedRealtime()
   main.post{lastMipMs=mipMs;lastDeliveryMs=SystemClock.elapsedRealtime()-deliveredAt;pending=false;main.removeCallbacks(this);if(!snapshotOnly && gen==epoch.current() && clients>0 && !suspended && !b8Paused && captureDisplay==targetCaptureDisplay()){
    val bitmap=capturedBitmap
    if(response?.getBoolean("ok")==true && bitmap!=null){if(recoveryStarted>0L){RecoveryLog.add("Glass wake recovery first capture: D$captureDisplay ${response.getInt("width")}x${response.getInt("height")}; +${SystemClock.elapsedRealtime()-recoveryStarted} ms");recoveryStarted=0L;recoveryFailure=""};frame=GlassFrame(bitmap,response.getInt("width"),response.getInt("height"),response.getLong("stamp"),pyramid,physicalId=response.getString("physicalId")?:"unknown",displayId=captureDisplay,rotation=response.getInt("rotation"),exclusions=valid.joinToString(" | "));CoverBridge.remember(frame!!);CoverStartupTrace.capture(frame!!);HandoffStartupTrace.capture(frame!!);HandoffStartupTrace.event("CAPTURE_PIPELINE","captureAndQueueMs=${captureDone-started/1_000_000}; mipMs=$mipMs; deliveryMs=$lastDeliveryMs");listeners.toList().forEach{it()};val now=SystemClock.elapsedRealtime();if(measuredStart==0L)measuredStart=now;measuredFrames++;if(now-measuredStart>=1000){measuredFps=measuredFrames*1000f/(now-measuredStart);measuredFrames=0;measuredStart=now};if(now>=statusAt || statusDisplay!=captureDisplay){statusAt=now+1000;statusDisplay=captureDisplay;status="Content target $targetFps FPS · measured ${"%.1f".format(measuredFps)} captures/s · D$captureDisplay · ${response.getString("backend") ?: "layer capture"}"}}
    else{frame=null;statusAt=0;status="Glass unavailable; debug-style fallback: $message";if(recoveryStarted>0L && recoveryFailure!=message){recoveryFailure=message;RecoveryLog.add("Glass wake recovery capture failed: $message")}}
    main.postDelayed(this,if(frame==null)retryDelay(600L) else RenderQuality.delay(targetFps,SystemClock.elapsedRealtimeNanos()-started))
   }else{
    discarded++
    // Never published: these buffers cannot be referenced by a renderer.
    (pyramid+listOfNotNull(capturedBitmap)).distinct().forEach{it.recycle()}
    if(clients>0 && !suspended)main.post(this)
   }}
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
    val bitmap=result?.getParcelable("bitmap",Bitmap::class.java)
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
     captured=GlassFrame(tagged,w,h,stamp,levels=levels(tagged),physicalId=source,displayId=result.getInt("displayId"));note="source D${result.getInt("displayId")} physical=$source; label=$labelled; "+"capture ${SystemClock.elapsedRealtime()-captureStarted} ms; queue ${captureStarted-requestedAt} ms"}
    else note=result?.getString("error")?:note
   }catch(e:Exception){note=e.message?:note}finally{p.recycle();r.recycle()}
   val f=captured;val message=note;main.post{done(f,message)}
  }
 }
 fun incoming(task:Int,physical:String,targetDisplay:Int=1,blurLevels:Boolean=true,includeWallpaper:Boolean=false,workspaceTasks:IntArray?=null,done:(GlassFrame?,String)->Unit){
  val overlays=surfaces.values.filter{it.isValid}
  if(overlays.isEmpty()){done(null,"No exclusion surface");return}
  // Exclude our photo leaves from the retained image, not just from later probes.
  // A wallpaper already baked into a snapshot cannot be detected by excluding live layers.
  val photos=try{if(includeWallpaper)emptyList() else org.duofold.live.wallpaperlayer.WallpaperProbeSurfaces.captureExclusions().toList()}
   catch(e:Exception){done(null,"Wallpaper exclusion unavailable: ${e.message}");return}
  val valid=overlays+photos
  if(valid.size>64){done(null,"Too many capture exclusion surfaces");return}
  RecoveryLog.add("B8 EXACT_CAPTURE_REQUEST task=$task physical=$physical display=$targetDisplay overlays=${overlays.size} photos=${photos.size}")
  exactExecutor.execute{
   val p=Parcel.obtain();val r=Parcel.obtain();var frame:GlassFrame?=null;var note="Capture unavailable"
   try{p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(valid.size);valid.forEach{p.writeTypedObject(it,0)};p.writeInt(targetDisplay);p.writeInt(task);p.writeString(physical)
    if(workspaceTasks!=null)p.writeIntArray(workspaceTasks)
    LiveAngles.captureBinder().transact(if(workspaceTasks!=null)11 else if(includeWallpaper)9 else 5,p,r,0);r.readException();val b=r.readBundle(Bitmap::class.java.classLoader)
    val bitmap=b?.getParcelable("bitmap",Bitmap::class.java)
    if(b?.getBoolean("ok")==true&&bitmap!=null)frame=GlassFrame(bitmap,b.getInt("width"),b.getInt("height"),b.getLong("stamp"),levels=if(blurLevels)levels(bitmap) else emptyList(),physicalId=b.getString("physicalId")?:"",displayId=targetDisplay,rotation=b.getInt("rotation"),exclusions=valid.joinToString(" | "))
    note=b?.getString("error")?:b?.getString("provenance")?:"Selected-app display capture complete"
   }catch(e:Exception){note=e.message?:note}finally{p.recycle();r.recycle()}
   var darkSamples=-1
   val pixels=frame?.let { image -> runCatching {
    val small=Bitmap.createScaledBitmap(image.bitmap,32,32,true)
    val readable=if(small.config==Bitmap.Config.HARDWARE)small.copy(Bitmap.Config.ARGB_8888,false) else small
    try { val colors=IntArray(1024);readable.getPixels(colors,0,32,0,0,32,32)
     val dark=colors.count{android.graphics.Color.red(it)<=12&&android.graphics.Color.green(it)<=12&&android.graphics.Color.blue(it)<=12}
     darkSamples=dark
     "darkSamples=$dark/1024; capture evidence only; dark app content may be legitimate"
    } finally {if(readable!==small)readable.recycle();if(small!==image.bitmap)small.recycle()}
   }.getOrElse{"pixel sample unavailable: ${it.javaClass.simpleName}"} }?:"no image"
   val f=frame?.copy(darkSamples=darkSamples)
   val message=note;main.post{RecoveryLog.add("B8 CAPTURE_PIXELS physical=$physical display=$targetDisplay stamp=${f?.stamp} $pixels provenance=$message");done(f,message)}
  }
 }
 fun incomingReady(task:Int,physical:String,width:Int,height:Int,done:(Boolean,String)->Unit){incomingReadyTimed(task,physical,width,height){ok,note,_->done(ok,note)}}
 fun incomingReadyTimed(task:Int,physical:String,width:Int,height:Int,workspaceTasks:IntArray?=null,done:(Boolean,String,Long)->Unit){readinessExecutor.execute{
  val p=Parcel.obtain();val r=Parcel.obtain();var ready=false;var note="Unavailable"
  try{p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(task);p.writeString(physical);p.writeInt(width);p.writeInt(height);if(workspaceTasks!=null)p.writeIntArray(workspaceTasks);LiveAngles.captureBinder().transact(if(workspaceTasks!=null)12 else 4,p,r,0);r.readException();val b=r.readBundle();ready=b?.getBoolean("ok")==true;note=b?.getString("error")?:note}
  catch(e:Exception){note=e.message?:note}finally{p.recycle();r.recycle()}
  // Timestamp the completed check on its worker, before any main-thread delivery delay.
  val ok=ready;val message=note;val observedAt=SystemClock.elapsedRealtime();main.post{done(ok,message,observedAt)}
 }}
 fun foldSetting(action:String,original:String?,done:(Bundle)->Unit){settingsExecutor.execute{
  val p=Parcel.obtain();val r=Parcel.obtain();val response=try{
   p.writeInterfaceToken(GlassCapture.TOKEN);p.writeString(action);if(action=="restore")p.writeString(original)
   LiveAngles.captureBinder().transact(2,p,r,0);r.readException();r.readBundle(javaClass.classLoader)?:Bundle()
  }catch(e:Exception){Bundle().apply{putString("error",e.message)}}finally{p.recycle();r.recycle()}
  main.post{done(response)}
 }}
}
