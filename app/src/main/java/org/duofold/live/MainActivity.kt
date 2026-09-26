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
  if(intent.action=="org.duofold.live.SUPPORT"){intent.action=null;SupportPrompts.open(this)}
  rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuPermission)
  if(FirstRun.required(this)){startActivity(Intent(this,SetupActivity::class.java));finish();return}
  setContent{
   MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xff9dc4ff),secondary=Color(0xffa8d5c2),background=Color(0xff080a0e),surface=Color(0xff171a20),surfaceVariant=Color(0xff242830))){
    val prefs=remember{getSharedPreferences("standalone",0)}
    val scope=rememberCoroutineScope()
    var enabled by remember{mutableStateOf(prefs.getBoolean("enabled",false))}
    var mode by remember{mutableStateOf(AnimationModePolicy.sanitize(prefs.getString("animation_mode",AnimationModePolicy.DEFAULT)))}
    var legacy by remember{mutableStateOf(false)}
    var supportBanner by remember{mutableStateOf(false)}
    var developer by remember{mutableStateOf(false)}
    var animationStyle by remember{mutableStateOf(prefs.getString("animation_style","duo") ?: "duo")}
    var liveMirror by remember{mutableStateOf(prefs.getBoolean("cover_preview",true))}
    var debug by remember{mutableStateOf(prefs.getBoolean("debug_mode",false))}
    var dual by remember{mutableStateOf(prefs.getBoolean("dual",false))}
    var advanced by remember{mutableStateOf(false)}
    var setup by remember{mutableStateOf(!enabled)}
    var smoothing by remember{mutableFloatStateOf(FrameSmoothing.sanitize(prefs.getFloat("smoothing_ms",30f)))}
    var fadeSmoothing by remember{mutableFloatStateOf(FadeSettings.smoothing(prefs.getFloat("fade_smoothing_ms",FadeSettings.DEFAULT_SMOOTHING)))}
    var fadeGradualness by remember{mutableFloatStateOf(FadeSettings.gradualness(prefs.getFloat("fade_gradualness",FadeSettings.DEFAULT_GRADUALNESS)))}
    var fullResolution by remember{mutableStateOf(prefs.getBoolean("full_resolution_glass",false))}
    var antialias by remember{mutableStateOf(prefs.getBoolean("antialias_enabled",true))}
    var antialiasStrength by remember{mutableFloatStateOf(RenderQuality.antialias(prefs.getFloat("antialias_strength",.35f)))}
    var contentFps by remember{mutableIntStateOf(RenderQuality.fps(prefs.getInt("content_fps",120)))}
    var blurStrength by remember{mutableFloatStateOf(RenderQuality.blur(prefs.getFloat("blur_strength",.3f)))}
    var seamOffset by remember{mutableFloatStateOf(RenderQuality.seam(prefs.getFloat("seam_offset",.07f)))}
    var intensity by remember{mutableFloatStateOf(prefs.getFloat("intensity",.5f))}
    var closedThreshold by remember{mutableFloatStateOf(FoldThreshold.sanitizeClosed(prefs.getFloat("closed_threshold",2f)))}
    var threshold by remember{mutableFloatStateOf(FoldThreshold.sanitize(prefs.getFloat("open_threshold",172f)))}
    var status by remember{mutableStateOf("Connecting…")}
    var photo by remember{mutableStateOf(BitmapFactory.decodeFile(WallpaperFiles.photoFile(this).absolutePath))}
    var photoStatus by remember{mutableStateOf("")}
    var rotationRepair by remember{mutableStateOf("")}
    var repairingRotation by remember{mutableStateOf(false)}
    var exporting by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){while(true){enabled=prefs.getBoolean("enabled",false);status=LiveAngles.status+"\n"+LiveAngles.handoffStatus;if(!supportBanner && SupportPrompts.inAppDue(this@MainActivity)){supportBanner=true;SupportPrompts.seenInApp(this@MainActivity)};delay(600)}}
    fun restart(){startBackground();StandaloneService.instance?.restart()}
    fun selectMode(value:String){mode=value;animationStyle="duo";dual=false;liveMirror=true;prefs.edit().putString("animation_mode",value).putBoolean("dual",false).putBoolean("cover_preview",true).putString("animation_style","duo").putBoolean("debug_mode",false).apply();debug=false;restart()}
    fun booleanSetting(key:String,value:Boolean){prefs.edit().putBoolean(key,value).apply();restart()}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
     photoStatus="Preparing image…"
     runCatching{withContext(Dispatchers.IO){WallpaperFiles.savePhoto(this@MainActivity,uri)}}.onSuccess{photo=it;photoStatus="Photo saved as your selection. Not applied to Home yet."}.onFailure{photoStatus="Could not read image: ${it.message}"}
    }}
    Surface(Modifier.fillMaxSize()){
     Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xff1d3048),Color(0xff172128))),RoundedCornerShape(28.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
       Text("Duo Fold Live",style=MaterialTheme.typography.headlineLarge)
       Text("Make every fold feel fluid.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
       Toggle("Enable animation",enabled){
        if(it && (!Settings.canDrawOverlays(this@MainActivity) || StandaloneService.instance==null)){
         startActivity(Intent(this@MainActivity,SetupActivity::class.java).putExtra("repair",true))
        }else{enabled=it;booleanSetting("enabled",it)}
       }
       Text(if(enabled){if(LiveAngles.fresh()) "Connected · ready to fold" else LiveAngles.status} else "Off · your phone uses its normal display behavior",style=MaterialTheme.typography.bodySmall)
      }
      if(enabled && status.startsWith("Waiting for fresh wallpaper angles")){
       SettingsCard("No live hinge angles yet","Shizuku is connected, but Samsung’s wallpaper has not supplied fresh angles."){
        Text("Move the hinge with the phone unlocked. If this remains, repair the required wallpaper setup. A custom photo alone does not provide angles.")
        Button(onClick={startActivity(Intent(this@MainActivity,SetupActivity::class.java).putExtra("repair",true))}){Text("Repair wallpaper & check permissions")}
        OutlinedButton(onClick={ShizukuAccess.show(this@MainActivity,ShizukuAccess.report(this@MainActivity))}){Text("Connection report")}
       }
      }
      DeviceProfileChoice{startActivity(Intent(this@MainActivity,SetupActivity::class.java).putExtra("repair",true))}
      if(supportBanner){
       SettingsCard("Enjoying Duo Fold Live?","Your support helps fund more updates. Always optional."){
        Button(onClick={SupportPrompts.open(this@MainActivity);supportBanner=false}){Text("Yes, support on Ko-fi")}
        TextButton(onClick={supportBanner=false}){Text("Not today")}
        TextButton(onClick={SupportPrompts.snooze(this@MainActivity);supportBanner=false}){Text("Snooze for 3 weeks")}
        TextButton(onClick={supportBanner=false;SupportPrompts.stop(this@MainActivity)}){Text("Don’t ask again")}
       }
      }
      SettingsCard("Folding animation styles","Choose how your Fold moves between screens."){
       listOf("windowed" to "Inspired by iPhone Duo with even more windowed glass. Default.","duo_classic" to "Native inner-screen glass with a black reveal. No mirrored cover or frosted preview.","fold_only" to "Windowed Glass when closing. Normal behavior when opening.","unfold_only" to "Windowed Glass when opening. Normal behavior when closing.").forEach{(id,description)->
        Surface(onClick={selectMode(id)},shape=RoundedCornerShape(20.dp),color=if(mode==id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()){
         Row(Modifier.padding(14.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
          RadioButton(selected=mode==id,onClick={selectMode(id)})
          Column(Modifier.weight(1f).padding(start=8.dp)){Text(AnimationModePolicy.label(id),style=MaterialTheme.typography.titleMedium);Text(description,style=MaterialTheme.typography.bodySmall)}
         }
        }
       }
      }
      ShizukuHelp()
      SettingsCard("Fine-tune your animation","Your saved glass and fade settings apply to each style."){

       Text("Motion smoothness · ${smoothing.roundToInt()} ms")
       Slider(value=smoothing,onValueChange={smoothing=it},valueRange=12f..120f,onValueChangeFinished={prefs.edit().putFloat("smoothing_ms",smoothing).apply();restart()})
       Text("30 ms is the current default. Higher values soften motion with more delay; they do not increase rendering FPS.",style=MaterialTheme.typography.bodySmall)
       TextButton(onClick={smoothing=30f;prefs.edit().putFloat("smoothing_ms",30f).apply();restart()}){Text("Reset smoothness")}
       Text("Blur amount · ${(blurStrength*100).roundToInt()}%")
       Slider(value=blurStrength,onValueChange={blurStrength=it},valueRange=0f..3f,onValueChangeFinished={prefs.edit().putFloat("blur_strength",blurStrength).apply();restart()})
       Text("Stronger frost softens pixelation, especially on the inner display. Default: 30%.",style=MaterialTheme.typography.bodySmall)
       TextButton(onClick={blurStrength=.3f;prefs.edit().putFloat("blur_strength",.3f).apply();restart()}){Text("Reset blur amount")}
       Text("Glass strength · ${(intensity*100).roundToInt()}%")
       Slider(value=intensity,onValueChange={intensity=it},valueRange=.3f..1.5f,onValueChangeFinished={prefs.edit().putFloat("intensity",intensity).apply();restart()})
       TextButton(onClick={intensity=.5f;prefs.edit().putFloat("intensity",.5f).apply();restart()}){Text("Reset glass strength")}
       Text("Black fade smoothing · ${fadeSmoothing.roundToInt()} ms")
       Slider(value=fadeSmoothing,onValueChange={fadeSmoothing=it},valueRange=0f..120f,onValueChangeFinished={prefs.edit().putFloat("fade_smoothing_ms",fadeSmoothing).apply()})
       Text("Softens changes in the fade as you move the hinge. Higher values add more smoothing.",style=MaterialTheme.typography.bodySmall)
       Text("Black fade gradualness · ${(fadeGradualness*100).roundToInt()}%")
       Slider(value=fadeGradualness,onValueChange={fadeGradualness=it},valueRange=0f..1f,onValueChangeFinished={prefs.edit().putFloat("fade_gradualness",fadeGradualness).apply()})
       Text("0% is the original sharp fade. Higher values begin fading earlier and reveal the new screen more slowly.",style=MaterialTheme.typography.bodySmall)
       TextButton(onClick={fadeSmoothing=FadeSettings.DEFAULT_SMOOTHING;fadeGradualness=FadeSettings.DEFAULT_GRADUALNESS;prefs.edit().putFloat("fade_smoothing_ms",fadeSmoothing).putFloat("fade_gradualness",fadeGradualness).apply()}){Text("Reset black fade")}
       Toggle("Full-resolution glass · higher GPU cost",fullResolution){fullResolution=it;booleanSetting("full_resolution_glass",it)}
       Text("Off renders the effect at half resolution in each direction to reduce GPU work. Screen content and handoff angles stay the same.",style=MaterialTheme.typography.bodySmall)
       Text("Fully open at ${threshold.roundToInt()}°. Adjust this in Advanced.",style=MaterialTheme.typography.bodySmall)
      }
      SettingsCard("Custom wallpaper","Your photo on Home, with live hinge support in the background."){
       Button(onClick={startActivity(Intent(this@MainActivity,org.duofold.live.wallpaperlayer.WallpaperActivity::class.java))}){Text("Choose & manage custom wallpaper")}
       Text("Choose one photo for both screens and preview each crop. Your photo and enabled choice stay saved across updates.",style=MaterialTheme.typography.bodySmall)
      }
      SettingsCard("Connection & setup","Shizuku and accessibility keep the effect running in the background."){
       TextButton(onClick={setup=!setup}){Text(if(setup)"Hide setup" else "Show setup")}
       if(setup){
        ShizukuPlusDownload()
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,SetupActivity::class.java).putExtra("repair",true))}){Text("Repair required wallpaper setup")}
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
        Toggle("Texture anti-aliasing",antialias){antialias=it;booleanSetting("antialias_enabled",it)}
        if(antialias){
         Text("Anti-aliasing strength · ${(antialiasStrength*100).roundToInt()}%")
         Slider(value=antialiasStrength,onValueChange={antialiasStrength=it},valueRange=.2f..0.5f,onValueChangeFinished={prefs.edit().putFloat("antialias_strength",antialiasStrength).apply();restart()})
         TextButton(onClick={antialiasStrength=.35f;prefs.edit().putFloat("antialias_strength",.35f).apply();restart()}){Text("Reset anti-aliasing")}
        }
        Text("Lightweight texture filtering for every glass style, independent of blur and glass strength. Reduces jagged detail and shimmer; cannot restore missing capture detail.",style=MaterialTheme.typography.bodySmall)
        Text("Live content frame rate",style=MaterialTheme.typography.titleMedium)
        for(rate in listOf(12,60,120))TextButton(onClick={contentFps=rate;prefs.edit().putInt("content_fps",rate).apply();restart()}){Text((if(contentFps==rate)"✓ " else "")+when(rate){12->"Legacy · approximately 12 FPS";120->"120 FPS · experimental";else->"60 FPS · lower GPU cost"})}
        Text("Uses your selected capture target without automatic refresh-rate switching. Actual FPS depends on device speed and temperature. Status reports show measured captures per second. Screenshot mode intentionally holds a still image.",style=MaterialTheme.typography.bodySmall)
        Text("Center-edge blur offset · ${(seamOffset*100).roundToInt()}%")
        Slider(value=seamOffset,onValueChange={seamOffset=it},valueRange=0f..0.15f,onValueChangeFinished={prefs.edit().putFloat("seam_offset",seamOffset).apply();restart()})
        Text("Moves the soft transition into the right side, measured as a percentage of the full inner display width beyond the fold. Default: 7%; 0 ends at the fold.",style=MaterialTheme.typography.bodySmall)
        Text("Fully-open threshold · ${threshold.roundToInt()}°",style=MaterialTheme.typography.titleMedium)
        Slider(value=threshold,onValueChange={threshold=it.roundToInt().toFloat()},valueRange=165f..179f,steps=13,onValueChangeFinished={prefs.edit().putFloat("open_threshold",threshold).apply();restart()})
        Text("At this angle the inner effect is completely clear. Closing starts below this threshold. Default: 172°.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={threshold=172f;prefs.edit().putFloat("open_threshold",172f).apply();restart()}){Text("Reset to 172°")}
        Text("Fully-closed threshold · ${closedThreshold.roundToInt()}°",style=MaterialTheme.typography.titleMedium)
        Slider(value=closedThreshold,onValueChange={closedThreshold=it.roundToInt().toFloat()},valueRange=1f..10f,steps=8,onValueChangeFinished={prefs.edit().putFloat("closed_threshold",closedThreshold).apply();restart()})
        Text("At or below this angle, treat the phone as fully closed and clear the cover effect. Default: 2°.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={closedThreshold=2f;prefs.edit().putFloat("closed_threshold",2f).apply();restart()}){Text("Reset to 2°")}
        HorizontalDivider()
        Text("Auto-rotate recovery",style=MaterialTheme.typography.titleMedium)
        Text("For stuck rotation: turn fold animation off, then repair. This enables auto-rotate for both postures and restores the default rotation policy. Requires connected Shizuku.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=!enabled && !repairingRotation,onClick={
         repairingRotation=true;rotationRepair="Releasing rotation overrides…"
         FoldSettingsClient.request(this@MainActivity,"rotation_repair",null){result->
          repairingRotation=false
          rotationRepair=if(result.getBoolean("ok"))result.getString("value") ?: "Auto-rotate repaired" else result.getString("error") ?: "Repair not verified; retry with animation off"
          RecoveryLog.add(rotationRepair)
         }
        }){Text("Repair auto-rotate")}
        if(rotationRepair.isNotEmpty())Text(rotationRepair,style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Keep cover awake on close",style=MaterialTheme.typography.titleMedium)
        Text("Always ON. Saved across updates and checked in the background, including when the animation is off. Reconnects automatically when authorized Shizuku becomes available.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={FoldAwakeDefault.reconnect();FoldAwakeDefault.tick(this@MainActivity);startBackground()}){Text("Recheck keep-awake now")}
        TextButton(onClick={developer=!developer}){Text("☰  Developer settings")}
        if(developer){
         SettingsCard("Developer settings","Diagnostics and experimental controls. Normal use does not require these."){
        TextButton(onClick={legacy=!legacy}){Text(if(legacy)"Hide legacy & experimental visuals" else "Legacy & experimental visuals")}
        if(legacy){
         Text("Older visuals · optional",style=MaterialTheme.typography.titleSmall)
         OutlinedButton(onClick={animationStyle="classic";prefs.edit().putString("animation_style","classic").apply();restart()}){Text("Use legacy Classic Glass")}
         Toggle("Classic black shading (debug)",debug){debug=it;booleanSetting("debug_mode",it)}
         TextButton(onClick={selectMode("windowed")}){Text("Restore Windowed Glass")}
        }
        Text("Screen continuity test",style=MaterialTheme.typography.titleMedium)
        Text("Arm, return to Home or the app you want to test, fully close, then unfold within 30 seconds. During the 20-second hold, opening past 98° tries moving real content to the inner display. The inner animation uses the transferred app. Check that content stays visible, with full-size layout, touch and navigation. Folding below 94° returns content; only one transfer per test. Temporarily requests system navigation on the inner display; restores the prior display policy after the test. Samsung may reject Home routing. Exit may still flash. Copy the connection report afterward.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=enabled&&liveMirror&&!dual,onClick={LiveAngles.startContinuityProbe()}){Text("Arm 20-second continuity test")}
        TextButton(onClick={LiveAngles.cancelContinuityProbe()}){Text("Stop continuity test")}
        Text(LiveAngles.continuityStatus,style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Toggle("Cover preview on inner screen (experimental)",liveMirror){liveMirror=it;booleanSetting("cover_preview",it)}
        Text("For use with Dual-screen screenshot handoff OFF. Mirrors cover content without its animation. A frosted second copy is already visible on the left. At handoff, the same layout briefly holds, then fades into the inner content without a bright expansion. Screen-switch angles stay unchanged.",style=MaterialTheme.typography.bodySmall)
        Toggle("Dual-screen screenshot handoff",dual){dual=it;booleanSetting("dual",it)}
        Text("Cover preview is on by default; screenshot handoff is off. Debug changes the shading only.",style=MaterialTheme.typography.bodySmall)
        Text(GlassFrames.status,style=MaterialTheme.typography.bodySmall)
        Text(HandoffFrames.status,style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,FoldProbeActivity::class.java))}){Text("Sensor & display diagnostics")}
          Text(FoldAwakeDefault.status,style=MaterialTheme.typography.bodySmall)
          Text(status,style=MaterialTheme.typography.bodySmall)
          Text(LiveAngles.rotationHold,style=MaterialTheme.typography.bodySmall)
          OutlinedButton(onClick={startActivity(Intent(this@MainActivity,org.duofold.live.wallpaperlayer.WallpaperActivity::class.java).putExtra("developer",true))}){Text("Wallpaper developer settings")}
          OutlinedButton(onClick={val sent=SupportPrompts.notify(this@MainActivity);android.widget.Toast.makeText(this@MainActivity,if(sent)"Preview notification sent; reminder schedule unchanged" else "Allow notifications in Android app settings",android.widget.Toast.LENGTH_LONG).show()}){Text("Preview support notification")}
         }
        }

       }
       OutlinedButton(onClick={val report=StandaloneService.instance?.report()?:"Duo Fold Live: accessibility disconnected";getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Duo Fold Live",report));android.widget.Toast.makeText(this@MainActivity,"Report copied",android.widget.Toast.LENGTH_SHORT).show()}){Text("Copy status report")}
      }
      SettingsCard("Support Duo Fold Live","Free, independent, and built with care for your Fold."){
       Text("If Duo makes your phone more enjoyable, you can help support development with a donation. Every feature stays available either way.")
       Button(onClick={SupportPrompts.open(this@MainActivity);supportBanner=false}){Text("Support on Ko-fi")}
       Text("Support invitations begin after 3 days of successful use, then 7 days later, 14 days later, and every 30 days thereafter. Snooze and permanent dismissal remain available.",style=MaterialTheme.typography.bodySmall)
      }
      Text("Duo Fold Live ${BuildConfig.VERSION_NAME} · Glass reference: chuspeeism/iphone-duo (MIT).",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
   }
  }
 }
}
@Composable internal fun SettingsCard(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit){
 Card(shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
  Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   Text(title,style=MaterialTheme.typography.titleLarge);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);content()
  }
 }
}
@Composable private fun Toggle(label:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f).padding(top=12.dp));Switch(checked,onChange)}}
