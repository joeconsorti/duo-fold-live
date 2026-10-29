package org.duofold.live

import android.accessibilityservice.AccessibilityService
import android.graphics.*
import android.os.*
import org.duofold.live.wallpaperlayer.HomeCaptureSource
import java.util.concurrent.Executors

/** Capture each selected app window without the display's transition/dim layers.
 * Compose at native window bounds over the saved wallpaper once. Never boost alpha. */
internal object WorkspaceFrames {
 private val worker=Executors.newSingleThreadExecutor()
 private val main=Handler(Looper.getMainLooper())
 private val pacing=HomeCapturePacing()
 private var busy=false
 var status="Workspace window capture: idle";private set
 private fun guard(tasks:IntArray,physical:String):Bundle{
  val p=Parcel.obtain();val r=Parcel.obtain()
  try{
   p.writeInterfaceToken(GlassCapture.TOKEN);p.writeIntArray(tasks);p.writeString(physical)
   check(LiveAngles.captureBinder().transact(13,p,r,0)){"Workspace guard unavailable"};r.readException()
   val b=r.readBundle()?:error("No workspace guard result")
   check(b.getBoolean("ok")){b.getString("error")?:"Workspace guard rejected"};return b
  }finally{p.recycle();r.recycle()}
 }
 fun incoming(tasks:IntArray,physical:String,blur:Boolean,done:(GlassFrame?,String,Long)->Unit){
  val host=StandaloneService.instance
  if(host==null||!RuntimeConsent.ready(host)||!host.getSharedPreferences("standalone",0).getBoolean("enabled",false)){done(null,"Workspace capture inactive",100);return}
  if(busy){done(null,"Workspace capture still active",50);return}
  val selected=tasks.clone()
  val source=try{HomeCaptureSource.current()}catch(e:Exception){done(null,"Workspace source unavailable: ${e.message}",100);return}
  if(source.physical!=physical){done(null,"Workspace mapping pending",50);return}
  busy=true
  val started=SystemClock.elapsedRealtime()
  worker.execute{
   val pending=ArrayList<CaptureCompletion<AccessibilityService.ScreenshotResult>>()
   val raw=ArrayList<Bitmap>();val levels=ArrayList<Bitmap>()
   var composed:Bitmap?=null;var frame:GlassFrame?=null;var note="";var retry=50L
   try{
    val before=guard(selected,physical)
    check(before.getInt("width")==source.width&&before.getInt("height")==source.height&&before.getInt("rotation")==source.rotation){"Workspace geometry changed"}
    val panes=source.workspace(selected)
    val signature=panes.toString()
    val wait=panes.maxOf{pacing.delayMs(it.window.id,SystemClock.uptimeMillis())}
    if(wait>0){retry=wait;error("Workspace window capture interval pending (${wait}ms)")}
    // Submit the panes together; one bounded deadline, rather than one timeout per pane.
    val deadline=SystemClock.elapsedRealtime()+800
    val failures=java.util.concurrent.ConcurrentHashMap<Int,Int>()
    for(pane in panes){
     val completion=CaptureCompletion<AccessibilityService.ScreenshotResult>{it.hardwareBuffer.close()};pending.add(completion)
     pacing.submitted(pane.window.id,SystemClock.uptimeMillis())
     source.capture(pane.window,{it.run()},object:AccessibilityService.TakeScreenshotCallback{
      override fun onSuccess(result:AccessibilityService.ScreenshotResult){completion.accept(result,0)}
      override fun onFailure(code:Int){failures[pane.window.id]=code;completion.accept(null,code)}
     })
    }
    try{for((index,pane) in panes.withIndex()){
     val result=pending[index].await((deadline-SystemClock.elapsedRealtime()).coerceAtLeast(1))
     result.hardwareBuffer.use{buffer->
      val b=pane.window.bounds
      check(WorkspaceBufferGeometry.exact(buffer.width,buffer.height,b.left,b.top,b.right,b.bottom,source.width,source.height)){"Workspace buffer/window geometry mismatch: task=${pane.task} buffer=${buffer.width}x${buffer.height} bounds=$b"}
      val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)?:error("Workspace buffer unreadable")
      try{raw.add(hardware.copy(Bitmap.Config.ARGB_8888,false)?:error("Workspace pixels unreadable"))}finally{hardware.recycle()}
     }
    }}finally{failures.forEach{(window,code)->if(code==AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT)pacing.rateLimited(window,SystemClock.uptimeMillis())}}
    val after=guard(selected,physical)
    check(before.getString("workspace")==after.getString("workspace")&&after.getInt("width")==source.width&&after.getInt("height")==source.height&&after.getInt("rotation")==source.rotation&&source.workspace(selected).toString()==signature){"Workspace identity/windows changed during capture"}
    val scale=minOf(1f,1440f/maxOf(source.width,source.height))
    val w=(source.width*scale).toInt().coerceAtLeast(1);val h=(source.height*scale).toInt().coerceAtLeast(1)
    composed=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(composed!!);val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawColor(Color.BLACK)
    canvas.drawBitmap(source.wallpaper,source.crop,Rect(0,0,w,h),paint)
    for((index,pane) in panes.withIndex()){
     val b=pane.window.bounds
     canvas.drawBitmap(raw[index],null,RectF(b.left*scale,b.top*scale,b.right*scale,b.bottom*scale),paint)
    }
    if(blur){var level=composed!!;repeat(7){level=Bitmap.createScaledBitmap(level,maxOf(1,level.width/2),maxOf(1,level.height/2),true);levels.add(level)}}
    // Feed the existing bounded incoming-content check a real measurement;
    // an unknown (-1) value would unnecessarily retry a good workspace for 1.2 s.
    val sample=Bitmap.createScaledBitmap(composed!!,32,32,true)
    val colors=IntArray(1024)
    try{sample.getPixels(colors,0,32,0,0,32,32)}finally{if(sample!==composed)sample.recycle()}
    val dark=colors.count{Color.red(it)<=12&&Color.green(it)<=12&&Color.blue(it)<=12}
    note="WORKSPACE_ISOLATED_WINDOWS tasks=${selected.contentToString()} windows=$signature; wallpaper behind app panes once; native bounds; no display dim/transition layers; original pixel alpha; capture evidence only"
    frame=GlassFrame(composed!!,source.width,source.height,started,levels=levels,physicalId=physical,displayId=0,rotation=source.rotation,exclusions=note,darkSamples=dark)
    composed=null
   }catch(e:Exception){note="Workspace capture rejected: ${e.message}"}
   finally{
    pending.forEach{it.close()};raw.forEach{it.recycle()};composed?.recycle()
    if(frame==null)levels.distinct().forEach{if(!it.isRecycled)it.recycle()}
   }
   val output=frame;val message=note;val delay=retry
   main.post{busy=false;status="$message; elapsedMs=${SystemClock.elapsedRealtime()-started}";RecoveryLog.add(status);done(output,message,delay)}
  }
 }
}
