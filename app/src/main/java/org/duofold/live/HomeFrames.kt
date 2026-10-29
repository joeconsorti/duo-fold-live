package org.duofold.live

import android.accessibilityservice.AccessibilityService
import android.graphics.*
import android.os.*
import org.duofold.live.wallpaperlayer.HomeCaptureSource
import java.util.concurrent.Executors

/** Home-only isolated window capture. The user's wallpaper is composed ONCE,
 * underneath launcher pixels. No display recapture fallback or opacity boosting. */
internal object HomeFrames {
 private val worker=Executors.newSingleThreadExecutor()
 private val validationWorker=Executors.newSingleThreadExecutor()
 private val main=Handler(Looper.getMainLooper())
 private val pacing=HomeCapturePacing() // worker-only; no cooldown for a rejected preflight
 private var busy=false
 var status="Home window capture: idle";private set
 private fun guard(task:Int,physical:String):Bundle{
  val p=Parcel.obtain();val r=Parcel.obtain()
  try{p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(task);p.writeString(physical)
   check(LiveAngles.captureBinder().transact(10,p,r,0)){"Home guard unavailable"};r.readException()
   val b=r.readBundle()?:error("No Home guard result");check(b.getBoolean("ok")){b.getString("error")?:"Home guard rejected"};return b
  }finally{p.recycle();r.recycle()}
 }
 fun incoming(task:Int,physical:String,blur:Boolean,reference:HomeContentPolicy.Sample?,done:(GlassFrame?,String,Long)->Unit){
  val host=StandaloneService.instance
  if(host==null||!RuntimeConsent.ready(host)||!host.getSharedPreferences("standalone",0).getBoolean("enabled",false)){done(null,"Home capture inactive",100);return}
  if(busy){done(null,"Home window capture still active",50);return}
  val now=SystemClock.elapsedRealtime()
  val source=try{HomeCaptureSource.current()}catch(e:Exception){done(null,"Home source unavailable: ${e.message}",50);return}
  if(source.physical!=physical){done(null,"Home source mapping pending",50);return}
  busy=true
  worker.execute{
   var raw:Bitmap?=null;var layer:Bitmap?=null;var image:Bitmap?=null;val levels=ArrayList<Bitmap>();var frame:GlassFrame?=null
   var note=""
   var capturedBounds:Rect?=null;var geometry="unmeasured"
   var windowId=-1;var submitted=false;var screenshotError=0;var retry=50L
   val began=SystemClock.elapsedRealtime();var preflightMs=-1L;var screenshotMs=-1L;var validationMs=-1L;var buildMs=-1L
   var validation:java.util.concurrent.Future<*>?=null
   try{
    val window=source.window();windowId=window.id
    val wait=pacing.delayMs(window.id,SystemClock.uptimeMillis())
    if(wait>0){retry=wait;error("Home window capture interval pending (${wait}ms)")}
    val before=guard(task,physical)
    check(before.getInt("width")==source.width&&before.getInt("height")==source.height&&before.getInt("rotation")==source.rotation){"Home source geometry changed"}
    preflightMs=SystemClock.elapsedRealtime()-began
    val captureAt=SystemClock.elapsedRealtime()
    val result=CaptureCompletion<AccessibilityService.ScreenshotResult>{it.hardwareBuffer.close()}.use{completion->
     // Pacing begins at the actual request, not at an earlier mapping/window
     // check that may reject before any screenshot reaches Android.
     pacing.submitted(window.id,SystemClock.uptimeMillis());submitted=true
     source.capture(window,{it.run()},object:AccessibilityService.TakeScreenshotCallback{
      override fun onSuccess(result:AccessibilityService.ScreenshotResult){completion.accept(result,0)}
      override fun onFailure(errorCode:Int){screenshotError=errorCode;completion.accept(null,errorCode)}
     })
     completion.await(800)
    }
    screenshotMs=SystemClock.elapsedRealtime()-captureAt
    result.hardwareBuffer.use{buffer->
     geometry="buffer=${buffer.width}x${buffer.height} window=${window.bounds} display=${source.width}x${source.height}"
     val full=buffer.width==source.width&&buffer.height==source.height
     val exact=HomeBufferGeometry.exactWindow(buffer.width,buffer.height,window.bounds.left,window.bounds.top,window.bounds.right,window.bounds.bottom,source.width,source.height)
     check(full||exact){ "Home window buffer geometry pending; $geometry" }
     capturedBounds=if(full)Rect(0,0,source.width,source.height) else Rect(window.bounds)
     // Validate the captured Home while building its pixels. Publication still
     // requires the same post-capture identity/window checks, including failures.
     val validationAt=SystemClock.elapsedRealtime()
     validation=validationWorker.submit{
      try{val after=guard(task,physical);val afterWindow=source.window()
       check(before.getString("home")==after.getString("home")&&after.getInt("width")==source.width&&after.getInt("height")==source.height&&after.getInt("rotation")==source.rotation&&afterWindow.id==window.id&&(full||afterWindow.bounds==window.bounds)){"Home identity/window changed during capture"}
      }finally{validationMs=SystemClock.elapsedRealtime()-validationAt}
     }
     val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)?:error("Home buffer unreadable")
     try{raw=hardware.copy(Bitmap.Config.ARGB_8888,false)?:error("Home pixels unreadable")}finally{hardware.recycle()}
    }
    val sample=Bitmap.createScaledBitmap(raw!!,64,64,true)
    val colors=IntArray(4096);try{sample.getPixels(colors,0,64,0,0,64,64)}finally{if(sample!==raw)sample.recycle()}
    val content=HomeContentPolicy.measure(colors,64,64);val rejection=HomeContentPolicy.rejection(content,reference)
    check(rejection.isEmpty()){ "$rejection; $content; source=$reference" }
    val scale=minOf(1f,1440f/maxOf(source.width,source.height));val w=(source.width*scale).toInt().coerceAtLeast(1);val h=(source.height*scale).toInt().coerceAtLeast(1)
    image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(image!!);val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source.wallpaper,source.crop,Rect(0,0,w,h),paint)
    val b=capturedBounds!!;canvas.drawBitmap(raw!!,null,RectF(b.left*scale,b.top*scale,b.right*scale,b.bottom*scale),paint)
    val small=minOf(1f,320f/maxOf(raw!!.width,raw!!.height))
    layer=Bitmap.createScaledBitmap(raw!!,(raw!!.width*small).toInt().coerceAtLeast(1),(raw!!.height*small).toInt().coerceAtLeast(1),true)
    if(layer===raw)layer=raw!!.copy(Bitmap.Config.ARGB_8888,false)
    if(blur){var level=image!!;repeat(7){level=Bitmap.createScaledBitmap(level,maxOf(1,level.width/2),maxOf(1,level.height/2),true);levels.add(level)}}
    buildMs=SystemClock.elapsedRealtime()-captureAt-screenshotMs
    validation!!.get() // Never deliver unvalidated pixels, even if rendering is already prepared.
    note="HOME_ISOLATED_WINDOW window=${window.id}; wallpaper drawn behind launcher once; $content; content heuristic, not optical proof"
    frame=GlassFrame(image!!,source.width,source.height,now,levels=levels,physicalId=physical,displayId=0,rotation=source.rotation,exclusions=note,homeContent=content,homeLayer=layer)
    image=null;layer=null
   }catch(e:Exception){
    var cause:Throwable=e;while(cause.cause!=null)cause=cause.cause!!
    // Firmware may enforce a stricter interval. Back off without a capture loop.
    if(submitted&&screenshotError==AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT)pacing.rateLimited(windowId,SystemClock.uptimeMillis())
    note="Home window retained previous image: ${cause.message}"
   }
   finally{
    // Drain this attempt before admitting another; no orphan validation backlog.
    runCatching{validation?.get()}
    raw?.recycle();image?.recycle();layer?.recycle();if(frame==null)levels.distinct().forEach{if(!it.isRecycled)it.recycle()}
   }
   if(windowId>=0)retry=maxOf(retry,pacing.delayMs(windowId,SystemClock.uptimeMillis()))
   val output=frame;val message=note;val finished=SystemClock.elapsedRealtime();val retryMs=retry
   val timing="HOME_CAPTURE_TIMING physical=$physical task=$task window=$windowId requested=$submitted accepted=${output!=null} queueMs=${began-now} preflightMs=$preflightMs screenshotMs=$screenshotMs validationMs=$validationMs buildMs=$buildMs validationOverlapsBuild=true workerMs=${finished-began} retryMs=$retryMs geometry=$geometry"
   main.post{busy=false;status=message+"\n"+timing+" deliveryMs=${SystemClock.elapsedRealtime()-finished}";RecoveryLog.add(status);done(output,message,retryMs)}
  }
 }
}
