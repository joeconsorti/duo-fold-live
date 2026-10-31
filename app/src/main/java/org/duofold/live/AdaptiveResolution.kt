package org.duofold.live

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/** Process-scoped observer survives service/settings restarts; never writes the slider. */
internal object AdaptiveResolution {
 const val ENABLED_KEY="adaptive_effect_resolution"
 private const val PENDING_KEY="adaptive_resolution_recovery_pending"
 private val main=Handler(Looper.getMainLooper())
 private var power:PowerManager?=null
 private var prefs:SharedPreferences?=null
 private var policy=AdaptiveResolutionPolicy(false)
 private var thermal=-1
 private var observer="not started"
 private val recovery=Runnable { refresh() }
 private val listener=PowerManager.OnThermalStatusChangedListener { status -> observe(status,"callback") }
 @JvmStatic @Synchronized fun start(context:Context){
  if(power!=null)return
  prefs=context.applicationContext.getSharedPreferences("standalone",0)
  policy=AdaptiveResolutionPolicy(prefs!!.getBoolean(PENDING_KEY,false))
  power=context.getSystemService(PowerManager::class.java)
  observer=runCatching { power!!.addThermalStatusListener(context.mainExecutor,listener);"listening" }
   .getOrElse { "session polling (${it.javaClass.simpleName})" }
  refresh()
 }
 @Synchronized private fun observe(status:Int,source:String){
  val now=SystemClock.elapsedRealtime();val before=policy.cap();thermal=status
  policy.observe(status,now)
  if(before!=policy.cap()){
   prefs?.edit()?.putBoolean(PENDING_KEY,policy.recovering())?.apply()
   RecoveryLog.add("ADAPTIVE_RESOLUTION thermal=$status cap=${before}->${policy.cap()} source=$source; applies at next animation; selected maximum retained")
  }
  main.removeCallbacks(recovery)
  val remaining=policy.remaining(now)
  if(remaining>=0)main.postDelayed(recovery,remaining.coerceAtLeast(1))
 }
 @JvmStatic @Synchronized fun refresh(){
  observe(runCatching { power?.currentThermalStatus ?: -1 }.getOrDefault(-1),"readback")
 }
 @JvmStatic @Synchronized fun effective(p:SharedPreferences):Int =
  policy.effective(GlassEffectSettings.resolution(p),p.getBoolean(ENABLED_KEY,true))
 @JvmStatic @Synchronized fun beginAnimation(p:SharedPreferences):Int {
  refresh()
  val selected=GlassEffectSettings.resolution(p);val effective=effective(p)
  RecoveryLog.add("ADAPTIVE_RESOLUTION animation selected=$selected effective=$effective thermal=$thermal enabled=${p.getBoolean(ENABLED_KEY,true)}")
  return effective
 }
 @JvmStatic @Synchronized fun report():String {
  val p=prefs ?: return "Adaptive resolution: not started"
  val remaining=policy.remaining(SystemClock.elapsedRealtime())
  return "Adaptive resolution: enabled=${p.getBoolean(ENABLED_KEY,true)}; selected=${GlassEffectSettings.resolution(p)}%; next animation=${effective(p)}%; thermal=$thermal; cap=${policy.cap()}%; recoveryRemainingMs=$remaining; observer=$observer; changes apply between animations"
 }
}
