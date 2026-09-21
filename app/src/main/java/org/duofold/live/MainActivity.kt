package org.duofold.live
import android.content.*
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class MainActivity:ComponentActivity(){
 private val shizukuPermission=rikka.shizuku.Shizuku.OnRequestPermissionResultListener { code,result ->
  if(code==42)runOnUiThread {
   android.widget.Toast.makeText(this,if(result==0)"Shizuku authorized" else "Shizuku authorization denied. Enable Duo in Shizuku → Authorized applications.",android.widget.Toast.LENGTH_LONG).show()
  }
 }
 override fun onDestroy(){rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuPermission);super.onDestroy()}

 private fun startBackground(){
  if(checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),43)
  startForegroundService(Intent(this,FoldBackgroundService::class.java))
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermission)
  if(FirstRun.required(this)){startActivity(Intent(this,SetupActivity::class.java));finish();return}
  setContent{
   MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xffc9bdff),secondary=Color(0xff91ded2),background=Color(0xff10121c),surface=Color(0xff1b1e2b),surfaceVariant=Color(0xff252939))){
    val prefs=remember{getSharedPreferences("standalone",0)}
    val scope=rememberCoroutineScope()
    var enabled by remember{mutableStateOf(prefs.getBoolean("enabled",false))}
    var animationStyle by remember{mutableStateOf(prefs.getString("animation_style","duo") ?: "duo")}
    var liveMirror by remember{mutableStateOf(prefs.getBoolean("cover_preview",false))}
    var debug by remember{mutableStateOf(prefs.getBoolean("debug_mode",false))}
    var keepAwake by remember{mutableStateOf(prefs.getBoolean("auto_keep_cover_awake",true))}
    var dual by remember{mutableStateOf(prefs.getBoolean("dual",true))}
    var advanced by remember{mutableStateOf(false)}
    var setup by remember{mutableStateOf(!enabled)}
    var smoothing by remember{mutableFloatStateOf(FrameSmoothing.sanitize(prefs.getFloat("smoothing_ms",12f)))}
    var fullResolution by remember{mutableStateOf(prefs.getBoolean("full_resolution_glass",false))}
    var intensity by remember{mutableFloatStateOf(prefs.getFloat("intensity",1f))}
    var threshold by remember{mutableFloatStateOf(FoldThreshold.sanitize(prefs.getFloat("open_threshold",172f)))}
    var preview by remember{mutableFloatStateOf(.45f)}
    var status by remember{mutableStateOf("Connecting…")}
    var photo by remember{mutableStateOf(BitmapFactory.decodeFile(WallpaperFiles.photoFile(this).absolutePath))}
    var photoStatus by remember{mutableStateOf("")}
    var exporting by remember{mutableStateOf(false)}
    var foldStatus by remember{mutableStateOf("")}
    LaunchedEffect(Unit){while(true){enabled=prefs.getBoolean("enabled",false);status=LiveAngles.status+"\n"+LiveAngles.handoffStatus;delay(600)}}
    fun restart(){if(prefs.getBoolean("enabled",false))startBackground() else stopService(Intent(this@MainActivity,FoldBackgroundService::class.java));StandaloneService.instance?.restart()}
    fun booleanSetting(key:String,value:Boolean){prefs.edit().putBoolean(key,value).apply();restart()}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
     photoStatus="Preparing image…"
     runCatching{withContext(Dispatchers.IO){WallpaperFiles.savePhoto(this@MainActivity,uri)}}.onSuccess{photo=it;photoStatus="Photo saved as your selection. Not applied to Home yet."}.onFailure{photoStatus="Could not read image: ${it.message}"}
    }}
    Surface(Modifier.fillMaxSize()){
     Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xff30314b),Color(0xff193b40))),RoundedCornerShape(28.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
       Text("DUO / FOLD LIVE",style=MaterialTheme.typography.labelLarge,color=Color(0xffacede2))
       Text("A smoother fold.",style=MaterialTheme.typography.headlineLarge)
       Text("Screenshot handoff · Duo reference glass",style=MaterialTheme.typography.bodyLarge)
       Toggle("Enable animation",enabled){enabled=it;booleanSetting("enabled",it)}
       Text("Folding animation",style=MaterialTheme.typography.labelLarge)
       Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        FilterChip(selected=animationStyle=="duo",onClick={animationStyle="duo";prefs.edit().putString("animation_style","duo").apply();restart()},label={Text("iPhone Duo Inspired")})
        FilterChip(selected=animationStyle=="classic",onClick={animationStyle="classic";prefs.edit().putString("animation_style","classic").apply();restart()},label={Text("Classic Glass")})
       }
       Text(if(enabled)status else "Off · your phone uses its normal display behavior",style=MaterialTheme.typography.bodySmall)
      }
      SettingsCard("Animation","Your outgoing screen is frozen; the incoming screen stays live."){
       key(animationStyle){Box(Modifier.fillMaxWidth().height(150.dp)){GlassPreview(preview)}}
       Slider(value=preview,onValueChange={preview=it})
       Text("Preview selected animation",style=MaterialTheme.typography.labelMedium)
       Text("Motion smoothness · ${smoothing.roundToInt()} ms")
       Slider(value=smoothing,onValueChange={smoothing=it},valueRange=12f..120f,onValueChangeFinished={prefs.edit().putFloat("smoothing_ms",smoothing).apply();restart()})
       Text("12 ms is the current default. Higher values soften motion with more delay; they do not increase rendering FPS.",style=MaterialTheme.typography.bodySmall)
       TextButton(onClick={smoothing=12f;prefs.edit().putFloat("smoothing_ms",12f).apply();restart()}){Text("Reset smoothness")}
       Toggle("Full-resolution glass · higher GPU cost",fullResolution){fullResolution=it;booleanSetting("full_resolution_glass",it)}
       Text("Off renders the effect at half resolution in each direction to reduce GPU work. Screen content and handoff angles stay the same.",style=MaterialTheme.typography.bodySmall)
       Text("Glass strength · ${(intensity*100).roundToInt()}%")
       Slider(value=intensity,onValueChange={intensity=it},valueRange=.3f..1.5f,onValueChangeFinished={prefs.edit().putFloat("intensity",intensity).apply();restart()})
       Text("Fully open at ${threshold.roundToInt()}°. Adjust this in Advanced.",style=MaterialTheme.typography.bodySmall)
      }
      SettingsCard("Custom wallpaper","Your photo behind the icons, with Samsung’s hinge wallpaper still running underneath."){
       Button(onClick={startActivity(Intent(this@MainActivity,org.duofold.live.wallpaperlayer.WallpaperActivity::class.java))}){Text("Choose & manage custom wallpaper")}
       Text("Disable the separate Duo Wallpaper Layer app before enabling the built-in wallpaper. Choose your photo here once. Wallpaper behavior, including the existing unlock flash, is unchanged.",style=MaterialTheme.typography.bodySmall)
      }
      SettingsCard("Connection & setup","Shizuku and accessibility keep the effect running in the background."){
       TextButton(onClick={setup=!setup}){Text(if(setup)"Hide setup" else "Show setup")}
       if(setup){
        Button(onClick={val result=ShizukuAccess.request(this@MainActivity,42); if(result.startsWith("Permission requested"))android.widget.Toast.makeText(this@MainActivity,result,1).show() else ShizukuAccess.show(this@MainActivity,result)}){Text("Authorize Shizuku")}
        OutlinedButton(onClick={ShizukuAccess.show(this@MainActivity,ShizukuAccess.report(this@MainActivity))}){Text("Connection report")}
        OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}){Text("Allow overlays")}
        OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}){Text("Enable accessibility")}
        OutlinedButton(onClick={restart()}){Text("Reconnect animation")}
        Text("Keep Samsung’s interactive wallpaper on both screens. Stop any older fold helper. If accessibility is blocked: App info → ⋮ → Allow restricted settings.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}){Text("App & battery settings")}
       }
      }
      SettingsCard("Advanced","Display thresholds, fold behavior, and diagnostics."){
       TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Hide advanced settings" else "Show advanced settings")}
       if(advanced){
        Text("Fully-open threshold · ${threshold.roundToInt()}°",style=MaterialTheme.typography.titleMedium)
        Slider(value=threshold,onValueChange={threshold=it.roundToInt().toFloat()},valueRange=165f..179f,steps=13,onValueChangeFinished={prefs.edit().putFloat("open_threshold",threshold).apply();restart()})
        Text("At this angle the inner effect is completely clear. Closing starts below this threshold. Default: 172°.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={threshold=172f;prefs.edit().putFloat("open_threshold",172f).apply();restart()}){Text("Reset to 172°")}
        Toggle("Cover preview on inner screen (experimental)",liveMirror){liveMirror=it;booleanSetting("cover_preview",it)}
        Text("For use with Dual-screen screenshot handoff OFF. Mirrors cover content without its animation. A frosted second copy is already visible on the left. At handoff, the same layout briefly holds, then fades into the inner content without a bright expansion. Screen-switch angles stay unchanged.",style=MaterialTheme.typography.bodySmall)
        Toggle("Dual-screen screenshot handoff",dual){dual=it;booleanSetting("dual",it)}
        Toggle("Debug mode · black fade",debug){debug=it;booleanSetting("debug_mode",it)}
        Text("Screenshot handoff is the default. Debug changes the shading only.",style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Keep cover awake on close",style=MaterialTheme.typography.titleMedium)
        Toggle("Automatically keep cover awake",keepAwake){keepAwake=it;booleanSetting("auto_keep_cover_awake",it)}
        Text("On by default. Applied through Shizuku while the animation runs. Turn this off while connected to restore your previous fold setting. The system setting persists when the animation is stopped.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))}){Text("Samsung display settings")}
        Text("Display → Continue apps on cover screen → Always. Home may be handled separately.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={GlassFrames.foldSetting("always",null){r->if(r.getBoolean("ok")){if(!prefs.contains("previous_fold_lock"))prefs.edit().putString("previous_fold_lock",r.getString("previous","null")).apply();foldStatus="Keep-awake setting applied"}else foldStatus="Could not apply: ${r.getString("error")}"}}){Text("Apply keep-awake setting")}
        TextButton(onClick={keepAwake=false;prefs.edit().putBoolean("auto_keep_cover_awake",false).apply();val original=prefs.getString("previous_fold_lock",null);if(original==null)foldStatus="No saved setting" else GlassFrames.foldSetting("restore",original){r->if(r.getBoolean("ok")){prefs.edit().remove("previous_fold_lock").apply();foldStatus="Previous setting restored"}else foldStatus="Restore failed: ${r.getString("error")}"}}){Text("Restore previous setting")}
        if(foldStatus.isNotEmpty())Text(foldStatus,style=MaterialTheme.typography.bodySmall)
        Text(GlassFrames.status,style=MaterialTheme.typography.bodySmall)
        Text(HandoffFrames.status,style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,FoldProbeActivity::class.java))}){Text("Sensor & display diagnostics")}
       }
       OutlinedButton(onClick={val report=StandaloneService.instance?.report()?:"Duo Fold Live: accessibility disconnected";getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Duo Fold Live",report));android.widget.Toast.makeText(this@MainActivity,"Report copied",0).show()}){Text("Copy status report")}
      }
      Text("Duo Fold Live 1.5.1 · Glass reference: chuspeeism/iphone-duo (MIT). Duo-derived diagnostics. Screen frames stay in memory.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
   }
  }
 }
}
@Composable private fun SettingsCard(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit){
 Card(shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
  Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(title,style=MaterialTheme.typography.titleLarge);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);content()
  }
 }
}
@Composable private fun Toggle(label:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f).padding(top=12.dp));Switch(checked,onChange)}}
