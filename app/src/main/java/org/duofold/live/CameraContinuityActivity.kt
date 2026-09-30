package org.duofold.live
import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import android.widget.*
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/** Opt-in Camera window-first test with live compositor content or an in-memory snapshot. */
class CameraContinuityActivity:Activity(){
 private val main=Handler(Looper.getMainLooper())
 private val worker=Executors.newSingleThreadExecutor()
 private val life=Binder()
 private var helper:IBinder?=null
 private var args:Shizuku.UserServiceArgs?=null
 private var bound=false
 private var dialog:Dialog?=null
 private var running=false
 private var generation=0
 private var primaryId=""
 private var secondaryId=""
 private var primaryDisplay=-1
 private var secondaryDisplay=-1
 private var inner=false
 private var frozen=false
 private var startAt=0L
 private var capture:IBinder?=null
 private var content:SurfaceView?=null
 private var snapshot:ImageView?=null
 private var contentPending=false
 private var contentReady=false
 private var snapshotReady=false
 private var testFrozen=false
 private var backgroundAllowed=false
 private var contentEpoch=0
 private var nativeReveal=false
 private var revealed=false
 private var revealScheduled=false
 private lateinit var nativeOption:CheckBox
 private lateinit var freezeOption:CheckBox
 private lateinit var status:TextView
 private lateinit var start:Button
 private val trace=StringBuilder()
 private fun note(s:String){trace.append(SystemClock.elapsedRealtime()).append(": ").append(s).append('\n');status.text=s;RecoveryLog.add("Camera test: "+s)}
 private val connection=object:ServiceConnection{
  override fun onServiceConnected(n:ComponentName,b:IBinder){main.post{
   if(!running)return@post
   helper=b
   call(1){out->if(!out.getBoolean("ok"))finishTest(out.getString("error")?:"Request failed") else {capture=out.getBinder("capture");note(out.getString("status")?:"Requested");poll()}}
  }}
  override fun onServiceDisconnected(n:ComponentName){main.post{finishTest("Helper disconnected")}}
 }
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/Display;","Landroid/content/res/Configuration;")
  val column=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)}
  column.addView(TextView(this).apply{text="Camera-style continuity test";textSize=22f})
  column.addView(TextView(this).apply{text="Turn Duo animation OFF. Start fully open to keep inner primary, or fully closed to keep cover primary. Observe both screens while slowly folding/unfolding. Start the test, then tap Show Home and open an app. Secondary content is read-only and center-fitted, not a native app transfer. Stops after 30 seconds, locking, or a primary mapping change. Full closing/exit may flash."})
  freezeOption=CheckBox(this).apply{text="Freeze real content 5 seconds after Show Home";setOnCheckedChangeListener{_,v->frozen=v}};column.addView(freezeOption)
  nativeOption=CheckBox(this).apply{text="Reveal native inner content 5 seconds after Show Home";setOnCheckedChangeListener{_,v->if(v)freezeOption.isChecked=false}};column.addView(nativeOption)
  freezeOption.setOnCheckedChangeListener{_,v->frozen=v;if(v)nativeOption.isChecked=false}
  column.addView(TextView(this).apply{text="Native reveal: start fully closed, tap Show Home, then unfold before 5 seconds. Duo removes its image while keeping the transparent secondary window and the same primary display. Check the inner layout and touch; a blank screen is a useful result. No tasks are moved."})
  start=Button(this).apply{text="Start 30-second Camera test";setOnClickListener{begin()}};column.addView(start)
  column.addView(Button(this).apply{text="Show Home during test";setOnClickListener{
   if(running){backgroundAllowed=true;startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));if(!revealScheduled&&(testFrozen||nativeReveal)){revealScheduled=true;val epoch=generation;main.postDelayed({if(running&&generation==epoch){if(nativeReveal)revealNative() else freezeContent()}},5000)}}
  }})
  column.addView(Button(this).apply{text="Stop test";setOnClickListener{finishTest("Stopped by user")}})
  column.addView(Button(this).apply{text="Copy Camera test report";setOnClickListener{
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Camera test","Duo "+BuildConfig.VERSION_NAME+"\n"+Build.MODEL+"\n"+trace.toString()))
   Toast.makeText(this@CameraContinuityActivity,"Report copied",Toast.LENGTH_SHORT).show()
  }})
  status=TextView(this).apply{text="Idle"};column.addView(status)
  column.addView(Pattern(this,false),LinearLayout.LayoutParams(-1,180));setContentView(ScrollView(this).apply{addView(column)})
 }
 private fun physical(d:Display)=Display::class.java.getMethod("getUniqueId").invoke(d).toString()
 private fun begin(){
  if(running)return
  if(getSharedPreferences("standalone",0).getBoolean("enabled",false)){note("Turn Duo animation OFF first");return}
  if(!android.provider.Settings.canDrawOverlays(this)){note("Allow display over other apps first");return}
  try{
   check(Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==android.content.pm.PackageManager.PERMISSION_GRANTED){"Authorize Shizuku first"}
   val dm=getSystemService(DisplayManager::class.java)
   val primary=dm.getDisplay(Display.DEFAULT_DISPLAY)?:error("Primary display missing")
   check(display?.displayId==primary.displayId){"Open this page on the primary screen"}
   val secondary=dm.getDisplays("com.samsung.android.hardware.display.category.BUILTIN").firstOrNull{it.displayId!=primary.displayId}?:error("Second built-in display unavailable")
   val size=Point();primary.getRealSize(size)
   inner=try{resources.configuration.javaClass.getField("semDisplayDeviceType").getInt(resources.configuration)==0}catch(_:Exception){size.x>size.y}
   check(!nativeOption.isChecked||!inner){"Native inner reveal: start fully closed on the cover"}
   primaryId=physical(primary);secondaryId=physical(secondary);primaryDisplay=primary.displayId;secondaryDisplay=secondary.displayId
   check(!getSystemService(KeyguardManager::class.java).isKeyguardLocked){"Unlock first"}
   running=true;nativeReveal=nativeOption.isChecked;revealed=false;revealScheduled=false;nativeOption.isEnabled=false;start.isEnabled=false;freezeOption.isEnabled=false;testFrozen=frozen;contentReady=false;snapshotReady=false;contentPending=false;backgroundAllowed=false;generation++;startAt=SystemClock.elapsedRealtime()
   note("Prepare first: primary="+primaryId+" secondary="+secondaryId+" direction="+if(inner)"inner → cover" else "cover → inner")
   val ctx=createDisplayContext(secondary).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null)
   dialog=Dialog(ctx).apply{
    window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
    window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    val frame=FrameLayout(ctx).apply{setBackgroundColor(Color.BLACK)}
    content=SurfaceView(ctx).also{view->
     view.holder.addCallback(object:SurfaceHolder.Callback{
      override fun surfaceCreated(h:SurfaceHolder)=Unit
      override fun surfaceChanged(h:SurfaceHolder,f:Int,w:Int,hg:Int){contentEpoch++;contentReady=false;prepareContent()}
      override fun surfaceDestroyed(h:SurfaceHolder){contentEpoch++;contentReady=false;if(running)call(5){} }
     });frame.addView(view,FrameLayout.LayoutParams(-1,-1))
    }
    snapshot=ImageView(ctx).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(Color.BLACK);visibility=View.GONE};frame.addView(snapshot,FrameLayout.LayoutParams(-1,-1))
    setContentView(frame)
    show();window?.setLayout(-1,-1)
   }
   prepared()
   val epoch=generation
   main.postDelayed({if(running&&generation==epoch&&helper==null)finishTest("Helper connection timed out")},5000)
   main.postDelayed({if(running&&generation==epoch)finishTest("30-second deadline")},30000)
  }catch(e:Exception){finishTest(e.message?:e.toString())}
 }
 private fun prepared(){
  if(!running||bound)return
  note("Secondary window shown before state request; waiting for draw/activation")
  args=Shizuku.UserServiceArgs(ComponentName(this,CameraContinuityService::class.java)).daemon(false).processNameSuffix("camera_continuity").version(BuildConfig.VERSION_CODE)
  bound=true
  try{Shizuku.bindUserService(args!!,connection)}catch(e:Exception){finishTest(e.toString())}
 }
 private fun call(code:Int,write:(Parcel)->Unit={},done:(Bundle)->Unit){
  val b=helper?:return;val epoch=generation
  worker.execute{
   val p=Parcel.obtain();val r=Parcel.obtain()
   val result=try{
    p.writeInterfaceToken(CameraContinuityService.TOKEN)
    if(code==1){p.writeInt(if(inner)1 else 0);p.writeStrongBinder(life);p.writeString(primaryId)}
    write(p)
    check(b.transact(code,p,r,0));r.readException();r.readBundle(javaClass.classLoader)?:Bundle()
   }catch(e:Exception){Bundle().apply{putString("error",e.toString())}}finally{p.recycle();r.recycle()}
   main.post{if(running&&generation==epoch)done(result)}
  }
 }
 private fun poll(){
  if(!running)return
  if(getSharedPreferences("standalone",0).getBoolean("enabled",false)){finishTest("Normal animation enabled; test released");return}
  if(!getSystemService(PowerManager::class.java).isInteractive||getSystemService(KeyguardManager::class.java).isKeyguardLocked){finishTest("Screen off or locked");return}
  val dm=getSystemService(DisplayManager::class.java)
  val p=dm.getDisplay(primaryDisplay);val s=dm.getDisplay(secondaryDisplay)
  if(p==null||s==null||physical(p)!=primaryId||physical(s)!=secondaryId){finishTest("Physical mapping changed");return}
  if(s.state==Display.STATE_ON)prepareContent()
  note("Primary state="+p.state+"; secondary state="+s.state+"; mirror="+contentReady+"; snapshot="+snapshotReady+"; nativeReveal="+revealed+"; elapsed="+(SystemClock.elapsedRealtime()-startAt)+" ms")
  main.postDelayed({if(running)call(2){out->
   if(!out.getBoolean("ok")||!out.getBoolean("active"))finishTest(out.getString("error")?:out.getString("status")?:"Ended") else poll()
  }},500)
 }
 private fun prepareContent(){
  val view=content?:return
  if(!running||revealed||helper==null||contentPending||contentReady||snapshotReady||!view.surfaceControl.isValid||view.width<=0||view.height<=0)return
  val revision=contentEpoch
  contentPending=true
  call(4,{p->p.writeTypedObject(view.surfaceControl,0);p.writeInt(view.width);p.writeInt(view.height)}){out->
   contentPending=false
   if(revision==contentEpoch){contentReady=out.getBoolean("ok");note(out.getString("content")?:out.getString("error")?:"Mirror attached")}
  }
 }
 private fun revealNative(){
  if(!running||revealed)return
  val dm=getSystemService(DisplayManager::class.java)
  if(!contentReady||contentPending||dm.getDisplay(secondaryDisplay)?.state!=Display.STATE_ON){
   note("Native reveal skipped: inner live mirror not ready. Stop and retry, unfolding before 5 seconds.");return
  }
  // Block all mirror reattachment before detaching it on the helper thread.
  revealed=true;contentEpoch++
  call(6){out->
   if(!out.getBoolean("ok")){finishTest(out.getString("error")?:"Native reveal failed");return@call}
   contentReady=false;snapshotReady=false
   snapshot?.setImageDrawable(null);snapshot?.visibility=View.GONE
   content?.visibility=View.GONE
   // Keep the same attached window, but make all its pixels invisible. Alpha zero
   // also lets touches pass through an untrusted application overlay on Android.
   dialog?.window?.let{window->
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
    val attributes=window.attributes;attributes.alpha=0f;attributes.dimAmount=0f;window.attributes=attributes
   }
   note("Native underlay exposed; primary request retained; no task/focus changes. "+out.getString("diagnostics"))
   val epoch=generation
   main.postDelayed({if(running&&generation==epoch)call(7){report->note("After reveal: "+(report.getString("diagnostics")?:report.getString("error"))) }},1500)
  }
 }
 private fun freezeContent(){
  val remote=capture;val view=content
  if(remote==null||view==null){note("Snapshot unavailable: helper or surface not ready; retry test");return}
  if(snapshotReady||!view.surfaceControl.isValid)return
  val epoch=generation
  worker.execute{
   val p=Parcel.obtain();val r=Parcel.obtain()
   val out=try{
    p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(1);p.writeTypedObject(view.surfaceControl,0);p.writeInt(primaryDisplay)
    check(remote.transact(3,p,r,0));r.readException();r.readBundle(Bitmap::class.java.classLoader)?:Bundle()
   }catch(e:Exception){Bundle().apply{putString("error",e.toString())}}finally{p.recycle();r.recycle()}
   val bitmap=out.getParcelable("bitmap",Bitmap::class.java)
   main.post{
    if(!running||epoch!=generation){bitmap?.recycle();return@post}
    if(out.getBoolean("ok")&&bitmap!=null){
     snapshotReady=true;snapshot?.setImageBitmap(bitmap);snapshot?.visibility=View.VISIBLE
     call(5){contentReady=false;note("Frozen primary snapshot displayed (in-memory only)")}
    }else note(out.getString("error")?:"Snapshot failed; live mirror retained")
   }
  }
 }
 private fun finishTest(reason:String){
  running=false;generation++;main.removeCallbacksAndMessages(null)
  val b=helper;helper=null;capture=null;backgroundAllowed=false;contentPending=false;contentEpoch++
  if(b!=null)worker.execute{val p=Parcel.obtain();val r=Parcel.obtain();try{p.writeInterfaceToken(CameraContinuityService.TOKEN);b.transact(3,p,r,0)}catch(_:Exception){}finally{p.recycle();r.recycle()}}
  if(bound){args?.let{runCatching{Shizuku.unbindUserService(it,connection,true)}};bound=false}
  runCatching{dialog?.dismiss()};dialog=null;content=null;snapshot=null
  if(::start.isInitialized)start.isEnabled=true
  if(::nativeOption.isInitialized)nativeOption.isEnabled=true
  if(::freezeOption.isInitialized)freezeOption.isEnabled=true
  if(::status.isInitialized)note(reason)
 }
 override fun onPause(){if(!backgroundAllowed)finishTest("Page paused; test released");super.onPause()}
 override fun onDestroy(){finishTest("Page closed");worker.shutdown();super.onDestroy()}
 private inner class Pattern(ctx:Context,private val secondary:Boolean):View(ctx){
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  private var announced=false
  override fun onDraw(c:Canvas){
   c.drawColor(Color.rgb(25,60,95))
   val t=SystemClock.elapsedRealtime()
   paint.color=Color.CYAN;c.drawRect(((t%2000)/2000f)*width,0f,((t%2000)/2000f)*width+width/8f,height.toFloat(),paint)
   paint.color=Color.WHITE;paint.textSize=24f*resources.displayMetrics.density
   c.drawText(if(secondary)"SECOND SCREEN" else "PRIMARY STAYS ON",20f,height/2f,paint)
   if(secondary&&!announced){announced=true;post{if(running)note("Secondary first onDraw (not optical proof)")}}
   if(isAttachedToWindow)postInvalidateDelayed(16)
  }
 }
}
