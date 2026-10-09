package org.duofold.live

import android.app.AlertDialog
import android.content.*
import android.os.Build
import android.widget.Toast
import org.duofold.live.root.Shizuku

/** Root authorization (KernelSU / Magisk). The name is kept so existing call sites are unchanged. */
object ShizukuAccess {
 fun request(context: Context, code: Int): String = try {
  when {
   Shizuku.checkSelfPermission()==0 -> "Root access is granted. This does not verify the angle reader or required wallpaper."
   else -> {Shizuku.requestPermission(code); "Permission requested. Approve Duo Fold Live in the KernelSU Manager prompt. If no prompt appears, open KernelSU Manager → Superuser and allow Duo Fold Live, then tap again."}
  }
 } catch(e: Exception){"Root request failed: ${e.javaClass.simpleName}: ${e.message}"}
 fun report(context: Context): String = buildString {
  append("Duo Fold Live ${BuildConfig.VERSION_NAME} · connection report (root build)\n")
  append("${Build.MODEL} / Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}\n")
  append("Firmware: ${Build.DISPLAY}\n")
  append("Angle reader: ${LiveAngles.status}\n")
  append("${HelperBinding.report()}\n")
  append("Reader diagnostics: ${LiveAngles.readerDiagnostics}\n")
  append("Package: ${context.packageName}\n")
  append("Root granted: ${Shizuku.pingBinder()} (permission=${Shizuku.checkSelfPermission()}, 0 = granted)\n")
  append("Wallpaper profile: ${runCatching{WallpaperProfile.resolve(Build.MODEL,context.getSharedPreferences("first_run",0).getString("wallpaper_profile",""))}.getOrDefault("Selection required")}\n")
  append("Wallpaper setup model/OS eligible: ${DeviceCompatibility.isEligible(Build.MODEL, Build.VERSION.SDK_INT)}\n")
  append("Wallpaper setup verified: ${context.getSharedPreferences("first_run",0).getBoolean("wallpaper_verified",false)}\n")
  append("Display handoff: ${LiveAngles.handoffStatus}\n")
  append("Cover preview active: ${LiveAngles.coverPreview}; native inner: ${LiveAngles.nativeInner}; continuity native: ${LiveAngles.continuityNative}\n")
  append("Angle trigger: ${LiveAngles.pokeStatus}\n")
  append("Glass capture: ${GlassFrames.status}\n")
  append("Mirror: ${LiveAngles.mirrorStatus}\n")
  append("Expansion: ${LiveAngles.expansionStatus}\n")
  append("Handoff fade: ${LiveAngles.handoffFade}\n")
  append("Rotation hold: ${LiveAngles.rotationHold}\n")
  append("Bridge: ${LiveAngles.bridgeTrace}\n")
  append("Continuity: ${LiveAngles.continuityStatus}\n")
  append("Effect allowed: ${LiveAngles.effectAllowed}\n")
  append("Recent events:\n${RecoveryLog.report().lines().takeLast(40).joinToString("\n")}\n")
  append("Window host ready: ${UiHost.ready()}; overlay object: ${StandaloneService.instance!=null}; status: ${StandaloneService.instance?.status}")
 }
 fun show(context: Context, message: String) {
  AlertDialog.Builder(context).setTitle("Root connection").setMessage(message)
   .setPositiveButton("OK",null).setNeutralButton("Copy report"){_,_->copy(context)}.show()
 }
 fun copy(context: Context){
  (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Duo connection report",report(context)))
  Toast.makeText(context,"Connection report copied",Toast.LENGTH_SHORT).show()
 }
}
