package org.duofold.live
import android.content.*
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable internal fun ShizukuHelp(){
 val c=LocalContext.current
 var expanded by remember{mutableStateOf(false)}
 var extra by remember{mutableStateOf(false)}
 fun open(action:String){runCatching{c.startActivity(Intent(action))}}
 SettingsCard("Trouble with Shizuku / Fold shutting off?","Keep your connection ready at home, away from Wi-Fi, and after sleep."){
  TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Close troubleshooting" else "Connection & offline guide")}
  if(expanded){
   ShizukuPlusDownload()
   Text("1 · Start with a working connection",style=MaterialTheme.typography.titleMedium)
   Text("In Shizuku or Shizuku+, start in ADB mode and authorize Duo Fold Live. Return here and check that live angles change when you move the hinge. Shizuku+ is an independent fork; available controls vary by version.")
   Text("2 · Optional local TCP for use away from Wi-Fi",style=MaterialTheme.typography.titleMedium)
   Text("On a Windows PC, download Google’s Platform Tools. Open PowerShell in the extracted platform-tools folder. Connect the unlocked phone by USB, enable USB debugging, and accept the phone’s authorization prompt. Run these commands on the PC:")
   Text(".\\adb.exe devices\n.\\adb.exe tcpip 5555",fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace)
   TextButton(onClick={c.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PC PowerShell commands",".\\adb.exe devices\n.\\adb.exe tcpip 5555"))}){Text("Copy PC commands")}
   Text("The first command must show ‘device’, not ‘unauthorized’. If unauthorized, unlock the phone and approve the USB prompt. If needed, run .\\adb.exe kill-server, reconnect, and try again. macOS/Linux: use ./adb instead.")
   Text("On the PHONE, use Shizuku+’s local/manual ADB connection option if available: host 127.0.0.1, port 5555. Start the service and enable its watchdog / auto-reconnect. Use the instructions shown by your installed Shizuku+ version; the TCP command alone does not start Shizuku. Standard Wireless debugging pairing uses a different, changing port.")
   Text("Test before relying on it: unplug USB, turn Wi-Fi off, and confirm Shizuku+ still says Running and Duo still receives live angles. Localhost points to the phone itself; it can work without home Wi-Fi while ADB remains running. It is not a guarantee against Samsung stopping the service.")
   Text("TCP debugging can also listen on network interfaces; 127.0.0.1 as a client address does not make the server loopback-only. Use trusted networks. To leave TCP mode, reconnect USB and run .\\adb.exe -d usb, then restart Shizuku using your preferred method.",style=MaterialTheme.typography.bodySmall)
   Text("3 · Prevent unnecessary background shutdowns",style=MaterialTheme.typography.titleMedium)
   Text("• Settings → Apps → Shizuku+ → Battery → Unrestricted. Do the same for Duo Fold Live.\n• Battery → Background usage limits: remove both from Sleeping / Deep sleeping apps and add them to Never sleeping apps where available. You can leave global app sleeping enabled.\n• Keep Developer options and USB debugging enabled. In Developer options, enable Disable ADB authorization timeout if available.\n• Set Default USB configuration to No data transfer / Charging only.\n• If Recent apps offers Keep open, you may use it for Shizuku+. It does not guarantee the service survives.")
   OutlinedButton(onClick={open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)}){Text("Open Developer options")}
   OutlinedButton(onClick={runCatching{c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}")))}}){Text("Duo app & battery settings")}
   Text("4 · Test locking and reboot recovery",style=MaterialTheme.typography.titleMedium)
   Text("Lock the phone, leave it for a few minutes, then unlock and fold/unfold. Duo reconnects when an authorized Shizuku service returns. After a reboot, expect to start Shizuku again and possibly repeat TCP setup. Pairing and battery preferences normally remain saved; repeat authorization only if requested. Auto-start/watchdog cannot guarantee recovery when ADB itself is unavailable.")
   TextButton(onClick={extra=!extra}){Text(if(extra)"Hide additional steps" else "Still stopping? Additional Samsung steps")}
   if(extra){
    Text("Optional, device-dependent steps from the supplied troubleshooting notes:",style=MaterialTheme.typography.titleSmall)
    Text("• USB workaround: temporarily switch USB debugging OFF, inspect Default USB configuration, and choose Debugging only IF that option exists; then switch USB debugging back ON and restart Shizuku. This is not verified as a universal One UI fix. If absent, use No data transfer.\n• Disable child process restrictions: if your Developer options includes it, test enabling it when Shizuku reports killed processes. It affects system-wide process limits and can increase resource use; restore it if it does not help.\n• Auto Blocker: Samsung can block USB commands. If it prevents setup, review Settings → Security and privacy → Auto Blocker. Disabling it reduces protection; it is not a proven cure for lock-screen disconnects.\n• Check that Pause app activity if unused is off for Shizuku+ and any watchdog app. Menu names vary.")
    Text("External watchdogs (Tasker / Automate) are optional advanced tools. Prefer Shizuku+’s built-in watchdog first. A watchdog needs a working, authorized ADB connection; a plain Termux shell cannot necessarily launch Shizuku. Running adb connect 127.0.0.1:5555 is a connection attempt, not a wake lock. Do not rely on a one-second screen-lock script, a global phantom-process override, or an alleged ‘keep wireless debugging without Wi-Fi’ switch as guaranteed fixes.")
   }
   OutlinedButton(onClick={ShizukuAccess.show(c,ShizukuAccess.report(c))}){Text("Check Shizuku connection")}
   Text("For a report: include phone model, Android / One UI version, Shizuku version and startup method, what stopped working, and Copy status report below. Never share pairing codes or private keys.",style=MaterialTheme.typography.bodySmall)
   TextButton(onClick={runCatching{c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/guide/setup/")))}}){Text("Official Shizuku guide")}
   TextButton(onClick={runCatching{c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/thejaustin/ShizukuPlus/releases")))}}){Text("Shizuku+ project & version-specific help")}
  }
 }
}

@Composable internal fun ShizukuPlusDownload(){
 val context=LocalContext.current
 Text("If you installed Shizuku, uninstall it first to avoid conflicts. Install Shizuku+ below, start it using wireless or USB debugging (ADB mode), then authorize Duo again.",style=MaterialTheme.typography.bodySmall)
 OutlinedButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/thejaustin/ShizukuPlus/releases")))}.onFailure{android.widget.Toast.makeText(context,"Could not open the link. Visit github.com/thejaustin/ShizukuPlus/releases in your browser.",android.widget.Toast.LENGTH_LONG).show()}}){Text("Download Shizuku+")}
}
