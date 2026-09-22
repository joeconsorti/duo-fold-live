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
    var liveMirror by remember{mutableStateOf(prefs.getBoolean("cover_preview",true))}
    var debug by remember{mutableStateOf(prefs.getBoolean("debug_mode",false))}
    var dual by remember{mutableStateOf(prefs.getBoolean("dual",false))}
    var advanced by remember{mutableStateOf(false)}
    var setup by remember{mutableStateOf(!enabled)}
    var smoothing by remember{mutableFloatStateOf(FrameSmoothing.sanitize(prefs.getFloat("smoothing_ms",12f)))}
    var fadeSmoothing by remember{mutableFloatStateOf(FadeSettings.smoothing(prefs.getFloat("fade_smoothing_ms",FadeSettings.DEFAULT_SMOOTHING)))}
    var fadeGradualness by remember{mutableFloatStateOf(FadeSettings.gradualness(prefs.getFloat("fade_gradualness",FadeSettings.DEFAULT_GRADUALNESS)))}
    var fullResolution by remember{mutableStateOf(prefs.getBoolean("full_resolution_glass",false))}
    var intensity by remember{mutableFloatStateOf(prefs.getFloat("intensity",1f))}
    var closedThreshold by remember{mutableFloatStateOf(FoldThreshold.sanitizeClosed(prefs.getFloat("closed_threshold",1f)))}
    var threshold by remember{mutableFloatStateOf(FoldThreshold.sanitize(prefs.getFloat("open_threshold",172f)))}
    var preview by remember{mutableFloatStateOf(.45f)}
    var status by remember{mutableStateOf("Connecting…")}
    var photo by remember{mutableStateOf(BitmapFactory.decodeFile(WallpaperFiles.photoFile(this).absolutePath))}
    var photoStatus by remember{mutableStateOf("")}
    var exporting by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){while(true){enabled=prefs.getBoolean("enabled",false);status=LiveAngles.status+"\n"+LiveAngles.handoffStatus;delay(600)}}
    fun restart(){startBackground();StandaloneService.instance?.restart()}
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
       Text("Black fade smoothing · ${fadeSmoothing.roundToInt()} ms")
       Slider(value=fadeSmoothing,onValueChange={fadeSmoothing=it},valueRange=0f..120f,onValueChangeFinished={prefs.edit().putFloat("fade_smoothing_ms",fadeSmoothing).apply()})
       Text("Softens changes in the fade as you move the hinge. Higher values add more smoothing.",style=MaterialTheme.typography.bodySmall)
       Text("Black fade gradualness · ${(fadeGradualness*100).roundToInt()}%")
       Slider(value=fadeGradualness,onValueChange={fadeGradualness=it},valueRange=0f..1f,onValueChangeFinished={prefs.edit().putFloat("fade_gradualness",fadeGradualness).apply()})
       Text("0% is the original sharp fade. Higher values begin fading earlier and reveal the new screen more slowly.",style=MaterialTheme.typography.bodySmall)
       TextButton(onClick={fadeSmoothing=FadeSettings.DEFAULT_SMOOTHING;fadeGradualness=FadeSettings.DEFAULT_GRADUALNESS;prefs.edit().putFloat("fade_smoothing_ms",fadeSmoothing).putFloat("fade_gradualness",fadeGradualness).apply()}){Text("Reset black fade")}
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
        Button(onClick={val result=ShizukuAccess.request(this@MainActivity,42); if(result.startsWith("Permission requested"))android.widget.Toast.makeText(this@MainActivity,result,android.widget.Toast.LENGTH_LONG).show() else ShizukuAccess.show(this@MainActivity,result)}){Text("Authorize Shizuku")}
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
        Text("Screen continuity test",style=MaterialTheme.typography.titleMedium)
        Text("Arm, return to Home or the app you want to test, fully close, then unfold within 30 seconds. During the 20-second hold, opening past 98° tries moving real content to the inner display. The inner animation uses the transferred app. Check that content stays visible, with full-size layout, touch and navigation. Folding below 94° returns content; only one transfer per test. Temporarily requests system navigation on the inner display; restores the prior display policy after the test. Samsung may reject Home routing. Exit may still flash. Copy the connection report afterward.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=enabled&&liveMirror&&!dual,onClick={LiveAngles.startContinuityProbe()}){Text("Arm 20-second continuity test")}
        TextButton(onClick={LiveAngles.cancelContinuityProbe()}){Text("Stop continuity test")}
        Text(LiveAngles.continuityStatus,style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Fully-open threshold · ${threshold.roundToInt()}°",style=MaterialTheme.typography.titleMedium)
        Slider(value=threshold,onValueChange={threshold=it.roundToInt().toFloat()},valueRange=165f..179f,steps=13,onValueChangeFinished={prefs.edit().putFloat("open_threshold",threshold).apply();restart()})
        Text("At this angle the inner effect is completely clear. Closing starts below this threshold. Default: 172°.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={threshold=172f;prefs.edit().putFloat("open_threshold",172f).apply();restart()}){Text("Reset to 172°")}
        Text("Fully-closed threshold · ${closedThreshold.roundToInt()}°",style=MaterialTheme.typography.titleMedium)
        Slider(value=closedThreshold,onValueChange={closedThreshold=it.roundToInt().toFloat()},valueRange=1f..10f,steps=8,onValueChangeFinished={prefs.edit().putFloat("closed_threshold",closedThreshold).apply();restart()})
        Text("At or below this angle, treat the phone as fully closed and clear the cover effect. Default: 1°.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={closedThreshold=1f;prefs.edit().putFloat("closed_threshold",1f).apply();restart()}){Text("Reset to 1°")}
        Toggle("Cover preview on inner screen (experimental)",liveMirror){liveMirror=it;booleanSetting("cover_preview",it)}
        Text("For use with Dual-screen screenshot handoff OFF. Mirrors cover content without its animation. A frosted second copy is already visible on the left. At handoff, the same layout briefly holds, then fades into the inner content without a bright expansion. Screen-switch angles stay unchanged.",style=MaterialTheme.typography.bodySmall)
        Toggle("Dual-screen screenshot handoff",dual){dual=it;booleanSetting("dual",it)}
        Toggle("Debug mode · black fade",debug){debug=it;booleanSetting("debug_mode",it)}
        Text("Cover preview is on by default; screenshot handoff is off. Debug changes the shading only.",style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Keep cover awake on close",style=MaterialTheme.typography.titleMedium)
        Text("Always ON. Saved across updates and checked in the background, including when the animation is off. Reconnects automatically when authorized Shizuku becomes available.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={FoldAwakeDefault.reconnect();FoldAwakeDefault.tick(this@MainActivity);startBackground()}){Text("Recheck keep-awake now")}
        Text(FoldAwakeDefault.status,style=MaterialTheme.typography.bodySmall)
        Text(GlassFrames.status,style=MaterialTheme.typography.bodySmall)
        Text(HandoffFrames.status,style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,FoldProbeActivity::class.java))}){Text("Sensor & display diagnostics")}
       }
       OutlinedButton(onClick={val report=StandaloneService.instance?.report()?:"Duo Fold Live: accessibility disconnected";getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Duo Fold Live",report));android.widget.Toast.makeText(this@MainActivity,"Report copied",android.widget.Toast.LENGTH_SHORT).show()}){Text("Copy status report")}
      }
      Text("Duo Fold Live ${BuildConfig.VERSION_NAME} · Glass reference: chuspeeism/iphone-duo (MIT). Duo-derived diagnostics. Screen frames stay in memory.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
