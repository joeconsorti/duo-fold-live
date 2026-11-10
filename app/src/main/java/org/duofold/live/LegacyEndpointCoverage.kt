package org.duofold.live

import android.app.KeyguardManager
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*

/** Plain selected-app coverage for the short D1 -> D0 remap, with a non-renewable lease. */
internal object LegacyEndpointCoverage {
 private val main=Handler(Looper.getMainLooper())
 private val windows=ArrayList<CoverWindow>()
 private val gate=B8IncomingCapturePolicy()
 private var epoch=0
 private var session=0L
 private var task=-1
 private var target=""
 private var closing=false
 private var width=0
 private var height=0
 private var frame:GlassFrame?=null
 private var capturePending=false
 private var readyPending=false
 private var readyCount=0
 private var retryAt=0L
 private var checkAt=0L
 private var updatedAt=0L
 private var preparedAt=0L
 private var deadline=0L
 private var finished=false
 private var active=false
 private fun discard(image:GlassFrame?){image?.let{(it.levels+it.bitmap).distinct().forEach{bitmap->if(!bitmap.isRecycled)bitmap.recycle()}}}
 private var service:StandaloneService?=null
 private val events=ArrayDeque<String>()
 private fun log(message:String){events.addLast("${SystemClock.elapsedRealtime()}: $message");while(events.size>24)events.removeFirst();RecoveryLog.add("Legacy endpoint coverage: $message")}
 @JvmStatic fun report()="Legacy endpoint coverage: plain selected-app snapshot; active=$active; deadline=$deadline; software evidence only\n"+events.joinToString("\n")
 @JvmStatic fun clear(reason:String){epoch++;main.removeCallbacks(tick);windows.forEach{it.close()};windows.clear();frame=null;capturePending=false;readyPending=false;active=false;deadline=0;finished=true;if(preparedAt!=0L)log("RELEASE $reason");preparedAt=0}
 @JvmStatic fun update(ctx:android.content.Context,info:Bundle?,angle:Float,fresh:Boolean,allowed:Boolean){
  val prefs=ctx.getSharedPreferences("standalone",0)
  if(HandoffVisualPolicy.ANIMATION_ENABLED||!allowed||!prefs.getBoolean("enabled",false)||!prefs.getBoolean("dual",false)||!prefs.getBoolean("screenshot_native_test",false)||ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked){if(windows.isNotEmpty())clear("disabled or locked");return}
  val next=info?.getLong("session")?:0L
  if(next==0L||info==null)return
  val now=SystemClock.elapsedRealtime();updatedAt=now
  if(next!=session){clear("new session");session=next;finished=false;retryAt=0;readyCount=0;checkAt=0}
  if(finished)return
  if(windows.isEmpty()){
   if(!info.getBoolean("active")||!info.getBoolean("routed")||info.getInt("task",-1)<0)return
   val host=StandaloneService.instance?:return;service=host
   task=info.getInt("task");target=info.getString("physical")?:"";closing=info.getBoolean("closing");width=info.getInt("width");height=info.getInt("height")
   if(target.isEmpty()||width<=0||height<=0)return
   preparedAt=now;gate.reset(now)
   try{for(id in 0..1){val d=host.getSystemService(DisplayManager::class.java).getDisplay(id)?:error("Display missing");CoverWindow(host,d).also{windows.add(it);it.show()}}}
   catch(e:Exception){clear("attachment failed: ${e.javaClass.simpleName}");return}
   log("PREPARE task=$task physical=$target; transparent hosts; no folding effects");main.post(tick)
  }
  if(deadline==0L&&fresh&&EndpointReturnPolicy.armPower(closing,angle,FoldThreshold.sanitize(prefs.getFloat("open_threshold",172f)))){
   deadline=now+5000;active=true;log("HOLD endpoint approach; deadline=$deadline; captured=${frame!=null}");windows.forEach{it.refresh()}
  }
  // Captures are bounded preparation retries, not a continuous screen recording.
  if(frame==null&&!capturePending&&info.getBoolean("active")&&now>=retryAt){
   capturePending=true;retryAt=now+150;val generation=epoch
   GlassFrames.incoming(task,target,1){image,note->
    if(generation!=epoch){discard(image);return@incoming}
    if(generation==epoch){capturePending=false
     if(image!=null&&EndpointReturnPolicy.ready(target,image.physicalId,2,width,height,image.width,image.height)&&gate.evaluate(image.darkSamples,SystemClock.elapsedRealtime())!=B8IncomingCapturePolicy.RETRY){frame=image;windows.forEach{it.refresh()};log("CAPTURE selected app ready; ${image.width}x${image.height}; dark=${image.darkSamples}")}
     else {discard(image);if(SystemClock.elapsedRealtime()-preparedAt>2500&&frame==null){log("No usable endpoint capture: $note");clear("capture unavailable")}}
    }
   }
  }
 }
 private val tick=object:Runnable{override fun run(){
  val host=service?:return;if(windows.isEmpty())return
  val now=SystemClock.elapsedRealtime()
  if(host.getSystemService(KeyguardManager::class.java).isKeyguardLocked){clear("keyguard");return}
  if(now-updatedAt>750||now-preparedAt>=30000||(deadline!=0L&&now>=deadline)){clear("bounded deadline or connection loss");return}
  windows.forEach{it.refresh()}
  val d=host.getSystemService(DisplayManager::class.java).getDisplay(0)
  if(active&&frame!=null&&d!=null&&PanelIdentity.identity(d)==target&&d.state==Display.STATE_ON&&!readyPending&&now>=checkAt){
   readyPending=true;checkAt=now+150;val generation=epoch
   GlassFrames.incomingReady(task,target,width,height){ready,note->
    if(generation==epoch){readyPending=false;readyCount=if(ready)readyCount+1 else 0
     if(readyCount>=2){log("READY twice for exact app on destination; $note");clear("immediate native reveal")}
    }
   }
  }
  main.postDelayed(this,25)
 }}
 private class CoverWindow(host:StandaloneService,d:Display){
  private val id=d.displayId
  private val dm=host.getSystemService(DisplayManager::class.java)
  private val wc=host.createDisplayContext(d).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null)
  private val wm=wc.getSystemService(WindowManager::class.java)
  private var added=false
  private val view=object:View(wc){override fun onDraw(canvas:Canvas){super.onDraw(canvas);frame?.let{drawScreenshotBackdrop(canvas,it,width,height)}}}
  init{view.alpha=0f;view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
   view.viewTreeObserver.addOnPreDrawListener{runCatching{val root=View::class.java.getDeclaredMethod("getViewRootImpl").invoke(view);val sc=root.javaClass.getMethod("getSurfaceControl").invoke(root) as SurfaceControl;if(sc.isValid)GlassFrames.surface(view,sc)};true}
  }
  fun show(){val lp=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT)
   lp.windowAnimations=0;lp.dimAmount=0f;lp.setFitInsetsTypes(0);lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;lp.title="Duo legacy endpoint app coverage";wm.addView(view,lp);added=true
  }
  fun refresh(){view.alpha=if(active&&frame!=null&&PanelIdentity.identity(dm.getDisplay(id))==target)1f else 0f;view.invalidate()}
  fun close(){view.alpha=0f;GlassFrames.surface(view,null);if(added)runCatching{wm.removeViewImmediate(view)};added=false}
 }
}
