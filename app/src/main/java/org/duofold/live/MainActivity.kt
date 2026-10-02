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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlin.math.roundToInt

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class MainActivity:ComponentActivity(){
 private var developerShortcut by mutableIntStateOf(0)
 fun openDeveloperSettings(){developerShortcut++}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra(DeveloperShortcut.EXTRA,false))openDeveloperSettings()}

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
  if(intent.getBooleanExtra(DeveloperShortcut.EXTRA,false))openDeveloperSettings()
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
    val developerAnchor=remember{BringIntoViewRequester()}
    var animationStyle by remember{mutableStateOf(prefs.getString("animation_style","duo") ?: "duo")}
    var liveMirror by remember{mutableStateOf(prefs.getBoolean("cover_preview",true))}
    var debug by remember{mutableStateOf(prefs.getBoolean("debug_mode",false))}
    var dual by remember{mutableStateOf(prefs.getBoolean("dual",false))}
    var closingAppReturn by remember{mutableStateOf(prefs.getBoolean("closing_app_return",false))}
    var closingPower by remember{mutableStateOf(prefs.getBoolean("closing_direct_power",false))}
    var powerOverride by remember{mutableStateOf(prefs.getBoolean("handoff_power_override",false))}
    var screenshotStartup by remember{mutableStateOf(prefs.getBoolean("screenshot_camera_startup",false))}
    var advanced by remember{mutableStateOf(false)}
    LaunchedEffect(developerShortcut){
     if(developerShortcut>0){advanced=true;developer=true;withFrameNanos{};withFrameNanos{};developerAnchor.bringIntoView(Rect(0f,0f,1f,resources.displayMetrics.heightPixels*.75f))}
    }
    var setup by remember{mutableStateOf(!enabled)}
    var smoothing by remember{mutableFloatStateOf(FrameSmoothing.sanitize(prefs.getFloat("smoothing_ms",30f)))}
    var fadeSmoothing by remember{mutableFloatStateOf(FadeSettings.smoothing(prefs.getFloat("fade_smoothing_ms",FadeSettings.DEFAULT_SMOOTHING)))}
    var handoffAngle by remember{mutableFloatStateOf(HandoffSettings.angle(prefs.getFloat("handoff_angle",HandoffSettings.DEFAULT)))}
    var fadeGradualness by remember{mutableFloatStateOf(FadeSettings.gradualness(prefs.getFloat("fade_gradualness",FadeSettings.DEFAULT_GRADUALNESS)))}
    var fullResolution by remember{mutableStateOf(prefs.getBoolean("full_resolution_glass",false))}
    var antialias by remember{mutableStateOf(prefs.getBoolean("antialias_enabled",true))}
    var earlyStretch by remember{mutableFloatStateOf(prefs.getFloat("early_stretch",2.7f).let{if(it.isFinite())it.coerceIn(0f,3f)/3f else .9f})}
    var endStretch by remember{mutableFloatStateOf(prefs.getFloat("end_stretch",1.25f).let{if(it.isFinite())it.coerceIn(.8f,1.5f) else 1.25f})}
    var enhancedEnd by remember{mutableStateOf(prefs.getBoolean("enhanced_end_stretch",true))}
    var coverVertical by remember{mutableFloatStateOf(prefs.getFloat("cover_vertical_compression",.9f).let{if(it.isFinite())it.coerceIn(0f,2f) else .9f})}
    var innerVertical by remember{mutableFloatStateOf(prefs.getFloat("inner_vertical_compression",.4f).let{if(it.isFinite())it.coerceIn(0f,2f) else .4f})}
    var startupEasing by remember{mutableStateOf(prefs.getBoolean("startup_easing",true))}
    var innerEarlyStretch by remember{mutableFloatStateOf(prefs.getFloat("inner_early_stretch",.9f).let{if(it.isFinite())it.coerceIn(0f,3f)/3f else .3f})}
    var innerEndStretch by remember{mutableFloatStateOf(prefs.getFloat("inner_end_stretch",.6f).let{if(it.isFinite())it.coerceIn(0f,1.5f) else .6f})}
    var innerEnhancedEnd by remember{mutableStateOf(prefs.getBoolean("inner_enhanced_end_stretch",true))}
    var innerStartupEasing by remember{mutableStateOf(prefs.getBoolean("inner_startup_easing",true))}
    var windowReveal by remember{mutableStateOf(prefs.getBoolean("window_reveal_v2",true))}
    var antialiasMode by remember{mutableIntStateOf(RenderQuality.aaMode(prefs.getInt("antialias_method_v2",0)))}
    var antialiasStrength by remember{mutableFloatStateOf(RenderQuality.antialias(prefs.getFloat("antialias_strength",.35f)))}
    var contentFps by remember{mutableIntStateOf(RenderQuality.fps(prefs.getInt("content_fps",120)))}
    var blurStrength by remember{mutableFloatStateOf(RenderQuality.blur(prefs.getFloat("blur_strength",.3f)))}
    var frostedReflection by remember{mutableStateOf(prefs.getBoolean("frosted_reflection",true))}
    var seamOffset by remember{mutableFloatStateOf(RenderQuality.seam(prefs.getFloat("seam_offset",.07f)))}
    var intensity by remember{mutableFloatStateOf(prefs.getFloat("intensity",1f))}
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
      if(enabled && (status.contains("helper",ignoreCase=true)||status.contains("timed out",ignoreCase=true)) && !LiveAngles.fresh()){
       SettingsCard("Animation is waiting for a helper","Shizuku can be running while Duo’s helper is still disconnected."){
        Button(onClick={FoldBackgroundService.reconnectHelpers(this@MainActivity)}){Text("Reconnect helpers")}
        Text("Allow up to 35 seconds. If the helpers still time out, stop and start Shizuku, then return here.",style=MaterialTheme.typography.bodySmall)
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

       InfoLabel("Motion smoothness · ${smoothing.roundToInt()} ms","Higher values smooth hinge motion but add delay. This does not increase FPS. Default: 30 ms.")
       Slider(value=smoothing,onValueChange={smoothing=it},valueRange=12f..120f,onValueChangeFinished={prefs.edit().putFloat("smoothing_ms",smoothing).apply();restart()})

       TextButton(onClick={smoothing=30f;prefs.edit().putFloat("smoothing_ms",30f).apply();restart()}){Text("Reset smoothness")}
       InfoLabel("Blur amount · ${(blurStrength*100).roundToInt()}%","Softens pixelation, especially on the inner screen. Default: 30%.")
       Slider(value=blurStrength,onValueChange={blurStrength=it},valueRange=0f..3f,onValueChangeFinished={prefs.edit().putFloat("blur_strength",blurStrength).apply();restart()})

       TextButton(onClick={blurStrength=.3f;prefs.edit().putFloat("blur_strength",.3f).apply();restart()}){Text("Reset blur amount")}
       InfoLabel("Glass strength · ${(intensity*100).roundToInt()}%","Controls the glass shading intensity, independently of blur. Default: 100%.")
       Slider(value=intensity,onValueChange={intensity=it},valueRange=.3f..1.5f,onValueChangeFinished={prefs.edit().putFloat("intensity",intensity).apply();restart()})
       TextButton(onClick={intensity=1f;prefs.edit().putFloat("intensity",1f).apply();restart()}){Text("Reset glass strength")}
       Toggle("Full-resolution glass · higher GPU cost",fullResolution,help="Off uses half-resolution rendering to reduce GPU work. Content and handoff angles are unchanged."){fullResolution=it;booleanSetting("full_resolution_glass",it)}

       Text("Fully open at ${threshold.roundToInt()}°. Adjust this in Advanced.",style=MaterialTheme.typography.bodySmall)
      }
      SettingsCard("Custom wallpaper","Your photo on Home, with live hinge support in the background."){
       Button(onClick={startActivity(Intent(this@MainActivity,org.duofold.live.wallpaperlayer.WallpaperActivity::class.java))}){Text("Choose & manage custom wallpaper")}
       Text("One photo, separate crops. Your selection survives updates.",style=MaterialTheme.typography.bodySmall)
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
        OutlinedButton(onClick={FoldBackgroundService.reconnectHelpers(this@MainActivity);StandaloneService.instance?.restart()}){Text("Reconnect helpers")}
        Text("Keep Samsung’s interactive wallpaper on both screens. Stop any older fold helper. If accessibility is blocked: App info → ⋮ → Allow restricted settings.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}){Text("App & battery settings")}
       }
      }
      SettingsCard("Advanced","Display thresholds, fold behavior, and diagnostics."){
       TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Hide advanced settings" else "Show advanced settings")}
       if(advanced){
        InfoLabel("Handoff angle · ${handoffAngle.roundToInt()}°","Cover-to-inner switch angle. Default: 85°; range: 75–115°. Closing uses 4° less to avoid repeated switching. Applies to live cover preview; legacy dual-screen screenshot and continuity tests use their own routing.")
        Slider(value=handoffAngle,onValueChange={handoffAngle=it.roundToInt().toFloat()},valueRange=75f..115f,steps=39,onValueChangeFinished={prefs.edit().putFloat("handoff_angle",handoffAngle).apply();restart()})
        TextButton(onClick={handoffAngle=HandoffSettings.DEFAULT;prefs.edit().putFloat("handoff_angle",handoffAngle).apply();restart()}){Text("Reset handoff angle")}
       InfoLabel("Black fade smoothing · ${fadeSmoothing.roundToInt()} ms","Smooths the black fade as the hinge moves. Higher values add more smoothing.")
       Slider(value=fadeSmoothing,onValueChange={fadeSmoothing=it},valueRange=0f..120f,onValueChangeFinished={prefs.edit().putFloat("fade_smoothing_ms",fadeSmoothing).apply()})

       InfoLabel("Black fade gradualness · ${(fadeGradualness*100).roundToInt()}%","0% gives a sharp fade. Higher values fade earlier and reveal the new screen more slowly.")
       Slider(value=fadeGradualness,onValueChange={fadeGradualness=it},valueRange=0f..1f,onValueChangeFinished={prefs.edit().putFloat("fade_gradualness",fadeGradualness).apply()})

       TextButton(onClick={fadeSmoothing=FadeSettings.DEFAULT_SMOOTHING;fadeGradualness=FadeSettings.DEFAULT_GRADUALNESS;prefs.edit().putFloat("fade_smoothing_ms",fadeSmoothing).putFloat("fade_gradualness",fadeGradualness).apply()}){Text("Reset black fade")}
        HorizontalDivider()

        InfoLabel("Screen placement","V2 starts stretching earlier without the inward squeeze. Original remains available for comparison. Applies in both fold directions.")
        for((v2,label) in listOf(false to "Original",true to "V2 Window Reveal · experimental"))TextButton(onClick={windowReveal=v2;prefs.edit().putBoolean("window_reveal_v2",v2).apply();restart()}){Text((if(windowReveal==v2)"✓ " else "")+label)}


        if(windowReveal){
         OutlinedCard(modifier=Modifier.fillMaxWidth()){
          Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
         Text("Cover display animation",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
         InfoLabel("Vertical compression · ${(coverVertical*100).roundToInt()}%","100% is the original top/bottom perspective; 0% removes compression and 200% doubles it. Classic glass has no vertical compression to scale.")
         Slider(value=coverVertical,onValueChange={coverVertical=it},valueRange=0f..2f,onValueChangeFinished={prefs.edit().putFloat("cover_vertical_compression",coverVertical).apply();restart()})

         TextButton(onClick={coverVertical=.9f;prefs.edit().putFloat("cover_vertical_compression",.9f).apply();restart()}){Text("Reset vertical compression")}

         Toggle("Startup easing",startupEasing,help="Softens the first opening frames. Turn off to compare."){startupEasing=it;booleanSetting("startup_easing",it)}

         InfoLabel("Early stretch · ${(earlyStretch*100).roundToInt()}%","Adds stretch earlier while preserving the ending and vertical perspective. Default: 90%.")
         Slider(value=earlyStretch,onValueChange={earlyStretch=it},valueRange=0f..1f,onValueChangeFinished={prefs.edit().putFloat("early_stretch",earlyStretch*3f).apply();restart()})

         TextButton(onClick={earlyStretch=.9f;prefs.edit().putFloat("early_stretch",2.7f).apply();restart()}){Text("Reset early stretch")}
         Toggle("Enhanced end stretch",enhancedEnd){enhancedEnd=it;booleanSetting("enhanced_end_stretch",it)}
         if(enhancedEnd){
          InfoLabel("End stretch · ${(endStretch*100).roundToInt()}%","Range: 80–150%. Default: 125%. Changes the ending stretch, not the handoff angle.")
          Slider(value=endStretch,onValueChange={endStretch=it},valueRange=.8f..1.5f,onValueChangeFinished={prefs.edit().putFloat("end_stretch",endStretch).apply();restart()})

          TextButton(onClick={endStretch=1.25f;prefs.edit().putFloat("end_stretch",1.25f).apply();restart()}){Text("Reset end stretch")}
         }
          }
         }
         Spacer(Modifier.height(16.dp))
         OutlinedCard(modifier=Modifier.fillMaxWidth()){
          Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
         Text("Inner display animation",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
         InfoLabel("Vertical compression · ${(innerVertical*100).roundToInt()}%","100% is the original top/bottom perspective; 0% removes compression and 200% doubles it. Classic glass has no vertical compression to scale.")
         Slider(value=innerVertical,onValueChange={innerVertical=it},valueRange=0f..2f,onValueChangeFinished={prefs.edit().putFloat("inner_vertical_compression",innerVertical).apply();restart()})

         TextButton(onClick={innerVertical=.4f;prefs.edit().putFloat("inner_vertical_compression",.4f).apply();restart()}){Text("Reset vertical compression")}

         Toggle("Startup easing",innerStartupEasing,help="Softens the inner animation near fully open, in both directions."){innerStartupEasing=it;booleanSetting("inner_startup_easing",it)}

         InfoLabel("Early stretch · ${(innerEarlyStretch*100).roundToInt()}%","Changes how early inner content stretches. Independent of the cover and reflected preview. Default: 30%.")
         Slider(value=innerEarlyStretch,onValueChange={innerEarlyStretch=it},valueRange=0f..1f,onValueChangeFinished={prefs.edit().putFloat("inner_early_stretch",innerEarlyStretch*3f).apply();restart()})

         TextButton(onClick={innerEarlyStretch=.3f;prefs.edit().putFloat("inner_early_stretch",.9f).apply();restart()}){Text("Reset early stretch")}
         Toggle("Enhanced end stretch",innerEnhancedEnd){innerEnhancedEnd=it;booleanSetting("inner_enhanced_end_stretch",it)}
         if(innerEnhancedEnd){
          InfoLabel("End stretch · ${(innerEndStretch*100).roundToInt()}%","Range: 0–150%. Default: 60%. 0% removes horizontal stretch. Vertical perspective and handoff angle stay unchanged.")
          Slider(value=innerEndStretch,onValueChange={innerEndStretch=it},valueRange=0f..1.5f,onValueChangeFinished={prefs.edit().putFloat("inner_end_stretch",innerEndStretch).apply();restart()})

          TextButton(onClick={innerEndStretch=.6f;prefs.edit().putFloat("inner_end_stretch",.6f).apply();restart()}){Text("Reset end stretch")}
         }
          }
         }
        }
        Toggle("Anti-aliasing",antialias,help="Lightweight is the default. Edge-Adaptive smooths the rendered image; 4× Supersampling doubles buffer width and height. Experimental options use more GPU power."){antialias=it;booleanSetting("antialias_enabled",it)}
        if(antialias){
         for((id,label) in listOf(0 to "Lightweight Texture Filtering",1 to "Edge-Adaptive Smoothing · experimental",2 to "4× Supersampling · experimental"))TextButton(onClick={antialiasMode=id;prefs.edit().putInt("antialias_method_v2",id).apply();restart()}){Text((if(antialiasMode==id)"✓ " else "")+label)}

         Text("Anti-aliasing strength · ${(antialiasStrength*100).roundToInt()}%")
         Slider(value=antialiasStrength,onValueChange={antialiasStrength=it},valueRange=.2f..0.5f,onValueChangeFinished={prefs.edit().putFloat("antialias_strength",antialiasStrength).apply();restart()})
         TextButton(onClick={antialiasStrength=.35f;prefs.edit().putFloat("antialias_strength",.35f).apply();restart()}){Text("Reset anti-aliasing")}
        }

        InfoLabel("Live content frame rate","No automatic refresh-rate switching. Actual FPS depends on the device and temperature; check the status report. Screenshot mode holds a still image.")
        for(rate in listOf(12,60,120))TextButton(onClick={contentFps=rate;prefs.edit().putInt("content_fps",rate).apply();restart()}){Text((if(contentFps==rate)"✓ " else "")+when(rate){12->"Legacy · approximately 12 FPS";120->"120 FPS · experimental";else->"60 FPS · lower GPU cost"})}

        InfoLabel("Left preview appearance","Animated Reflection mirrors the cover animation. Frosted Reflection uses a blurred mirror with less rendering work. Both keep the clean right preview and center blend.")
        for(frosted in listOf(true,false))TextButton(onClick={frostedReflection=frosted;booleanSetting("frosted_reflection",frosted)}){Text((if(frostedReflection==frosted)"✓ " else "")+if(frosted)"Frosted Reflection" else "Animated Reflection")}

        InfoLabel("Center-edge blur offset · ${(seamOffset*100).roundToInt()}%","Moves the blend to the right of the fold, as a percentage of the full inner-screen width. Default: 7%; 0% ends at the fold.")
        Slider(value=seamOffset,onValueChange={seamOffset=it},valueRange=0f..0.15f,onValueChangeFinished={prefs.edit().putFloat("seam_offset",seamOffset).apply();restart()})

        InfoLabel("Fully-open threshold · ${threshold.roundToInt()}°","The inner effect clears at this angle; closing starts below it. Default: 172°.")
        Slider(value=threshold,onValueChange={threshold=it.roundToInt().toFloat()},valueRange=165f..179f,steps=13,onValueChangeFinished={prefs.edit().putFloat("open_threshold",threshold).apply();restart()})

        TextButton(onClick={threshold=172f;prefs.edit().putFloat("open_threshold",172f).apply();restart()}){Text("Reset to 172°")}
        InfoLabel("Fully-closed threshold · ${closedThreshold.roundToInt()}°","Clears the cover effect at or below this angle. Default: 2°.")
        Slider(value=closedThreshold,onValueChange={closedThreshold=it.roundToInt().toFloat()},valueRange=1f..10f,steps=8,onValueChangeFinished={prefs.edit().putFloat("closed_threshold",closedThreshold).apply();restart()})

        TextButton(onClick={closedThreshold=2f;prefs.edit().putFloat("closed_threshold",2f).apply();restart()}){Text("Reset to 2°")}
        HorizontalDivider()
        Text("Auto-rotate recovery",style=MaterialTheme.typography.titleMedium)
        Text("Turn animation off, then repair. Requires Shizuku. Enables auto-rotate on both screens.",style=MaterialTheme.typography.bodySmall)
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
        Text("Always on, including when animation is off. Requires authorized Shizuku.",style=MaterialTheme.typography.bodySmall)
        Text("Still locking? In Samsung Settings, search “Lock when folded” and turn it OFF. Return to Home and fold to test.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={FoldAwakeDefault.reconnect();FoldAwakeDefault.tick(this@MainActivity);startBackground()}){Text("Recheck keep-awake now")}
        TextButton(modifier=Modifier.bringIntoViewRequester(developerAnchor),onClick={developer=!developer}){Text("☰  Developer settings")}
        Text("Shortcut: tap with four fingers anywhere in the app.",style=MaterialTheme.typography.bodySmall)
        if(developer){
         SettingsCard("Developer settings","Diagnostics and experimental controls. Normal use does not require these."){
        TextButton(onClick={legacy=!legacy}){Text(if(legacy)"Hide legacy & experimental visuals" else "Legacy & experimental visuals")}
        if(legacy){
         Text("Older visuals · optional",style=MaterialTheme.typography.titleSmall)
         OutlinedButton(onClick={animationStyle="classic";prefs.edit().putString("animation_style","classic").apply();restart()}){Text("Use legacy Classic Glass")}
         Toggle("Classic black shading (debug)",debug){debug=it;booleanSetting("debug_mode",it)}
         TextButton(onClick={selectMode("windowed")}){Text("Restore Windowed Glass")}
        }
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,CameraContinuityActivity::class.java))}){Text("Camera-style continuity test")}
        Text("Screen continuity test",style=MaterialTheme.typography.titleMedium)
        Text("Arm, return to Home or the app you want to test, fully close, then unfold within 30 seconds. During the 20-second hold, opening past 98° tries moving real content to the inner display. The inner animation uses the transferred app. Check that content stays visible, with full-size layout, touch and navigation. Folding below 94° returns content; only one transfer per test. Temporarily requests system navigation on the inner display; restores the prior display policy after the test. Samsung may reject Home routing. Exit may still flash. Copy the connection report afterward.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=enabled&&liveMirror&&!dual,onClick={LiveAngles.startContinuityProbe()}){Text("Arm 20-second continuity test")}
        TextButton(onClick={LiveAngles.cancelContinuityProbe()}){Text("Stop continuity test")}
        Text(LiveAngles.continuityStatus,style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Toggle("Cover preview on inner screen (experimental)",liveMirror){liveMirror=it;booleanSetting("cover_preview",it)}
        Text("For use with Dual-screen screenshot handoff OFF. Mirrors cover content without its animation. A frosted second copy is already visible on the left. At handoff, the same layout briefly holds, then fades into the inner content without a bright expansion. Screen-switch angles stay unchanged.",style=MaterialTheme.typography.bodySmall)
        Toggle("Dual-screen screenshot handoff",dual){dual=it;booleanSetting("dual",it)}
        Text(if(dual)"Active path: dual-screen screenshot handoff" else "Active path: normal handoff. The next two switches are inactive while dual-screen handoff is OFF.",style=MaterialTheme.typography.bodySmall)
        Toggle("Screenshot window-first startup (experimental)",screenshotStartup){screenshotStartup=it;booleanSetting("screenshot_camera_startup",it)}
        Toggle("Hold displays ON at handoff (experimental)",powerOverride){powerOverride=it;booleanSetting("handoff_power_override",it)}
        Text("Requires both screenshot handoff options above. Requests Samsung ON overrides near full opening/closing, then releases them after two seconds. May be rejected or delay the switch; compare ON versus OFF and copy the short report.",style=MaterialTheme.typography.bodySmall)
        Text("Requires Dual-screen screenshot handoff ON. Diagnostic: commits the outgoing screenshot, then wakes the incoming panel while keeping the outgoing screen on. Incoming content is uncovered live content, with incoming glass temporarily omitted. Final native-layout switch may still flash. Maximum hold: 30 seconds. Turn this off to restore the original screenshot path.",style=MaterialTheme.typography.bodySmall)
        Toggle("Closing app-first return (experimental)",closingAppReturn){closingAppReturn=it;booleanSetting("closing_app_return",it)}
        Text("Screenshot window-first tests only. Returns the selected app to the primary screen at 15° while closing, before Android releases dual mode. Tests whether leaving Home focused contributes to fold sleep. May interrupt the live cover preview near closure. OFF by default.",style=MaterialTheme.typography.bodySmall)
        Toggle("Closing cover power pulse (experimental)",closingPower){closingPower=it;booleanSetting("closing_direct_power",it)}
        Text("Normal handoff only (Dual-screen OFF, Cover preview ON). After closing releases concurrency, requests cover ON once if native closed mode reports the cover OFF. Restores the system power request after 250 ms. Compare OFF versus ON; opening is unchanged.",style=MaterialTheme.typography.bodySmall)
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
       Button(onClick={val report=StandaloneService.instance?.report(includeHealthHistory=false)?:"Duo Fold Live: accessibility disconnected";getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Duo Fold Live",report));android.widget.Toast.makeText(this@MainActivity,"Status report copied",android.widget.Toast.LENGTH_SHORT).show()}){Text("Copy status report")}
       TextButton(colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.tertiary),onClick={val report=StandaloneService.instance?.report(includeHealthHistory=true)?:"Duo Fold Live: accessibility disconnected";getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Duo Fold Live",report));android.widget.Toast.makeText(this@MainActivity,"Report with health history copied",android.widget.Toast.LENGTH_SHORT).show()}){Text("With health history",style=MaterialTheme.typography.labelSmall)}
      }
      SettingsCard("Support Duo Fold Live","Free, independent, and built with care for your Fold."){
       Text("Donations support development. All features remain free.")
       Button(onClick={SupportPrompts.open(this@MainActivity);supportBanner=false}){Text("Support on Ko-fi")}
       InfoLabel("Support reminders","First shown after 3 days of successful use, then 7 days later, 14 days later, and every 30 days. You can snooze or stop reminders.")
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
@Composable private fun InfoLabel(label:String,help:String,modifier:Modifier=Modifier){
 var show by remember{mutableStateOf(false)}
 Row(modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.Top){
  Text(label,Modifier.weight(1f).padding(top=12.dp))
  TextButton(onClick={show=true},modifier=Modifier.semantics{contentDescription="More information: $label"}){Text("ⓘ")}
 }
 if(show)AlertDialog(onDismissRequest={show=false},title={Text(label)},text={Column(Modifier.verticalScroll(rememberScrollState())){Text(help)}},confirmButton={TextButton(onClick={show=false}){Text("Done")}})
}
@Composable private fun Toggle(label:String,checked:Boolean,help:String?=null,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){if(help==null)Text(label,Modifier.weight(1f).padding(top=12.dp)) else InfoLabel(label,help,Modifier.weight(1f));Switch(checked,onChange)}}
