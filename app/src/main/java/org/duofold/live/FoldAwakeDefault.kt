package org.duofold.live

import android.content.Context
import android.os.SystemClock
import rikka.shizuku.Shizuku

/** Verify actual settings, not a once-per-process success flag. Never wakes a locked phone. */
internal object FoldAwakeDefault {
 private var pending=false
 private var nextAttempt=0L
 private var previousWanted:Boolean?=null
 @JvmField var status="Keep-awake awaiting verification"
 @JvmStatic fun reconnect(){nextAttempt=0L;status="Keep-awake rechecking after reconnect / wake"}
 @JvmStatic fun tick(context:Context){
  val prefs=context.getSharedPreferences("standalone",0)
  val wanted=prefs.getBoolean("auto_keep_cover_awake",true)
  if(previousWanted!=wanted){previousWanted=wanted;nextAttempt=0L}
  if(pending || SystemClock.elapsedRealtime()<nextAttempt)return
  val original=prefs.getString("previous_fold_lock",null)
  if(!wanted && original==null){status="Keep-awake disabled by user";return}
  if(!runCatching{Shizuku.pingBinder() && Shizuku.checkSelfPermission()==0}.getOrDefault(false)){
   status="Keep-awake saved; waiting for authorized Shizuku";return
  }
  // A fresh hinge sample is not required to repair the fold setting.
  pending=true;nextAttempt=SystemClock.elapsedRealtime()+15000
  GlassFrames.foldSetting(if(wanted)"always" else "restore",original){result->
   pending=false
   if(result.getBoolean("ok")){
    if(wanted){
     if(!prefs.contains("previous_fold_lock"))prefs.edit().putString("previous_fold_lock",result.getString("previous","null")).apply()
     status="Keep-awake verified: ${result.getString("value")}; checked ${SystemClock.elapsedRealtime()} ms"
    }else{prefs.edit().remove("previous_fold_lock").apply();status="Previous fold setting restored"}
   }else{status="Keep-awake retry: ${result.getString("error")}";RecoveryLog.add(status)}
  }
 }
}
