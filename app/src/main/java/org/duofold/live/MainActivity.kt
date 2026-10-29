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
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
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
import androidx.compose.ui.semantics.stateDescription
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
  if(!RuntimeControls.wanted(this))return
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
   DuoTheme{
    val prefs=remember{getSharedPreferences("standalone",0)}
    val scope=rememberCoroutineScope()
    var enabled by remember{mutableStateOf(prefs.getBoolean("enabled",false))}
    var behavior by remember{mutableStateOf(AnimationModePolicy.behavior(prefs.getString("animation_behavior",AnimationModePolicy.BOTH)))}
    var motionGlass by remember{mutableStateOf(prefs.getBoolean("motion_glass",true))}
    var motionCoverGlass by remember{mutableStateOf(prefs.getBoolean("motion_glass_cover_enabled",false))}
    val legacyMotionStrength=remember{MotionGlassMath.safe(prefs.getFloat("motion_glass_projection_strength",1f),1f,0f,2f)}
    var motionCoverStrength by remember{mutableStateOf(MotionGlassMath.safe(prefs.getFloat("motion_glass_cover_strength",legacyMotionStrength),legacyMotionStrength,0f,2f))}
    var motionInnerStrength by remember{mutableStateOf(MotionGlassMath.safe(prefs.getFloat("motion_glass_inner_strength",legacyMotionStrength),legacyMotionStrength,0f,2f))}
    var motionSmoothing by remember{mutableStateOf(prefs.getFloat("motion_glass_smoothing",90f))}
    var supportBanner by remember{mutableStateOf(false)}
    var developer by remember{mutableStateOf(false)}
    val developerAnchor=remember{BringIntoViewRequester()}
    var liveMirror by remember{mutableStateOf(prefs.getBoolean("cover_preview",true))}
    val baseViewConfiguration=LocalViewConfiguration.current
    var screenshotMode by remember{mutableStateOf(FoldingModeSettings.screenshot(prefs))}
    var jitterProtection by remember{mutableStateOf(prefs.getBoolean("closed_hinge_jitter_protection",true))}
    var advanced by remember{mutableStateOf(false)}
    LaunchedEffect(developerShortcut){
     if(developerShortcut>0){advanced=true;developer=true;withFrameNanos{};withFrameNanos{};developerAnchor.bringIntoView(Rect(0f,0f,1f,resources.displayMetrics.heightPixels*.75f))}
    }
    var setup by remember{mutableStateOf(!enabled)}
    var smoothing by remember{mutableFloatStateOf(FrameSmoothing.sanitize(prefs.getFloat("smoothing_ms",FrameSmoothing.DEFAULT_MS)))}
    var fadeSmoothing by remember{mutableFloatStateOf(FadeSettings.smoothing(prefs.getFloat("fade_smoothing_ms",FadeSettings.DEFAULT_SMOOTHING)))}
    var handoffAngle by remember{mutableFloatStateOf(HandoffSettings.angle(prefs.getFloat("handoff_angle",HandoffSettings.DEFAULT)))}
    var fadeGradualness by remember{mutableFloatStateOf(FadeSettings.gradualness(prefs.getFloat("fade_gradualness",FadeSettings.DEFAULT_GRADUALNESS)))}
    var effectResolution by remember{mutableIntStateOf(GlassEffectSettings.resolution(prefs))}
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
    var blurStrength by remember{mutableFloatStateOf(RenderQuality.blur(prefs.getFloat("blur_strength",RenderQuality.DEFAULT_BLUR)))}
    var frostedReflection by remember{mutableStateOf(prefs.getBoolean("frosted_reflection",true))}
    var seamOffset by remember{mutableFloatStateOf(RenderQuality.seam(prefs.getFloat("seam_offset",.07f)))}
    var intensity by remember{mutableFloatStateOf(prefs.getFloat("intensity",RenderQuality.DEFAULT_GLASS))}
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
    fun selectBehavior(value:String){
     behavior=value;prefs.edit().putString("animation_behavior",value).apply();restart()
    }
    fun selectFoldingMode(shot:Boolean){
     FoldingModeSettings.select(prefs,shot)
     screenshotMode=shot;liveMirror=!shot
     restart()
    }
    fun booleanSetting(key:String,value:Boolean){prefs.edit().putBoolean(key,value).apply();restart()}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
     photoStatus="Preparing image…"
     runCatching{withContext(Dispatchers.IO){WallpaperFiles.savePhoto(this@MainActivity,uri)}}.onSuccess{photo=it;photoStatus="Photo saved as your selection. Not applied to Home yet."}.onFailure{photoStatus="Could not read image: ${it.message}"}
    }}
    Surface(Modifier.fillMaxSize()){
     Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal=maxOf(20, (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp-720)/2).dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
      Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xff263d5b),Color(0xff252139))),RoundedCornerShape(32.dp)).padding(26.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
       DuoSignature()
       Text("Duo Fold Live",style=MaterialTheme.typography.headlineLarge)
       Text("Make every fold feel fluid.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
       Toggle("Enable animation",enabled){
        if(it && !RuntimeConsent.ready(this@MainActivity)){startActivity(Intent(this@MainActivity,PrivacyActivity::class.java))}
        else if(it && (!Settings.canDrawOverlays(this@MainActivity) || StandaloneService.instance==null)){
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
        TextButton(onClick={SupportPrompts.snooze(this@MainActivity);supportBanner=false}){Text("Snooze for 7 days")}
        TextButton(onClick={supportBanner=false;SupportPrompts.stop(this@MainActivity)}){Text("I already supported")}
        TextButton(onClick={supportBanner=false;SupportPrompts.stop(this@MainActivity)}){Text("Don’t ask again")}
       }
      }
      SettingsCard("Make it yours",""){
       WallpaperInvitation{startActivity(Intent(this@MainActivity,WallpaperStudioActivity::class.java))}
      }
      SettingsCard("Folding Animation Style","Windowed Glass with live content or screenshots. Change styles while fully folded or fully open."){
       listOf(true to FoldingModeSettings.DUO_LABEL,false to FoldingModeSettings.LIVE_LABEL).forEach{(shot,label)->
        Surface(onClick={selectFoldingMode(shot)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=if(screenshotMode==shot)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant){
         Row(Modifier.padding(14.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
          RadioButton(selected=screenshotMode==shot,onClick=null)
          Column(Modifier.weight(1f).padding(start=8.dp)){
           Text(label,style=MaterialTheme.typography.titleMedium)
           Text(if(shot)"Default. Animates app snapshots with Windowed Glass and your saved glass settings, then reveals the app on the selected screen." else "Animates live content as you fold between screens.",style=MaterialTheme.typography.bodySmall)
          }
         }
        }
       }
       Text("Hold the hinge still for 1.25 seconds to ease back to a usable screen: below 90° uses the cover, above 90° uses the inner screen, and exactly 90° keeps the active screen. Small hinge jitter is ignored; move 5° to resume.",style=MaterialTheme.typography.bodySmall)
      }
      SettingsCard("Animation Behavior","Choose when your selected animation is used."){
       listOf(AnimationModePolicy.BOTH,"fold_only","unfold_only").forEach{id->
        Surface(onClick={selectBehavior(id)},shape=RoundedCornerShape(20.dp),color=if(behavior==id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()){
         Row(Modifier.padding(14.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
          RadioButton(selected=behavior==id,onClick={selectBehavior(id)})
          Text(AnimationModePolicy.behaviorLabel(id),modifier=Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleMedium)
         }
        }
       }
      }
      SettingsCard("Auto-rotate recovery","Screen stuck in portrait or landscape?"){
        Text("Turn off Enable animation above, then tap Repair auto-rotate. This enables auto-rotate on both screens. Requires authorized Shizuku or root.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=!enabled && !repairingRotation,onClick={
         repairingRotation=true;rotationRepair="Releasing rotation overrides…"
         FoldSettingsClient.request(this@MainActivity,"rotation_repair",null){result->
          repairingRotation=false
          rotationRepair=if(result.getBoolean("ok"))result.getString("value") ?: "Auto-rotate repaired" else result.getString("error") ?: "Repair not verified; retry with animation off"
          RecoveryLog.add(rotationRepair)
         }
        }){Text("Repair auto-rotate")}
        if(rotationRepair.isNotEmpty())Text(rotationRepair,style=MaterialTheme.typography.bodySmall)
      }
      ShizukuHelp()
      SettingsCard("Fine-tune your animation","Glass, blur and motion settings apply to both Live Screen Mode (Legacy) and Duo Mode."){

       InfoLabel("Motion smoothness · ${smoothing.roundToInt()} ms","Higher values smooth hinge motion but add delay. This does not increase FPS. Default: Smooth (120 ms).")
       Slider(value=smoothing,onValueChange={smoothing=it},valueRange=12f..120f,onValueChangeFinished={prefs.edit().putFloat("smoothing_ms",smoothing).apply();restart()})

       listOf("Smooth" to FrameSmoothing.SMOOTH_MS,"Fast" to FrameSmoothing.FAST_MS).forEach{(label,value)->
        val selected=smoothing==value
        val selectPreset={smoothing=value;prefs.edit().putFloat("smoothing_ms",value).apply();restart()}
        Surface(onClick=selectPreset,shape=RoundedCornerShape(20.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()){
         Row(Modifier.padding(14.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
          RadioButton(selected=selected,onClick=selectPreset)
          Text(label,modifier=Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleMedium)
          Text("${value.roundToInt()} ms",style=MaterialTheme.typography.bodyMedium)
         }
        }
       }
       TextButton(onClick={smoothing=FrameSmoothing.DEFAULT_MS;prefs.edit().putFloat("smoothing_ms",smoothing).apply();restart()}){Text("Reset smoothness")}
       InfoLabel("Blur amount · ${(blurStrength*100).roundToInt()}%","Softens pixelation, especially on the inner screen. Default: 150%.")
       Slider(value=blurStrength,onValueChange={blurStrength=it},valueRange=0f..3f,onValueChangeFinished={prefs.edit().putFloat("blur_strength",blurStrength).apply();restart()})

       TextButton(onClick={blurStrength=RenderQuality.DEFAULT_BLUR;prefs.edit().putFloat("blur_strength",RenderQuality.DEFAULT_BLUR).apply();restart()}){Text("Reset blur amount")}
       InfoLabel("Glass strength · ${(intensity*100).roundToInt()}%","Controls the glass shading intensity, independently of blur. Default: 40% in both modes.")
       Slider(value=intensity,onValueChange={intensity=it},valueRange=.3f..1.5f,onValueChangeFinished={prefs.edit().putFloat("intensity",intensity).apply();restart()})
       TextButton(onClick={intensity=RenderQuality.DEFAULT_GLASS;prefs.edit().putFloat("intensity",RenderQuality.DEFAULT_GLASS).apply();restart()}){Text("Reset glass strength")}
       InfoLabel("Glass / effect resolution · $effectResolution%","Shared by both modes. Default: 25% of each screen dimension, matching Duo Mode's existing resolution. Higher values add detail and GPU work. Advanced 4× Supersampling doubles the selected dimensions.")
       Slider(value=effectResolution.toFloat(),onValueChange={effectResolution=((it/25f).roundToInt()*25).coerceIn(25,100)},valueRange=25f..100f,steps=2,onValueChangeFinished={prefs.edit().putInt(GlassEffectSettings.RESOLUTION_KEY,effectResolution).apply();restart()})
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){listOf(25,50,75,100).forEach{Text("$it%",style=MaterialTheme.typography.bodySmall)}}
       Toggle("Inner screen motion glass",motionGlass,help="Tilt your phone to change the glass perspective on the inner display. Uses the same controls in Live Screen Mode (Legacy) and Duo Mode."){motionGlass=it;prefs.edit().putBoolean("motion_glass",it).apply();if(!it && !motionCoverGlass)MotionGlass.stop()}
       if(motionGlass){
        Text("Inner screen sensitivity · ${(motionInnerStrength*100).roundToInt()}%")
        Slider(value=motionInnerStrength,onValueChange={motionInnerStrength=it},valueRange=0f..2f,onValueChangeFinished={prefs.edit().putFloat("motion_glass_inner_strength",motionInnerStrength).apply()})
        Text("Smoothing · ${motionSmoothing.roundToInt()} ms")
        Slider(value=motionSmoothing,onValueChange={motionSmoothing=it},valueRange=0f..300f,onValueChangeFinished={prefs.edit().putFloat("motion_glass_smoothing",motionSmoothing).apply()})
        OutlinedButton(onClick={MotionGlass.recenter()}){Text("Recenter")}
        TextButton(onClick={motionInnerStrength=1f;motionSmoothing=90f;prefs.edit().putFloat("motion_glass_inner_strength",1f).putFloat("motion_glass_smoothing",90f).apply();MotionGlass.recenter()}){Text("Reset motion settings")}
       }

       Text("Fully open at ${threshold.roundToInt()}°. Adjust this in Advanced.",style=MaterialTheme.typography.bodySmall)
      }
      SettingsCard("Connection & setup","Shizuku or existing root access and accessibility keep the effect running."){
       TextButton(onClick={setup=!setup}){Text(if(setup)"Hide setup" else "Show setup")}
       if(setup){
        RootAccessControls()
        ShizukuOnly { ShizukuPlusDownload() }
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,SetupActivity::class.java).putExtra("repair",true))}){Text("Repair required wallpaper setup")}
        ShizukuOnly { Button(onClick={val result=ShizukuAccess.request(this@MainActivity,42); if(result.startsWith("Permission requested"))android.widget.Toast.makeText(this@MainActivity,result,android.widget.Toast.LENGTH_LONG).show() else ShizukuAccess.show(this@MainActivity,result)}){Text("Authorize Shizuku")} }
        OutlinedButton(onClick={ShizukuAccess.show(this@MainActivity,ShizukuAccess.report(this@MainActivity))}){Text("Connection report")}
        OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}){Text("Allow overlays")}
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,PrivacyActivity::class.java))}){Text("Enable accessibility")}
        OutlinedButton(onClick={FoldBackgroundService.reconnectHelpers(this@MainActivity);StandaloneService.instance?.restart()}){Text("Reconnect helpers")}
        Text("Keep Samsung’s interactive wallpaper on both screens. Stop any older fold helper. If accessibility is blocked: App info → ⋮ → Allow restricted settings.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}){Text("App & battery settings")}
       }
      }
      SettingsCard("Privacy & controls","Review access, choose keep-awake, or stop background work."){
       Button(onClick={startActivity(Intent(this@MainActivity,PrivacyActivity::class.java))}){Text("Privacy & controls")}
       OutlinedButton(onClick={RuntimeControls.stopAll(this@MainActivity);enabled=false}){Text("Stop all")}
      }
      SettingsCard("Advanced","Display thresholds, fold behavior, and diagnostics."){
       TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Hide advanced settings" else "Show advanced settings")}
       if(advanced){
        InfoLabel("Handoff angle · ${handoffAngle.roundToInt()}°","Default: 85°; range: 75–115°. Coordinated mode uses this as the center of a 40° hold band (opening +20°, closing −20°). Other applicable modes use it as the opening angle with closing 4° lower. Applies to live cover preview and the screenshot window-first primary switch; legacy screenshot and continuity tests use their own routing.")
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

         InfoLabel("Early stretch · ${(innerEarlyStretch*100).roundToInt()}%","Changes how early inner content stretches. Independent of the cover and reflected preview. Default: 150%.")
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
        Text("Keep cover awake on close",style=MaterialTheme.typography.titleMedium)
        Text("Optional. Choose whether Duo changes the fold-lock setting in Privacy & controls. Requires authorized Shizuku or root.",style=MaterialTheme.typography.bodySmall)
        Text("Still locking? In Samsung Settings, search “Lock when folded” and turn it OFF. Return to Home and fold to test.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,PrivacyActivity::class.java))}){Text("Keep-awake controls")}
        TextButton(modifier=Modifier.bringIntoViewRequester(developerAnchor),onClick={developer=!developer}){Text("☰  Developer settings")}
        Text("Shortcut: tap with four fingers anywhere in the app.",style=MaterialTheme.typography.bodySmall)
        if(developer){
         SettingsCard("Developer settings","Diagnostics and experimental controls. Normal use does not require these."){
        Toggle("Cover screen motion glass (experimental)",motionCoverGlass,help="Off by default. Adds tilt response to the cover display independently of inner motion glass, in both styles."){motionCoverGlass=it;prefs.edit().putBoolean("motion_glass_cover_enabled",it).apply();if(!it && !motionGlass)MotionGlass.stop()}
        if(motionCoverGlass){
        Text("Cover screen sensitivity · ${(motionCoverStrength*100).roundToInt()}%")
        Slider(value=motionCoverStrength,onValueChange={motionCoverStrength=it},valueRange=0f..2f,onValueChangeFinished={prefs.edit().putFloat("motion_glass_cover_strength",motionCoverStrength).apply()})
         Text("Direction and smoothing are shared with inner motion glass.",style=MaterialTheme.typography.bodySmall)
         OutlinedButton(onClick={MotionGlass.recenter()}){Text("Recenter motion glass")}
         TextButton(onClick={motionCoverStrength=1f;prefs.edit().putFloat("motion_glass_cover_strength",1f).apply()}){Text("Reset cover sensitivity")}
        }
        HorizontalDivider()
        Toggle("Closed-hinge jitter protection",jitterProtection){jitterProtection=it;booleanSetting("closed_hinge_jitter_protection",it)}
        Text("On by default. Starts at ${(ClosedHingeGate.startAngle(closedThreshold)).roundToInt()}°; once started, follows the hinge until it returns to ${closedThreshold.roundToInt()}° or less. Filters tiny closed-phone readings before the animation and cover preview start. Turn off to restore the original threshold behavior.",style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Glass rendering experiments",style=MaterialTheme.typography.titleMedium)
        var renderOption by remember { mutableStateOf(GlassRenderOptions.mode(this@MainActivity)) }
        GlassRenderOptions.ids.forEachIndexed { index,id ->
         OutlinedButton(onClick={renderOption=id;prefs.edit().putString("glass_render_mode",id).apply();restart()}){Text((if(renderOption==id)"✓ " else "")+GlassRenderOptions.labels[index])}
        }
        var systemRadius by remember { mutableFloatStateOf(CompositorBlurRegions.radius(prefs.getFloat("notification_blur_radius",250f))) }
        InfoLabel("Notification blur radius · ${systemRadius.roundToInt()} px","For both Samsung experiments. Default 250 screen pixels, matching the captured notification background. Neutral background by default; optional Samsung adjustment is available below. Excludes notification-card color curves. Glass strength scales the radius.")
        Slider(value=systemRadius,onValueChange={systemRadius=it;prefs.edit().putFloat("notification_blur_radius",it).apply()},valueRange=0f..500f)
        TextButton(onClick={systemRadius=250f;prefs.edit().putFloat("notification_blur_radius",250f).apply()}){Text("Reset notification blur radius")}
        var blurAdjusted by remember { mutableStateOf(prefs.getBoolean("notification_blur_adjusted",false)) }
        var blurSolid by remember { mutableStateOf(prefs.getBoolean("notification_blur_solid_mask",false)) }
        Toggle("Samsung background adjustment · A/B test",blurAdjusted){blurAdjusted=it;prefs.edit().putBoolean("notification_blur_adjusted",it).apply()}
        Text("Turn off to test neutral blacks. On reproduces alpha.13. This changes only the Samsung blur experiment.",style=MaterialTheme.typography.bodySmall)
        Toggle("Solid blur mask · diagnostic",blurSolid){blurSolid=it;prefs.edit().putBoolean("notification_blur_solid_mask",it).apply()}
        Text("Off: broad, smooth feathering. On: same coverage and radius with full opacity, isolating edge mixing from the blur itself. A hard edge is expected in this test.",style=MaterialTheme.typography.bodySmall)
        var blurMeter by remember { mutableStateOf(GlassRenderOptions.blurMeters()) }
        OutlinedButton(onClick={blurMeter=GlassRenderOptions.blurMeters()}){Text("Refresh retained blur meters")}
        Text(blurMeter,style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={val report=GlassRenderOptions.report(this@MainActivity)+"\nSelected BG adjustment=$blurAdjusted; solid mask=$blurSolid; radius=$systemRadius screen px";getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Blur diagnostics",report))}){Text("Copy blur diagnostics")}
        Text("Live Screen Mode (Legacy) only. Normal remains the default. Both Samsung options use a wider moving sweep with a soft feathered edge: cover right edge across the screen; inner left edge toward the midpoint, keeping the right half clear. Notification blur + stretch retains projection. Android may suppress blur. These are visual experiments, not a proven speed improvement.",style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,CameraContinuityActivity::class.java))}){Text("Camera-style continuity test")}
        OutlinedButton(onClick={val report=StandaloneService.instance?.screenshotReport()?:"Accessibility disconnected";getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Screenshot mode test",report));android.widget.Toast.makeText(this@MainActivity,"Screenshot report copied",android.widget.Toast.LENGTH_SHORT).show()}){Text("Copy screenshot mode test report")}
        Text("Composition image diagnostic",style=MaterialTheme.typography.titleMedium)
        Text("Off by default. Arm next 6 folds, then return Home and fold normally with animation enabled. Home records the inner display throughout motion and for one second after cleanup, targeting 8 samples per second. The ZIP includes a frame viewer; extract it and open review.html. Missing or slow captures remain labelled gaps. Ordinary app diagnostics keep their existing samples. Images include screen content; inspect before sharing. Arming lasts 10 minutes. Keeps the latest 24 sample folders. Save ZIP to Downloads and attach it from your files.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={CompositionDiagnostic.arm(this@MainActivity);android.widget.Toast.makeText(this@MainActivity,"Image diagnostic armed for six folds",android.widget.Toast.LENGTH_SHORT).show()}){Text("Arm next 6 folds")}
        OutlinedButton(enabled=!exporting,onClick={scope.launch{exporting=true;try{
         val saved=withContext(Dispatchers.IO){DiagnosticDownloads.save(this@MainActivity,CompositionDiagnostic.archive(this@MainActivity))}
         android.widget.Toast.makeText(this@MainActivity,"Saved to Downloads/DuoFoldLive: $saved",android.widget.Toast.LENGTH_LONG).show()
        }catch(e:Exception){android.widget.Toast.makeText(this@MainActivity,"ZIP not saved: ${e.message}",android.widget.Toast.LENGTH_LONG).show()}finally{exporting=false}}}){Text("Save ZIP to Downloads")}
        OutlinedButton(enabled=!exporting,onClick={scope.launch{exporting=true;try{val intent=withContext(Dispatchers.IO){CompositionDiagnostic.export(this@MainActivity)};startActivity(intent)}catch(e:Exception){android.widget.Toast.makeText(this@MainActivity,e.message,android.widget.Toast.LENGTH_LONG).show()}finally{exporting=false}}}){Text("Share composition images")}
        TextButton(onClick={CompositionDiagnostic.clear(this@MainActivity)}){Text("Clear composition images")}
        Text("Incoming ghosting diagnostic",style=MaterialTheme.typography.titleMedium)
        Text("Duo Mode only. Choose a test, then fold/unfold in a fullscreen app. Raw capture shows the captured image without distortion. Solid magenta covers the animated region; text should not show through it. Return to Normal animation afterward. Resets when the app process restarts.",style=MaterialTheme.typography.bodySmall)
        IncomingGhostDiagnostic.labels.forEachIndexed { index,label ->
         OutlinedButton(onClick={IncomingGhostDiagnostic.select(index)}){Text((if(IncomingGhostDiagnostic.mode==index)"✓ " else "")+label)}
        }
        Text("Screen continuity test",style=MaterialTheme.typography.titleMedium)
        Text("Arm, return to Home or the app you want to test, fully close, then unfold within 30 seconds. During the 20-second hold, opening past 98° tries moving real content to the inner display. The inner animation uses the transferred app. Check that content stays visible, with full-size layout, touch and navigation. Folding below 94° returns content; only one transfer per test. Temporarily requests system navigation on the inner display; restores the prior display policy after the test. Samsung may reject Home routing. Exit may still flash. Copy the connection report afterward.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=enabled&&liveMirror&&!screenshotMode,onClick={LiveAngles.startContinuityProbe()}){Text("Arm 20-second continuity test")}
        TextButton(onClick={LiveAngles.cancelContinuityProbe()}){Text("Stop continuity test")}
        Text(LiveAngles.continuityStatus,style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        if(!screenshotMode){
         Toggle("Cover preview on inner screen (experimental)",liveMirror){liveMirror=it;booleanSetting("cover_preview",it)}
         Text("Mirrors cover content during Live Screen Mode (Legacy) transitions, using your saved glass settings.",style=MaterialTheme.typography.bodySmall)
        }
        Text(GlassFrames.status,style=MaterialTheme.typography.bodySmall)
        Text(HandoffFrames.status,style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,WallpaperTestActivity::class.java))}){Text("Photo wallpaper hinge test")}
        OutlinedButton(onClick={startActivity(Intent(this@MainActivity,FoldProbeActivity::class.java))}){Text("Sensor & display diagnostics")}
          Text(FoldAwakeDefault.status,style=MaterialTheme.typography.bodySmall)
          Text(status,style=MaterialTheme.typography.bodySmall)
          Text(LiveAngles.rotationHold,style=MaterialTheme.typography.bodySmall)
          OutlinedButton(onClick={startActivity(Intent(this@MainActivity,org.duofold.live.wallpaperlayer.WallpaperActivity::class.java).putExtra("developer",true))}){Text("Wallpaper developer settings")}
          if(!BuildConfig.IS_PLAY)OutlinedButton(onClick={val sent=SupportPrompts.notify(this@MainActivity);android.widget.Toast.makeText(this@MainActivity,if(sent)"Preview notification sent; reminder schedule unchanged" else "Allow support notifications in Android settings",android.widget.Toast.LENGTH_LONG).show()}){Text("Preview support notification")}
        HorizontalDivider()
        var wakePhotoTest by remember { mutableStateOf(org.duofold.live.wallpaperlayer.WakePreparationTest.enabled(this@MainActivity)) }
        val wakeViewConfiguration=remember(baseViewConfiguration){object:ViewConfiguration by baseViewConfiguration{override val longPressTimeoutMillis=3000L}}
        CompositionLocalProvider(LocalViewConfiguration provides wakeViewConfiguration){
         Row(Modifier.fillMaxWidth().combinedClickable(
          onClick={if(!wakePhotoTest){wakePhotoTest=true;prefs.edit().putBoolean(org.duofold.live.wallpaperlayer.WakePreparationTest.KEY,true).apply()}else android.widget.Toast.makeText(this@MainActivity,"Hold for 3 seconds to turn off",android.widget.Toast.LENGTH_SHORT).show()},
          onLongClick=if(wakePhotoTest){{wakePhotoTest=false;prefs.edit().putBoolean(org.duofold.live.wallpaperlayer.WakePreparationTest.KEY,false).apply()}}else null,
          onLongClickLabel="Turn off wallpaper wake preparation",
          role=androidx.compose.ui.semantics.Role.Switch
         ).semantics{stateDescription=if(wakePhotoTest)"On" else "Off"},verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
          Text("Prepare custom wallpaper before unlock",Modifier.weight(1f))
          Switch(checked=wakePhotoTest,onCheckedChange=null)
         }
        }
        Text("On by default when custom wallpaper is enabled. Keeps your photo ready beneath the lock-screen interface for unlocking. Developer override: hold the toggle for 3 seconds to turn off; tap to turn it back on. Saved lock-screen wallpaper is unchanged.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={val report=org.duofold.live.wallpaperlayer.WakePreparationTest.report(this@MainActivity);(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Wallpaper wake test",report));android.widget.Toast.makeText(this@MainActivity,"Wallpaper wake report copied",android.widget.Toast.LENGTH_SHORT).show()}){Text("Copy wallpaper wake test report")}
         }
        }

       }
       Button(onClick={val report=StandaloneService.instance?.report(includeHealthHistory=false)?:"Duo Fold Live: accessibility disconnected";getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Duo Fold Live",report));android.widget.Toast.makeText(this@MainActivity,"Status report copied",android.widget.Toast.LENGTH_SHORT).show()}){Text("Copy status report")}
       TextButton(colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.tertiary),onClick={val report=StandaloneService.instance?.report(includeHealthHistory=true)?:"Duo Fold Live: accessibility disconnected";getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Duo Fold Live",report));android.widget.Toast.makeText(this@MainActivity,"Report with health history copied",android.widget.Toast.LENGTH_SHORT).show()}){Text("With health history",style=MaterialTheme.typography.labelSmall)}
       if(advanced){
      SettingsCard("Notifications","Manage Android notification visibility."){
       Text("Hide the persistent running notification in Samsung’s notification settings. Duo Fold Live will continue running.",style=MaterialTheme.typography.bodySmall)
       OutlinedButton(onClick={
        FoldBackgroundService.createNotificationChannel(this@MainActivity)
        try{startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName).putExtra(Settings.EXTRA_CHANNEL_ID,"fold_live"))}
        catch(e:ActivityNotFoundException){startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}
       }){Text("Running notification settings")}
       if(!BuildConfig.IS_PLAY)Text("Support notifications are enabled automatically, subject to Android notification settings. Reminder scheduling, snooze, and stop actions still apply.",style=MaterialTheme.typography.bodySmall)
      }
       }
      }
      if(!BuildConfig.IS_PLAY)SettingsCard("Support Duo Fold Live","Free, independent, and built with care for your Fold."){
       Text("Donations support development. All features remain free.")
       Button(onClick={SupportPrompts.open(this@MainActivity);supportBanner=false}){Text("Support on Ko-fi")}
       InfoLabel("Support reminders","First shown after 3 days of successful use, then 7 days later, 14 days later, and every 30 days. You can snooze or stop reminders.")
      }
      Text("Duo Fold Live ${BuildConfig.VERSION_NAME} · Glass reference: chuspeeism/iphone-duo (MIT).",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      Text("Duo Fold Live is an open-source project, provided “as is” without warranties. Use it at your own risk. To the extent permitted by applicable law, the authors and contributors are not liable for device damage, data loss, malfunctions, or other harm arising from its use. Not affiliated with or endorsed by Samsung.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
   }
  }
 }
}
@Composable internal fun SettingsCard(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit){
 Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f)),modifier=Modifier.fillMaxWidth()){
  Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text(title,style=MaterialTheme.typography.titleLarge);if(subtitle.isNotEmpty())Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);content()
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
