package org.duofold.live

import android.content.Context
import android.os.SystemClock
import rikka.shizuku.Shizuku

/** Always-on fold policy. Does not override lock/power-button sleep or wake a locked phone. */
internal object FoldAwakeDefault {
 private var pending=false
 private var nextAttempt=0L
 private var epoch=0L
 @JvmField var status="Keep-awake ON; awaiting verification"
 @JvmStatic fun persist(context:Context){
  val prefs=context.getSharedPreferences("standalone",0)
  if(!prefs.contains("auto_keep_cover_awake") || !prefs.getBoolean("auto_keep_cover_awake",true)){
   if(!prefs.edit().putBoolean("auto_keep_cover_awake",true).commit())RecoveryLog.add("Keep-awake ON; preference save needs retry")
  }
 }
 @JvmStatic fun reconnect(){epoch++;nextAttempt=0L;status="Keep-awake ON; rechecking connection / setting"}
 @JvmStatic fun tick(context:Context){
  persist(context)
  if(pending || SystemClock.elapsedRealtime()<nextAttempt)return
  if(!runCatching{Shizuku.pingBinder() && Shizuku.checkSelfPermission()==0}.getOrDefault(false)){
   status="Keep-awake ON saved; waiting for authorized Shizuku; automatic retry active";return
  }
  pending=true
  val requestEpoch=epoch
  FoldSettingsClient.request(context,"always",null){result->
   pending=false
   // A change/wake/disconnect during the request requires another immediate check.
   nextAttempt=if(requestEpoch!=epoch)0L else SystemClock.elapsedRealtime()+if(result.getBoolean("ok"))15000L else 2000L
   if(requestEpoch!=epoch)status="Keep-awake ON; state changed during check, verifying again"
   else if(result.getBoolean("ok"))status="Keep-awake ON verified: ${result.getString("value")}; checked ${SystemClock.elapsedRealtime()} ms"
   else{status="Keep-awake ON; automatic retry: ${result.getString("error")}";RecoveryLog.add(status)}
  }
 }
}
