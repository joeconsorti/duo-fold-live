package org.duofold.live
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable internal fun DeviceProfileChoice(onChanged:()->Unit={}){
 if(DeviceCompatibility.isRecognized(Build.MODEL))return
 val context=LocalContext.current
 val prefs=remember{context.getSharedPreferences("first_run",0)}
 var selected by remember{mutableStateOf(prefs.getString("wallpaper_profile","") ?: "")}
 Text(DeviceCompatibility.modelWarning(Build.MODEL),color=MaterialTheme.colorScheme.error)
 Text("Choose the wallpaper setup for your phone. This does not guarantee compatibility or bypass Android/API requirements.")
 for((id,label) in listOf("fold7" to "Fold 7","fold8" to "Fold 8 / Fold 8 Ultra")){
  FilterChip(selected=selected==id,onClick={
   if(selected!=id){selected=id;prefs.edit().putString("wallpaper_profile",id).putBoolean("wallpaper_verified",false).putBoolean("repair_required",true).apply();onChanged()}
  },label={Text(label)})
 }
}
