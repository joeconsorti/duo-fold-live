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

/** Opt-in Camera window-first test with our own live/frozen image. */
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
 private var frozenAt=0L
 private lateinit var status:TextView
 private lateinit var start:Button
 private val trace=StringBuilder()
 private fun note(s:String){trace.append(SystemClock.elapsedRealtime()).append(": ").append(s).append('\n');status.text=s;RecoveryLog.add("Camera test: "+s)}
 private val connection=object:ServiceConnection{
  override fun onServiceConnected(n:ComponentName,b:IBinder){main.post{
   if(!running)return@post
   helper=b
   call(1){out->if(!out.getBoolean("ok"))finishTest(out.getString("error")?:"Request failed") else {note(out.getString("status")?:"Requested");poll()}}
  }}
  override fun onServiceDisconnected(n:ComponentName){main.post{finishTest("Helper disconnected")}}
 }
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/Display;","Landroid/content/res/Configuration;")
  val column=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)}
  column.addView(TextView(this).apply{text="Camera-style continuity test";textSize=22f})
  column.addView(TextView(this).apply{text="Turn Duo animation OFF. Start fully open to keep inner primary, or fully closed to keep cover primary. Observe both screens while slowly folding/unfolding. Stops after 30 seconds, leaving this page, or a primary mapping change. Exit may flash. This tests a moving image, not ordinary app transfer."})
  column.addView(CheckBox(this).apply{text="Freeze test image (screenshot comparison)";setOnCheckedChangeListener{_,v->frozen=v;frozenAt=SystemClock.elapsedRealtime()}})
  start=Button(this).apply{text="Start 30-second Camera test";setOnClickListener{begin()}};column.addView(start)
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
   primaryId=physical(primary);secondaryId=physical(secondary);primaryDisplay=primary.displayId;secondaryDisplay=secondary.displayId
   running=true;start.isEnabled=false;generation++;startAt=SystemClock.elapsedRealtime()
   note("Prepare first: primary="+primaryId+" secondary="+secondaryId+" direction="+if(inner)"inner → cover" else "cover → inner")
   val ctx=createDisplayContext(secondary).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null)
   dialog=Dialog(ctx).apply{
    window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
    window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    setContentView(Pattern(ctx,true))
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
 private fun call(code:Int,done:(Bundle)->Unit){
  val b=helper?:return;val epoch=generation
  worker.execute{
   val p=Parcel.obtain();val r=Parcel.obtain()
   val result=try{
    p.writeInterfaceToken(CameraContinuityService.TOKEN)
    if(code==1){p.writeInt(if(inner)1 else 0);p.writeStrongBinder(life);p.writeString(primaryId)}
    check(b.transact(code,p,r,0));r.readException();r.readBundle(javaClass.classLoader)?:Bundle()
   }catch(e:Exception){Bundle().apply{putString("error",e.toString())}}finally{p.recycle();r.recycle()}
   main.post{if(running&&generation==epoch)done(result)}
  }
 }
 private fun poll(){
  if(!running)return
  val dm=getSystemService(DisplayManager::class.java)
  val p=dm.getDisplay(primaryDisplay);val s=dm.getDisplay(secondaryDisplay)
  if(p==null||s==null||physical(p)!=primaryId||physical(s)!=secondaryId){finishTest("Physical mapping changed");return}
  note("Primary state="+p.state+"; secondary state="+s.state+"; frozen="+frozen+"; elapsed="+(SystemClock.elapsedRealtime()-startAt)+" ms")
  main.postDelayed({if(running)call(2){out->
   if(!out.getBoolean("ok")||!out.getBoolean("active"))finishTest(out.getString("error")?:out.getString("status")?:"Ended") else poll()
  }},500)
 }
 private fun finishTest(reason:String){
  running=false;generation++;main.removeCallbacksAndMessages(null)
  val b=helper;helper=null
  if(b!=null)worker.execute{val p=Parcel.obtain();val r=Parcel.obtain();try{p.writeInterfaceToken(CameraContinuityService.TOKEN);b.transact(3,p,r,0)}catch(_:Exception){}finally{p.recycle();r.recycle()}}
  if(bound){args?.let{runCatching{Shizuku.unbindUserService(it,connection,true)}};bound=false}
  runCatching{dialog?.dismiss()};dialog=null
  if(::start.isInitialized)start.isEnabled=true
  if(::status.isInitialized)note(reason)
 }
 override fun onPause(){finishTest("Page paused; test released");super.onPause()}
 override fun onDestroy(){finishTest("Page closed");worker.shutdown();super.onDestroy()}
 private inner class Pattern(ctx:Context,private val secondary:Boolean):View(ctx){
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  private var announced=false
  override fun onDraw(c:Canvas){
   c.drawColor(Color.rgb(25,60,95))
   val t=if(frozen)frozenAt else SystemClock.elapsedRealtime()
   paint.color=Color.CYAN;c.drawRect(((t%2000)/2000f)*width,0f,((t%2000)/2000f)*width+width/8f,height.toFloat(),paint)
   paint.color=Color.WHITE;paint.textSize=24f*resources.displayMetrics.density
   c.drawText(if(secondary)"SECOND SCREEN" else "PRIMARY STAYS ON",20f,height/2f,paint)
   if(secondary&&!announced){announced=true;post{if(running)note("Secondary first onDraw (not optical proof)")}}
   if(isAttachedToWindow)postInvalidateDelayed(16)
  }
 }
}
