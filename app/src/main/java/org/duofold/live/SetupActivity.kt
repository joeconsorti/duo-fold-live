package org.duofold.live

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** First-install wizard. Existing users never enter this activity automatically. */
class SetupActivity : ComponentActivity() {
 private val prefs by lazy { getSharedPreferences("first_run",0) }
 private var stage by mutableIntStateOf(0)
 private var message by mutableStateOf("")
 private var applying by mutableStateOf(false)
 private var attempted=false
 private var bound=false
 private val args by lazy { Shizuku.UserServiceArgs(ComponentName(this,WallpaperSetupService::class.java)).daemon(false).processNameSuffix("wallpaper_setup").version(24) }
 private val listener=Shizuku.OnRequestPermissionResultListener { _, result ->
  runOnUiThread { message=if(result==PackageManager.PERMISSION_GRANTED)"Shizuku authorized. Continue with wallpaper setup below." else "Shizuku access was declined. Tap Authorize to try again." }
 }
 private val connection=object:ServiceConnection {
  override fun onServiceConnected(name:ComponentName,binder:IBinder){
   Thread {
    var success=false
    val result=try {
     val input=Parcel.obtain();val output=Parcel.obtain()
     try {
      input.writeInterfaceToken(WallpaperSetupService.TOKEN)
      check(binder.transact(1,input,output,0)){"Setup helper did not respond"}
      output.readException();success=output.readInt()==1;output.readString() ?: "No result"
     } finally { input.recycle();output.recycle() }
    } catch(e:Exception){"Setup failed: ${e.message}. Tap Retry."}
    runOnUiThread {
     applying=false;message=result
     if(success){prefs.edit().putBoolean("wallpaper_verified",true).apply();go(3)}
     releaseHelper()
    }
   }.start()
  }
  override fun onServiceDisconnected(name:ComponentName){bound=false;applying=false;message="Setup helper disconnected. Restart Shizuku if needed, then retry."}
 }
 private fun releaseHelper(){if(bound){runCatching{Shizuku.unbindUserService(args,connection,true)};bound=false}}
 private fun go(step:Int){stage=step;prefs.edit().putInt("step",step).apply()}
 private fun startApply(){
  if(applying)return
  attempted=true;applying=true;message="Applying the required fold wallpaper to both home screens…"
  try {bound=true;Shizuku.bindUserService(args,connection)}
  catch(e:Exception){bound=false;applying=false;message="Could not start setup: ${e.message}. Tap Retry."}
 }
 private fun open(intent:Intent){runCatching{startActivity(intent)}.onFailure{Toast.makeText(this,"This screen is unavailable. Open it in Settings.",Toast.LENGTH_LONG).show()}}
 private fun link(url:String)=open(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  stage=prefs.getInt("step",0)
  Shizuku.addRequestPermissionResultListener(listener)
  setContent {
   MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xffc9bdff),background=Color(0xff10121c),surface=Color(0xff1b1e2b))){
    val scope=rememberCoroutineScope()
    var custom by remember{mutableStateOf(prefs.getBoolean("custom",false))}
    var hasPhoto by remember{mutableStateOf(File(filesDir,"photo.jpg").exists())}
    var loading by remember{mutableStateOf(false)}
    var shizuku by remember{mutableStateOf(false)}
    var authorized by remember{mutableStateOf(false)}
    var overlay by remember{mutableStateOf(false)}
    var accessibility by remember{mutableStateOf(false)}
    var usb by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){while(true){
     shizuku=runCatching{Shizuku.pingBinder()}.getOrDefault(false)
     authorized=shizuku && runCatching{Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED}.getOrDefault(false)
     overlay=Settings.canDrawOverlays(this@SetupActivity)
     accessibility=StandaloneService.instance!=null

     delay(500)
    }}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){
     loading=true;message="Preparing your photo…"
     scope.launch {
      runCatching {withContext(Dispatchers.IO){
       val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver,uri)){decoder,info,_->
        decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        val maximum=maxOf(info.size.width,info.size.height)
        if(maximum>1600)decoder.setTargetSampleSize((maximum+1599)/1600)
       }
       try {
        val temp=File(filesDir,"setup-photo.tmp")
        temp.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)){"Image encoding failed"}}
        Files.move(temp.toPath(),File(filesDir,"photo.jpg").toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
       }finally{bitmap.recycle()}
      }}.onSuccess{hasPhoto=true;message="Photo selected. It will appear behind your home-screen icons."}
       .onFailure{message="Photo could not be loaded: ${it.message}"}
      loading=false
     }
    }}
    Surface(Modifier.fillMaxSize()){
     Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
      Text("Duo Fold Live",color=Color(0xff91ded2),style=MaterialTheme.typography.labelLarge)
      Text("Make the fold yours.",style=MaterialTheme.typography.headlineLarge)
      Text("Setup ${stage+1} of 4",style=MaterialTheme.typography.labelLarge)
      LinearProgressIndicator(progress={(stage+1)/4f},modifier=Modifier.fillMaxWidth())
      if(!DeviceCompatibility.isRecognized(Build.MODEL)) Text(DeviceCompatibility.modelWarning(Build.MODEL),color=MaterialTheme.colorScheme.error)
      when(stage){
       0->{
        Text("The wallpaper that powers the fold",style=MaterialTheme.typography.headlineSmall)
        Text("Duo uses Samsung’s interactive fold wallpaper to read the hinge angle. Setup will apply it to BOTH the inner and cover home screens, including the cover-screen option Samsung normally hides.")
        Text("This replaces your current home wallpapers and remains applied if you disable Duo. You can change them later in Samsung wallpaper settings. Your lock-screen wallpaper is not changed by this setup.")
        Text("Next, choose whether to cover it with your own photo. Then we’ll connect Shizuku and apply the required wallpaper automatically. No root is needed.")
        Text("Regional SM-F971, SM-F976, and SM-F966 model numbers are recognized. Other models may also proceed with a warning. Android 17 is still required; Samsung wallpaper components and APIs are checked separately. Only SM-F971U has been tested.",style=MaterialTheme.typography.bodySmall)
        Button(onClick={go(1)}){Text("Continue")}
       }
       1->{
        Text("Use a custom wallpaper?",style=MaterialTheme.typography.headlineSmall)
        Text("Your photo can sit behind the icons while Samsung’s wallpaper keeps supplying live hinge angles underneath.")
        FilterChip(selected=!custom,onClick={custom=false;prefs.edit().putBoolean("custom",false).apply()},label={Text("No · use Samsung’s wallpaper")})
        FilterChip(selected=custom,onClick={custom=true;prefs.edit().putBoolean("custom",true).apply()},label={Text("Yes · choose my photo")})
        if(custom){
         OutlinedButton(onClick={picker.launch(arrayOf("image/*"))},enabled=!loading){Text(if(hasPhoto)"Change selected photo" else "Choose photo")}
         Text("The custom photo can briefly reveal Samsung’s wallpaper when unlocking. This existing limitation is still being worked on.",style=MaterialTheme.typography.bodySmall)
        }
        Button(onClick={message="";go(2)},enabled=!loading&&(!custom||hasPhoto)){Text("Continue to Shizuku")}
       }
       2->{
        Text("Connect Shizuku",style=MaterialTheme.typography.headlineSmall)
        Text("Shizuku gives Duo the access needed to install the fold wallpaper on both screens and read live hinge angles. After authorization, tap Apply required wallpapers below. Wallpaper compatibility is checked separately.")
        Text(if(authorized)"Shizuku connected and authorized" else if(shizuku)"Shizuku connected · authorization needed" else "Duo has not received a Shizuku connection")
        Button(onClick={link("https://github.com/thejaustin/ShizukuPlus/releases")}){Text("Download Shizuku+ (Recommended)")}
        Text("Recommended for automatic recovery features. Compatibility with Duo is still being verified. Download the APK from GitHub Releases and use ADB mode.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={link("https://shizuku.rikka.app/download/")}){Text("Official Shizuku (Alternative)")}
        OutlinedButton(onClick={val intent=packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api");if(intent!=null)open(intent)else link("https://github.com/thejaustin/ShizukuPlus/releases")}){Text("Open installed Shizuku")}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
         FilterChip(selected=!usb,onClick={usb=false},label={Text("Wireless")})
         FilterChip(selected=usb,onClick={usb=true},label={Text("USB / computer")})
        }
        Text("In Samsung Settings → About phone → Software information, tap Build number seven times. Then open Developer options.")
        if(!usb){
         Text("Connect to Wi-Fi. Enable USB debugging and Wireless debugging. In Shizuku, tap Pairing; open Wireless debugging → Pair device with pairing code, and enter that code in Shizuku’s notification. Return to Shizuku and tap Start.")
        }else{
         Text("Enable USB debugging and connect to your computer. Download and extract Google’s platform-tools. Open PowerShell inside that extracted folder, run .\\adb.exe devices, and accept the phone’s debugging prompt. Then run the start command below, or copy the current command shown in Shizuku.")
         OutlinedButton(onClick={link("https://developer.android.com/tools/releases/platform-tools")}){Text("Download platform-tools")}
         OutlinedButton(onClick={
          (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Start Shizuku",".\\adb.exe shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"))
          Toast.makeText(this@SetupActivity,"Windows command copied",Toast.LENGTH_SHORT).show()
         }){Text("Copy Windows start command")}
         Text("Mac / Linux: use ./adb instead of .\\adb.exe.",style=MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick={open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))}){Text("Open Developer options")}
        Button(onClick={message=ShizukuAccess.request(this@SetupActivity,74)},enabled=!applying){Text(if(authorized)"Check Shizuku authorization" else "Authorize / check connection")}
        OutlinedButton(onClick={ShizukuAccess.copy(this@SetupActivity)}){Text("Copy connection report")}
        if(applying)CircularProgressIndicator()
        if(!applying&&authorized&&prefs.getBoolean("wallpaper_verified",false))Button(onClick={go(3)}){Text("Continue") }
        if(!applying&&authorized&&!prefs.getBoolean("wallpaper_verified",false))Button(onClick={startApply()}){Text(if(attempted)"Retry wallpaper setup" else "Apply required wallpapers")}
        Text("After reboot, start Shizuku again. Allow Shizuku and Duo to run in the background; keep Developer options enabled.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={link("https://shizuku.rikka.app/guide/setup/")}){Text("Official Shizuku setup guide")}
       }
       3->{
        Text("Ready for the animation",style=MaterialTheme.typography.headlineSmall)
        Text("The required Samsung fold wallpaper is configured on both home screens. Finish these Android permissions so the animation can run over other apps.")
        OutlinedButton(onClick={open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}){Text(if(overlay)"Overlay access granted" else "Allow display over other apps")}
        OutlinedButton(onClick={open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}){Text(if(accessibility)"Accessibility connected" else "Enable Duo accessibility")}
        Text("If Android blocks accessibility: App info → ⋮ → Allow restricted settings, then return to Accessibility. Duo uses screen capture and overlays for the fold effect; enable it only if you trust the app.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}){Text("Open app info")}
        OutlinedButton(onClick={if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),75)}){Text("Allow service notifications")}
        Text("Default: Windowed Glass, inspired by iPhone Duo with even more windowed glass. Choose styles in Folding animation styles. Keep-awake is always on and checked in the background.")
        Button(onClick={
         if(!authorized){message="Start Shizuku again before finishing.";attempted=false;go(2);return@Button}
         prefs.edit().putInt("state",2).commit()
         getSharedPreferences("standalone",0).edit().putBoolean("enabled",true).putBoolean("auto_keep_cover_awake",true).apply()
         startForegroundService(Intent(this@SetupActivity,FoldBackgroundService::class.java))
         StandaloneService.instance?.restart()
         startActivity(Intent(this@SetupActivity,MainActivity::class.java))
         if(custom&&hasPhoto)startActivity(Intent(this@SetupActivity,org.duofold.live.wallpaperlayer.WallpaperActivity::class.java).putExtra("auto_enable",true))
         finish()
        },enabled=overlay&&accessibility&&prefs.getBoolean("wallpaper_verified",false)){Text("Finish & enable animation")}
       }
      }
      if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
      if(stage>0&&!applying)TextButton(onClick={message="";go(stage-1)}){Text("Back")}
     }
    }
   }
  }
 }
 override fun onDestroy(){Shizuku.removeRequestPermissionResultListener(listener);if(!applying)releaseHelper();super.onDestroy()}
}
