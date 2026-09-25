package org.duofold.live
import android.content.Context
import android.os.SystemClock
import rikka.shizuku.Shizuku

/** Runs only through the background supervisor, with the angle reader stopped. */
internal object RotationMigration {
 private const val DONE="rotation_repair_v1_complete"
 private var pending=false
 private var nextAttempt=0L
 private var failures=0
 private var pauseAt=-1L
 @JvmField var status="Automatic rotation repair: not checked"
 @JvmStatic fun needed(context:Context):Boolean {
  val prefs=context.getSharedPreferences("standalone",0)
  if(prefs.getBoolean(DONE,false)){status="Automatic rotation repair: complete (will not repeat)";return false}
  val info=context.packageManager.getPackageInfo(context.packageName,0)
  if(!RotationMigrationPolicy.needed(false,info.firstInstallTime,info.lastUpdateTime)){
   // Fresh installs cannot contain a hold left by an older installation.
   if(prefs.edit().putBoolean(DONE,true).commit())status="Automatic rotation repair: fresh install; not needed"
   return false
  }
  return true
 }
 @JvmStatic fun tick(context:Context){
  val now=SystemClock.elapsedRealtime()
  if(pauseAt<0){pauseAt=now;status="Automatic rotation repair: waiting for previous hold to release";return}
  if(pending || now-pauseAt<1500 || now<nextAttempt)return
  if(!runCatching{Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==0}.getOrDefault(false)){
   status="Automatic rotation repair: waiting for authorized Shizuku";return
  }
  val app=context.applicationContext
  val install=app.packageManager.getPackageInfo(app.packageName,0).firstInstallTime.toString()
  pending=true;status="Automatic rotation repair: running; animation temporarily paused"
  FoldSettingsClient.request(app,"rotation_migrate",install){result->
   pending=false
   if(result.getBoolean("ok")&&app.getSharedPreferences("standalone",0).edit().putBoolean(DONE,true).commit()){
    status="Automatic rotation repair: verified complete (will not repeat)"
    RecoveryLog.add(status)
    // Never overwrite enabled: preserve changes the user made while repair ran.
    if(app.getSharedPreferences("standalone",0).getBoolean("enabled",false))StandaloneService.instance?.restart()
   }else{
    failures=(failures+1).coerceAtMost(5)
    nextAttempt=SystemClock.elapsedRealtime()+RotationMigrationPolicy.retryDelay(failures)
    status="Automatic rotation repair: retry pending — ${result.getString("error") ?: "completion could not be saved"}"
    RecoveryLog.add(status)
   }
  }
 }
}
