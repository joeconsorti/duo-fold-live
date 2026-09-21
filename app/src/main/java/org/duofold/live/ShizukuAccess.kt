package org.duofold.live

import android.app.AlertDialog
import android.content.*
import android.os.Build
import android.widget.Toast
import rikka.shizuku.Shizuku

/** Authorization is independent of wallpaper compatibility and ADB transport. */
object ShizukuAccess {
 fun request(context: Context, code: Int): String = try {
  when {
   !Shizuku.pingBinder() -> "Duo has not received Shizuku’s connection. Check that Shizuku says Running (pairing alone is not enough). Keep both apps in the same Android profile. If it is already running, restart Shizuku, then reopen Duo."
   Shizuku.isPreV11() -> "This Shizuku version is unsupported. Update official Shizuku."
   Shizuku.checkSelfPermission()==0 -> "Shizuku is authorized. Wallpaper setup is a separate step."
   Shizuku.shouldShowRequestPermissionRationale() -> "Authorization was previously denied. Open Shizuku → Authorized applications and enable Duo Fold Live, then return here."
   else -> {Shizuku.requestPermission(code); "Permission requested. Select Allow in Shizuku’s dialog. If no dialog appears, open Shizuku → Authorized applications and enable Duo Fold Live."}
  }
 } catch(e: Exception){"Shizuku request failed: ${e.javaClass.simpleName}: ${e.message}"}
 fun report(context: Context): String = buildString {
  append("Duo Fold Live 1.6.0-alpha.2 · connection report\n")
  append("${Build.MODEL} / Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}\n")
  append("Package: ${context.packageName}\n")
  append("Official Shizuku installed in this profile: "+runCatching{context.packageManager.getPackageInfo("moe.shizuku.privileged.api",0);true}.getOrDefault(false)+"\n")
  val alive=runCatching{Shizuku.pingBinder()}.getOrDefault(false)
  append("Binder received and alive: $alive\n")
  if(alive) runCatching {
   append("Server version: ${Shizuku.getVersion()}\n")
   append("Server UID: ${Shizuku.getUid()} (ADB expects 2000)\n")
   append("Permission: ${Shizuku.checkSelfPermission()} (0 = authorized)\n")
   append("Previously denied: ${Shizuku.shouldShowRequestPermissionRationale()}\n")
  }.onFailure{append("Connection check failed: ${it.javaClass.simpleName}: ${it.message}\n")}
  append("Wallpaper setup model/OS eligible: ${DeviceCompatibility.isEligible(Build.MODEL, Build.VERSION.SDK_INT)}\n")
  append("Wallpaper setup verified: ${context.getSharedPreferences("first_run",0).getBoolean("wallpaper_verified",false)}\n")
  append("Wireless and USB start the same Shizuku ADB service. An unsupported wallpaper profile is not an authorization failure.")
 }
 fun show(context: Context, message: String) {
  AlertDialog.Builder(context).setTitle("Shizuku connection").setMessage(message)
   .setPositiveButton("OK",null).setNeutralButton("Copy report"){_,_->copy(context)}
   .setNegativeButton("Open Shizuku"){_,_->
    val intent=context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    if(intent!=null)runCatching{context.startActivity(intent)}
   }.show()
 }
 fun copy(context: Context){
  (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Duo connection report",report(context)))
  Toast.makeText(context,"Connection report copied",Toast.LENGTH_SHORT).show()
 }
}
