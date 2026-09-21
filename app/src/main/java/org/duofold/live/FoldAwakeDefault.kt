package org.duofold.live

import android.content.Context
import android.os.SystemClock

/** Apply only after the existing reader connects; failures never stop animation. */
internal object FoldAwakeDefault {
 private var pending=false
 private var applied=false
 private var nextAttempt=0L
 @JvmStatic fun tick(context:Context){
  val prefs=context.getSharedPreferences("standalone",0)
  val wanted=prefs.getBoolean("auto_keep_cover_awake",true)
  if(pending || SystemClock.elapsedRealtime()<nextAttempt)return
  if(wanted && applied)return
  val original=prefs.getString("previous_fold_lock",null)
  if(!wanted && original==null){applied=false;return}
  if(!LiveAngles.fresh())return
  pending=true;nextAttempt=SystemClock.elapsedRealtime()+15000
  GlassFrames.foldSetting(if(wanted)"always" else "restore",original){result->
   pending=false
   if(result.getBoolean("ok")){
    if(wanted){
     if(!prefs.contains("previous_fold_lock"))prefs.edit().putString("previous_fold_lock",result.getString("previous","null")).apply()
     applied=true
    }else{prefs.edit().remove("previous_fold_lock").apply();applied=false}
    RecoveryLog.add(if(wanted)"Default keep-cover-awake applied" else "Previous fold setting restored")
   }else RecoveryLog.add("Keep-cover-awake waiting: ${result.getString("error")}")
  }
 }
}
