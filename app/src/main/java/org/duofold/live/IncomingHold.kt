package org.duofold.live

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*

/** Screenshot-only endpoint fade. Independent hosts follow the incoming physical panel D1 -> D0. */
internal object IncomingHold {
 private val main=Handler(Looper.getMainLooper())
 private val events=ArrayDeque<String>()
 private val windows=ArrayList<HoldWindow>()
 private var generation=0
 private var session=0L
 private var attempted=false
 private var engaged=false
 private var lease:IncomingHoldLease?=null
 private var revealAt=0L
 private var revealDuration=292L
 private var targetWidth=0
 private var targetHeight=0
 private var context:Context?=null
 private var lastUpdate=0L
 private var lastTick=0L
 private var task=-1
 private var physical=""
 private var closing=false
 private var pendingReady=false
 private var readyCount=0
 private var checkAt=0L
 private var lastNote=""
 private var desiredAlpha=0f
 private var opacity=0f
 private var fadeNoted=false
 private fun log(s:String){events.addLast("${SystemClock.elapsedRealtime()}: $s");while(events.size>30)events.removeFirst();RecoveryLog.add("Screenshot endpoint fade: $s")}
 @JvmStatic fun report()="Screenshot endpoint fade: final 12°; endpoint-only primary handoff; opacity=$opacity; active=$engaged\nSoftware readiness/commit evidence, not optical proof:\n"+events.joinToString("\n")
 @JvmStatic fun clear(reason:String){
  generation++;main.removeCallbacks(tick);windows.forEach{it.close()};windows.clear();pendingReady=false;readyCount=0;lease=null;revealAt=0;opacity=0f;desiredAlpha=0f
  if(engaged)log("RELEASE $reason; session=$session")
  engaged=false
 }
 @JvmStatic fun update(ctx:Context,info:Bundle?,angle:Float,fresh:Boolean,allowed:Boolean){
  val now=SystemClock.elapsedRealtime();lastUpdate=now;context=ctx.applicationContext
  val prefs=ctx.getSharedPreferences("standalone",0)
  if(prefs.getBoolean("screenshot_native_test",false)){if(engaged)clear("native incoming app test");return}
  if(!allowed||!fresh||!prefs.getBoolean("enabled",false)||!prefs.getBoolean("dual",false)||!prefs.getBoolean("screenshot_camera_startup",false)||ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked||!ctx.getSystemService(PowerManager::class.java).isInteractive){clear("disabled, stale, or locked");return}
  val next=info?.getLong("session")?:0L
  if(next!=session){clear("new session");session=next;attempted=false}
  if(next==0L||info==null)return
  val closed=prefs.getFloat("closed_threshold",2f);val open=prefs.getFloat("open_threshold",172f)
  if(engaged&&lease?.started()==true&&EndpointFadePolicy.reversedAfterEndpoint(closing,angle,closed,open)){clear("reopened/reversed after endpoint");return}
  if(!engaged){
   if(attempted||!info.getBoolean("active"))return
   val target=info.getString("physical")?:"";if(target.isEmpty())return
   val service=StandaloneService.instance?:return
   attempted=true;task=info.getInt("task",-1);physical=target;closing=info.getBoolean("closing")
   targetWidth=info.getInt("width");targetHeight=info.getInt("height")
   revealDuration=FadeSettings.reveal(prefs.getFloat("fade_gradualness",FadeSettings.DEFAULT_GRADUALNESS)).coerceIn(180L,450L)
   engaged=true;lease=IncomingHoldLease(now);checkAt=0;lastNote="";fadeNoted=false;generation++;lastTick=now
   try{
    val dm=ctx.getSystemService(DisplayManager::class.java)
    for(id in 0..1){val d=dm.getDisplay(id)?:throw IllegalStateException("Missing D$id");val window=HoldWindow(service,d,physical);windows.add(window);window.show()}
    log("PREATTACHED transparent accessibility hosts; session=$session; incoming=$physical; task=$task; direction=${if(closing)"closing" else "opening"}")
   }catch(e:Exception){log("ATTACH failed ${e.javaClass.simpleName}");clear("attachment failure");return}
   main.post(tick)
  }
  desiredAlpha=EndpointFadePolicy.alpha(closing,angle,closed,open)
  if(desiredAlpha>0f&&!fadeNoted){fadeNoted=true;log("FADE starts angle=$angle; closed=$closed; open=$open; band=12°")}
  if((closing&&angle<=FoldThreshold.sanitizeClosed(closed))||(!closing&&angle>=FoldThreshold.sanitize(open)))handoff(now,"hinge endpoint")
  else if(!info.getBoolean("active"))handoff(now,"Android/concurrent release")
 }
 private fun handoff(now:Long,reason:String){if(lease?.handoff(now)==true){desiredAlpha=1f;opacity=1f;windows.forEach{it.refresh(1f)};log("HANDOFF $reason; bounded reveal deadline=${lease?.deadline()}")}}
 private fun reveal(now:Long,reason:String){if(revealAt==0L){revealAt=now;log("REVEAL $reason; duration=$revealDuration ms")}}
 private val tick=object:Runnable{override fun run(){
  if(!engaged)return
  val ctx=context?:return;val now=SystemClock.elapsedRealtime();val hold=lease?:return
  if(hold.expired(now)){clear("bounded deadline");return}
  if(now-lastUpdate>750){clear("connection loss");return}
  if(ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked||!ctx.getSystemService(PowerManager::class.java).isInteractive){clear("locked or asleep");return}
  val primary=ctx.getSystemService(DisplayManager::class.java).getDisplay(0)
  if(primary!=null&&identity(primary)==physical)handoff(now,"incoming physical panel on D0")
  if(hold.started()&&now>=hold.deadline()-revealDuration)reveal(now,"bounded readiness fallback")
  if(revealAt>0){
   val t=((now-revealAt).toFloat()/revealDuration).coerceIn(0f,1f);opacity=1f-t*t*(3f-2f*t)
   if(t>=1f){clear("smooth reveal complete");return}
  }else if(hold.started())opacity=1f
  else {
   val dt=(now-lastTick).coerceIn(0L,64L).toFloat()
   opacity+=(desiredAlpha-opacity)*(1f-kotlin.math.exp(-dt/45f))
   if(desiredAlpha==0f&&opacity<.002f)opacity=0f
  }
  lastTick=now;windows.forEach{it.refresh(opacity)}
  if(hold.started()&&revealAt==0L&&targetWidth>0&&targetHeight>0&&primary!=null&&identity(primary)==physical&&primary.state==Display.STATE_ON&&!pendingReady&&now>=checkAt){
   pendingReady=true;checkAt=now+150;val epoch=generation
   val done:(Boolean,String)->Unit={ready,note->
    if(epoch==generation){pendingReady=false;if(note!=lastNote){lastNote=note;log("D0 readiness: $note")};readyCount=if(ready)readyCount+1 else 0
     if(readyCount>=2)main.postDelayed({if(epoch==generation&&engaged)reveal(SystemClock.elapsedRealtime(),"incoming app ready twice")},64)
    }
   }
   if(task>=0)GlassFrames.incomingReady(task,physical,targetWidth,targetHeight,done)
   else{val size=android.graphics.Point();primary.getRealSize(size);done(EndpointReturnPolicy.ready(physical,identity(primary),primary.state,targetWidth,targetHeight,size.x,size.y),"Home/system destination geometry")}
  }
  main.postDelayed(this,16)
 }}
 private fun identity(d:Display?):String=if(d==null)"" else runCatching{Display::class.java.getMethod("getUniqueId").invoke(d).toString()}.getOrDefault("")
 private class HoldWindow(service:StandaloneService,d:Display,val target:String){
  private val id=d.displayId
  private val dm=service.getSystemService(DisplayManager::class.java)
  private val wc=service.createDisplayContext(d).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null)
  private val wm=wc.getSystemService(WindowManager::class.java)
  private val view=View(wc)
  private var added=false
  private var lastPhysical=""
  private var committed=false
  init{
   view.setBackgroundColor(Color.BLACK);view.alpha=0f;view.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
   view.viewTreeObserver.addOnPreDrawListener{
    // Exclude the mask from live captures to prevent black feedback into the animation.
    runCatching{val root=View::class.java.getDeclaredMethod("getViewRootImpl").invoke(view);val sc=root.javaClass.getMethod("getSurfaceControl").invoke(root) as SurfaceControl;if(sc.isValid)GlassFrames.surface(view,sc)}
    true
   }
   view.viewTreeObserver.addOnDrawListener{if(view.alpha>=.999f&&!committed){committed=true;view.viewTreeObserver.registerFrameCommitCallback{if(added)log("BLACK buffer submitted D$id; physical=${identity(dm.getDisplay(id))}; not optical proof")}}}
  }
  fun show(){
   val params=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT)
   params.alpha=1f;params.windowAnimations=0;params.dimAmount=0f;params.setFitInsetsTypes(0);params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;params.title="Duo screenshot endpoint fade"
   wm.addView(view,params);added=true
  }
  fun refresh(alpha:Float){
   val actual=identity(dm.getDisplay(id));if(actual!=lastPhysical){lastPhysical=actual;committed=false;log("HOST D$id physical=$actual; target=$target")}
   view.alpha=if(actual==target)alpha else 0f
   if(added)view.invalidate()
  }
  fun close(){GlassFrames.surface(view,null);view.alpha=0f;if(added)runCatching{wm.removeViewImmediate(view)};added=false}
 }
}
