package org.duofold.live

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject

/** Journal before a temporary display-policy change; recovery survives helper/app restart. */
internal object InnerDecorRecovery {
 private const val KEY="pending_inner_decor_restore"
 private var busy=false
 private var ownsLease=false
 private var attempted=false
 private var readback=false
 private var unsupported=false
 private var apisCollected=false
 private var apiRetryAt=0L
 @JvmField var apiStatus="Available display APIs: awaiting helper"
 private var retryAt=0L
 @JvmField var status="Inner system UI test idle"
 @JvmStatic fun tick(context:Context){
  val prefs=context.getSharedPreferences("standalone",0)
  val active=LiveAngles.continuityNative
  if(!active)attempted=false
  if(busy)return
  // Firmware inspection has no display, fold-state or restoration prerequisite.
  if(!apisCollected&&SystemClock.elapsedRealtime()>=apiRetryAt){
   busy=true
   FoldSettingsClient.request(context,"decor_apis",null){r->
    busy=false;apiRetryAt=SystemClock.elapsedRealtime()+15000
    apisCollected=r.getBoolean("ok")
    apiStatus=if(apisCollected)"Available display APIs: ${r.getString("displayApis")}" else "Display API inspection pending: ${r.getString("error")}"
   };return
  }
  if(SystemClock.elapsedRealtime()<retryAt)return
  val saved=prefs.getString(KEY,null)
  // A journal from an older process must be restored even if a new test starts.
  if(saved!=null&&(!active||!ownsLease)){
   busy=true
   FoldSettingsClient.request(context,"decor_restore",saved){r->
    busy=false;retryAt=SystemClock.elapsedRealtime()+(if(r.getBoolean("busy"))2000 else 60000)
    if(r.getBoolean("ok")){
     prefs.edit().remove(KEY).commit();ownsLease=false
     retryAt=0
     status="Original inner display policy confirmed; navigation=${r.getBoolean("nav")}";RecoveryLog.add(status)
    }else{status="Inner display restoration pending: ${r.getString("error")}";RecoveryLog.add(status)}
   };return
  }
  if(active&&saved!=null&&ownsLease&&!readback){
   readback=true;busy=true
   FoldSettingsClient.request(context,"decor_read",saved){r->
    busy=false;status=if(r.getBoolean("ok"))"Inner system UI readback: supported=${r.getBoolean("decor")}; navigation=${r.getBoolean("nav")}; temporary policy active"
     else "Inner system UI readback failed: ${r.getString("error")}"
    RecoveryLog.add(status)
   };return
  }
  if(!active||attempted||unsupported||saved!=null)return
  attempted=true;busy=true
  FoldSettingsClient.request(context,"decor_read",null){r->
   if(!r.getBoolean("ok")){busy=false;attempted=false;retryAt=SystemClock.elapsedRealtime()+2000;status="Inner system UI query failed: ${r.getString("error")}";RecoveryLog.add(status);return@request}
   if(!r.getBoolean("setterAvailable")){
    busy=false;unsupported=true
    status="Inner system UI unsupported: decoration setter unavailable; no change requested. Available display APIs: ${r.getString("displayApis")}";RecoveryLog.add(status);return@request
   }
   if(r.getBoolean("original")){busy=false;status="Inner system decorations already enabled; navigation=${r.getBoolean("nav")}";return@request}
   if(!LiveAngles.continuityNative){busy=false;return@request}
   val journal=JSONObject().put("physical",r.getString("physical")).put("original",r.getBoolean("original")).toString()
   if(!prefs.edit().putString(KEY,journal).commit()){busy=false;status="Display policy unchanged: could not save restoration journal";return@request}
   ownsLease=true;readback=false
   FoldSettingsClient.request(context,"decor_enable",journal){changed->
    busy=false;retryAt=SystemClock.elapsedRealtime()+2000
    status=if(changed.getBoolean("ok"))"Inner system decorations requested; supported=${changed.getBoolean("decor")}; navigation=${changed.getBoolean("nav")}. Samsung inner Home layout still requires handset verification."
      else "Inner system UI request rejected: ${changed.getString("error")}"
    RecoveryLog.add(status)
    // Keep the journal even on failure: the remote write may have completed before timeout.
    if(!changed.getBoolean("ok"))ownsLease=false
   }
  }
 }
}
