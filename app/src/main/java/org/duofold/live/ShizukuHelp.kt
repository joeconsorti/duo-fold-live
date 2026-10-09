package org.duofold.live
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable internal fun ShizukuHelp(){
 val c=LocalContext.current
 var expanded by remember{mutableStateOf(false)}
 SettingsCard("Trouble connecting?","This build uses root (KernelSU) instead of Shizuku."){
  TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Close troubleshooting" else "Root connection guide")}
  if(expanded){
   Text("1 · Grant root",style=MaterialTheme.typography.titleMedium)
   Text("Open KernelSU Manager → Superuser and make sure Duo Fold Live is allowed. Then tap Authorize / check connection here.")
   Text("2 · Keep it running",style=MaterialTheme.typography.titleMedium)
   Text("Settings → Apps → Duo Fold Live → Battery → Unrestricted. No start-up step is needed after a reboot; root is requested again automatically.")
   Text("3 · Still no live angles?",style=MaterialTheme.typography.titleMedium)
   Text("Check that Samsung’s interactive fold wallpaper is applied to both home screens (Repair required wallpaper setup), then copy the connection report.")
   OutlinedButton(onClick={ShizukuAccess.show(c,ShizukuAccess.report(c))}){Text("Check root connection")}
  }
 }
}

@Composable internal fun ShizukuPlusDownload(){ /* Not needed in the root build. */ }
