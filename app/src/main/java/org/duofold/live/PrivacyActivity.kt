package org.duofold.live

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Disclosures are separate, with explicit acceptance before access is used. */
class PrivacyActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  FoldAwakeDefault.reconnect();FoldAwakeDefault.tick(this@PrivacyActivity)
  setContent{DuoTheme{
   var revision by remember{mutableIntStateOf(0)}
   val access=remember(revision){RuntimeConsent.accepted(this@PrivacyActivity,"accessibility")}
   val screen=remember(revision){RuntimeConsent.accepted(this@PrivacyActivity,"screen")}
   var awake by remember{mutableStateOf(FoldAwakeDefault.enabled(this@PrivacyActivity))}
   var awakeStatus by remember{mutableStateOf(FoldAwakeDefault.status)}
   LaunchedEffect(Unit){while(true){awakeStatus=FoldAwakeDefault.status;delay(500)}}
   Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    Text("Privacy & controls",style=MaterialTheme.typography.headlineMedium)
    Text("Choose whether to enable Duo’s access. Declining leaves the animation off. You can withdraw consent and stop background work here at any time.")
    Button(onClick={disclose("accessibility"){revision++}},enabled=!access){Text(if(access)"Accessibility use accepted" else "Review accessibility use")}
    Button(onClick={disclose("screen"){revision++}},enabled=!screen){Text(if(screen)"Screen processing accepted" else "Review screen processing")}
    if(access)OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}){Text("Android accessibility settings")}
    Text("Keep cover awake on close",style=MaterialTheme.typography.titleMedium)
    Text("Optional. Changes Samsung’s fold-lock setting and uses a bounded wake hold during closing. May increase battery use. Power-button locking is unchanged. Turning this off restores the setting recorded before Duo changed it, if it has not since been changed elsewhere. For upgrades from older builds, the original setting is unknown; check Samsung’s Lock when folded setting yourself.")
    Switch(checked=awake,enabled=access&&screen,onCheckedChange={awake=it;FoldAwakeDefault.setEnabled(this@PrivacyActivity,it);if(it)org.duofold.live.wallpaperlayer.WallpaperRestore.resume(this@PrivacyActivity)})
    Text(awakeStatus,style=MaterialTheme.typography.bodySmall)
    Button(onClick={RuntimeControls.stopAll(this@PrivacyActivity);awake=false;revision++}){Text("Stop all Duo background work")}
    OutlinedButton(onClick={RuntimeConsent.withdraw(this@PrivacyActivity);awake=false;revision++}){Text("Withdraw both consents & stop")}
    Text("Stopping retains your photos and preferences. Android permissions remain granted until you revoke them in Settings. Samsung’s underlying home wallpaper remains installed; choose another wallpaper in Samsung Settings if desired.",style=MaterialTheme.typography.bodySmall)
    if(access&&screen)Button(onClick={org.duofold.live.wallpaperlayer.WallpaperRestore.resume(this@PrivacyActivity);StandaloneService.instance?.restart();finish()}){Text("Done")}
    else TextButton(onClick={finish()}){Text("Back without enabling")}
    HorizontalDivider()
    Text(remember{assets.open("privacy.txt").bufferedReader().use{it.readText()}},style=MaterialTheme.typography.bodySmall)
   }
  }}
 }
 private fun disclose(scope:String,accepted:()->Unit){
  val accessibility=scope=="accessibility"
  AlertDialog.Builder(this@PrivacyActivity).setTitle(if(accessibility)"Accessibility service disclosure" else "Screen processing disclosure")
   .setMessage(if(accessibility)
    "Duo uses Android’s AccessibilityService to host non-interactive animation overlays across both displays while fold animation is enabled, including over other apps. It is not an accessibility tool for people with disabilities. For unlocked Home transitions with custom wallpaper, the service captures the focused launcher window so icons can be composited above your wallpaper. It does not read node text, perform gestures, or automate taps. Ordinary app images are obtained separately through your authorized Shizuku or root helper. The optional custom-wallpaper instrumentation helper uses UiAutomation window events, root package names, focus and geometry to identify Home and place your photo. Processing stays local; images are saved only when you arm image diagnostics. Accept to permit this use; enabling the service in Android Settings is a separate step."
    else "While fold animation is enabled and the phone is unlocked, Duo’s authorized Shizuku or root helper captures and mirrors displayed app content, which may contain personal information, to render live glass and fold transitions. Frames are processed locally in memory; Duo does not save them as recordings or upload them. Secure/protected captures are rejected. The helper also reads filtered Samsung wallpaper/hinge diagnostics and controls display handoffs. This privileged capture route does not show Android’s MediaProjection prompt. Accept only if you want this screen processing. Stop all or withdraw consent here to stop it; chosen wallpaper photos and diagnostic reports are stored separately as described below.")
   .setNegativeButton("Decline",null)
   .setPositiveButton("Accept"){_,_->RuntimeConsent.accept(this@PrivacyActivity,scope);accepted()}.show()
 }
}
