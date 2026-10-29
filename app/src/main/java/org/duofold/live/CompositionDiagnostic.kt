package org.duofold.live

import android.content.*
import android.graphics.*
import android.os.*
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.SurfaceControl
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Bounded, read-only evidence. Never changes exclusion flags, opacity, or handoff gates. */
internal object CompositionDiagnostic {
 private val main=Handler(Looper.getMainLooper())
 private val worker=Executors.newSingleThreadExecutor()
 private var busy=false
 private var remaining=0
 private var animationPaused=false
 private var episode=0L
 private var attempts=0
 private val seen=HashSet<String>()
 private val rows=ArrayDeque<String>()
 @Volatile private var generation=0
 internal val fileLock=Any()
 private var prefs:SharedPreferences?=null
 private fun initialize(c:Context){
  if(prefs!=null)return
  prefs=c.applicationContext.getSharedPreferences("composition-diagnostic",0)
  val p=prefs!!
  if(p.getString("build","")==BuildConfig.VERSION_NAME && System.currentTimeMillis() in p.getLong("armedAt",0)..p.getLong("expiresAt",0)){
   remaining=p.getInt("remaining",0).coerceIn(0,6)
   if(remaining>0)record("RESTORED armed request; remaining=$remaining")
  }
 }
 private fun persist(){prefs?.edit()?.putInt("remaining",remaining)?.apply()}
 private fun record(s:String){val row="${SystemClock.elapsedRealtime()} $s";synchronized(rows){rows.addLast(row);while(rows.size>40)rows.removeFirst()};prefs?.edit()?.putString("lastStatus",row)?.apply();RecoveryLog.add("COMPOSITION_EYES $row")}
 fun pauseForAnimation(c:Context,paused:Boolean){initialize(c);val changed=animationPaused!=paused;animationPaused=paused;if(changed&&paused&&remaining>0)record("PAUSED: standard animation + glass is ON; image comparisons require it OFF; fold budget preserved")}
 fun report()="Composition image diagnostic: remaining folds=$remaining busy=$busy animationPaused=$animationPaused. Captured composition, NOT optical panel proof. Moving captures: full panel at <=320px longest side. Static comparisons: central 80%; secure/skip-screenshot layers may be omitted.\n"+synchronized(rows){rows.joinToString("\n")}+"\n"+HomeInnerDiagnostic.report()
 fun arm(c:Context){HomeInnerDiagnostic.stop("rearmed");initialize(c);generation++;remaining=6;episode=0;seen.clear()
  val now=System.currentTimeMillis()
  prefs!!.edit().putString("build",BuildConfig.VERSION_NAME).putLong("armedAt",now).putLong("expiresAt",now+600_000).putInt("remaining",remaining).apply()
  record("ARMED next 6 coordinated folds; expires in 10 minutes; images saved locally")
  animationPaused=Build.VERSION.SDK_INT<35&&c.getSharedPreferences("standalone",0).getBoolean(HandoffVisualPolicy.KEY,false)
  if(animationPaused)record("PAUSED: standard animation + glass is ON; image comparisons require it OFF; fold budget preserved")
 }
 fun active(token:Long)=episode==token&&attempts<4
 fun begin(c:Context,token:Long,homeInner:Boolean=false):Boolean {
  initialize(c);if(token==episode)return false
  if(System.currentTimeMillis()>prefs!!.getLong("expiresAt",0)){remaining=0;persist()}
  episode=token;attempts=if(homeInner)4 else 0;seen.clear()
  if(remaining>0){remaining--;persist();record("SESSION $token armed; "+if(homeInner)"Home inner sequence through cleanup" else "at most 4 samples");return true}
  attempts=4;return false
 }
 internal fun folder(c:Context)=File(c.filesDir,"composition-evidence").apply{mkdirs()}
 fun sample(c:Context,token:Long,task:Int,physical:String,display:Int,frame:GlassFrame,view:SurfaceView,rasterW:Int,rasterH:Int,stage:String,valid:()->Boolean){
  val key="$physical:$display:${frame.stamp}"
  if(busy||episode!=token||attempts>=4||key in seen||!valid())return
  seen.add(key);attempts++;busy=true
  val epoch=generation;val started=SystemClock.elapsedRealtime()
  val scale=320f/maxOf(frame.width,frame.height);val w=(frame.width*scale).toInt().coerceAtLeast(1);val h=(frame.height*scale).toInt().coerceAtLeast(1)
  val images=linkedMapOf<String,Bitmap>()
  val notes=StringBuilder("build=${BuildConfig.VERSION_NAME}\nsession=$token task=$task physical=$physical display=$display stage=$stage\nsourceStamp=${frame.stamp} sourcePhysical=${frame.physicalId} sourceDisplay=${frame.displayId} rotation=${frame.rotation}\nlogical=${frame.width}x${frame.height} raster=${rasterW}x$rasterH comparison=${w}x$h\nCapture exclusion list for source: ${frame.exclusions}\n")
  val exclusions=GlassFrames.probeExclusions()
  val photos=runCatching{org.duofold.live.wallpaperlayer.WallpaperProbeSurfaces.snapshot()}.getOrDefault(emptyArray())
  fun recycle(){images.values.distinct().forEach{if(!it.isRecycled)it.recycle()}}
  fun finish(reason:String){
   notes.append("result=$reason\nspanMs=${SystemClock.elapsedRealtime()-started}\n")
   val text=notes.toString()
   worker.execute{
    var saved=""
    try{
     synchronized(fileLock){if(epoch==generation){
      val root=folder(c);val dir=File(root,"$token-D$display-$started").apply{mkdirs()}
      images.forEach{(name,b)->File(dir,"$name.png").outputStream().use{check(b.compress(Bitmap.CompressFormat.PNG,100,it))}}
      File(dir,"evidence.txt").writeText(text)
      CompositionRetention.expired(root.listFiles()?:emptyArray(),24).forEach{it.deleteRecursively()}
      saved="saved=${dir.name}"
     }else saved="discarded: rearmed/cleared"}
    }catch(e:Exception){saved="SAVE_FAILED ${e.message}"}finally{recycle()}
    main.post{busy=false;record("session=$token D$display stage=$stage $reason $saved")}
   }
  }
  try{
   // Keep original capture pixels (bounded to the existing capture resolution).
   images["01-source-capture"]=frame.bitmap.copy(Bitmap.Config.ARGB_8888,false)?:error("Source unreadable")
   val raster=Bitmap.createBitmap(rasterW,rasterH,Bitmap.Config.ARGB_8888)
   Canvas(raster).apply{scale(rasterW.toFloat()/frame.width,rasterH.toFloat()/frame.height);drawScreenshotBackdrop(this,frame,frame.width,frame.height)}
   images["02-expected-render"]=Bitmap.createScaledBitmap(raster,w,h,true);if(images["02-expected-render"]!==raster)raster.recycle()
   val buffer=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);images["03-rendered-buffer"]=buffer
   PixelCopy.request(view,buffer,firstCopy@{result->
    if(result!=PixelCopy.SUCCESS){finish("INCONCLUSIVE PixelCopy=$result");return@firstCopy}
    if(epoch!=generation||!valid()){finish("INCONCLUSIVE scene changed during buffer readback");return@firstCopy}
    worker.execute{
     var error:String?=null
     try{
      for((name,exclude) in listOf("04-composition-A" to emptyArray<SurfaceControl>(),"05-underlay" to exclusions,"06-underlay-without-photo" to (exclusions+photos),"07-composition-B" to emptyArray<SurfaceControl>())){
       check(SystemClock.elapsedRealtime()-started<2000){"sample budget exceeded"}
       val p=Parcel.obtain();val r=Parcel.obtain()
       try{
        p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(exclude.size);exclude.forEach{p.writeTypedObject(it,0)};p.writeInt(display);p.writeInt(task);p.writeString(physical)
        check(LiveAngles.captureBinder().transact(6,p,r,0)){"Capture operation unavailable"};r.readException()
        val b=r.readBundle(Bitmap::class.java.classLoader)?:error("No capture reply")
        val image=b.getParcelable("bitmap",Bitmap::class.java)
        if(!b.getBoolean("ok")||image==null){image?.recycle();error(b.getString("error")?:"No image")}
        images[name]=image
        check(b.getString("physicalId")==physical&&b.getInt("width")==frame.width&&b.getInt("height")==frame.height&&b.getInt("rotation")==frame.rotation&&kotlin.math.abs(image.width-w)<=1&&kotlin.math.abs(image.height-h)<=1){"Capture mapping/geometry changed"}
        if(image.width!=w||image.height!=h){images[name]=Bitmap.createScaledBitmap(image,w,h,true);image.recycle();notes.append("$name normalized capture rounding to ${w}x$h\n")}
        notes.append("$name stamp=${b.getLong("stamp")} excluded=${exclude.size} provenance=${b.getString("provenance")}\n")
       }finally{p.recycle();r.recycle()}
      }
     }catch(e:Exception){error=e.message?:e.javaClass.simpleName}
     val failure=error
     main.post{
      if(failure!=null||epoch!=generation||!valid()||SystemClock.elapsedRealtime()-started>2000){finish("INCONCLUSIVE ${failure?:"scene changed or sample expired"}");return@post}
      // A second buffer readback detects changes during composition capture.
      val end=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);images["08-rendered-buffer-end"]=end
      try{PixelCopy.request(view,end,endCopy@{code->
       if(code!=PixelCopy.SUCCESS||epoch!=generation||!valid()){finish("INCONCLUSIVE end buffer=$code or scene changed");return@endCopy}
       worker.execute{
        try{
        fun pixels(name:String)=IntArray(w*h).also{images.getValue(name).getPixels(it,0,w,0,0,w,h)}
        fun compare(a:String,b:String)=CompositionEvidence.compare(pixels(a),pixels(b),w,h)
        val stable=compare("04-composition-A","07-composition-B")
        val bufferStable=compare("03-rendered-buffer","08-rendered-buffer-end")
        val render=compare("02-expected-render","03-rendered-buffer")
        val composed=compare("03-rendered-buffer","04-composition-A")
        notes.append("compositionStability=$stable\nbufferStability=$bufferStable\nexpectedVsBuffer=$render\nbufferVsComposition=$composed\n")
        notes.append("Underlay/photo samples are sequential visual aids, not an A/B/A wallpaper-contribution verdict. A match cannot certify app-only source content.\n")
        val result=if(!stable.startsWith("MATCH")||!bufferStable.startsWith("MATCH"))"INCONCLUSIVE unstable scene" else "expectedVsBuffer=$render; bufferVsComposition=$composed"
        main.post{finish(if(epoch==generation&&valid()&&SystemClock.elapsedRealtime()-started<=2500)result else "INCONCLUSIVE scene changed before analysis completed")}
        }catch(e:Exception){main.post{finish("INCONCLUSIVE analysis failed: ${e.message}")}}
       }
      },main)}catch(e:Exception){finish("INCONCLUSIVE ${e.message}")}
     }
    }
   },main)
  }catch(e:Exception){finish("INCONCLUSIVE ${e.message}")}
 }
 /** Moving frames are evidence images, never stability comparisons or passes. */
 fun movingSample(c:Context,token:Long,task:Int,physical:String,display:Int,frame:GlassFrame,stage:String,revision:Int,workspaceTasks:IntArray?=null,valid:()->Boolean){
  val key="moving:$stage:$physical"
  if(busy||!active(token)||key in seen||display !in 0..1||!valid())return
  seen.add(key);attempts++;busy=true
  val epoch=generation;val started=SystemClock.elapsedRealtime()
  val source=frame.bitmap
  worker.execute{
   val images=linkedMapOf<String,Bitmap>()
   val notes=StringBuilder("build=${BuildConfig.VERSION_NAME}\nsession=$token physical=$physical display=$display stage=$stage revision=$revision workspace=${workspaceTasks?.contentToString()?:"none"}\nsourcePhysical=${frame.physicalId} sourceStamp=${frame.stamp} sourceEvidence=${frame.exclusions}\nMOVING SCENE: sequential samples; no pixel-equality verdict; capture is not optical proof.\nNo static shield is allocated on this route.\n")
   var result="RECORDED"
   try{
    val scale=320f/maxOf(source.width,source.height)
    images["01-source-image"]=Bitmap.createScaledBitmap(source,(source.width*scale).toInt().coerceAtLeast(1),(source.height*scale).toInt().coerceAtLeast(1),true).let{if(it===source)it.copy(Bitmap.Config.ARGB_8888,false)!! else it}
    for(n in 0..1){
     check(SystemClock.elapsedRealtime()-started<1500){"capture budget expired"}
     val p=Parcel.obtain();val r=Parcel.obtain()
     try{p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(display)
      if(workspaceTasks==null){p.writeInt(task);p.writeString(physical)}else{p.writeString(physical);p.writeIntArray(workspaceTasks)}
      check(LiveAngles.captureBinder().transact(if(workspaceTasks==null)7 else 14,p,r,0)){"Physical composition diagnostic unavailable"};r.readException()
      val out=r.readBundle(Bitmap::class.java.classLoader)?:error("No capture result")
      val image=out.getParcelable("bitmap",Bitmap::class.java)
      if(!out.getBoolean("ok")||image==null){image?.recycle();error(out.getString("error")?:"No physical composition")}
      images["0${n+2}-physical-composition"]=image
      notes.append("sample=$n stamp=${out.getLong("stamp")} pixels=${image.width}x${image.height} backend=${out.getString("backend")} windowObservation=${out.getString("provenance")}\n")
     }finally{p.recycle();r.recycle()}
    }
   }catch(e:Exception){result="INCONCLUSIVE ${e.message}"}
   val outcome=result
   main.post{
    val current=epoch==generation&&valid()
    worker.execute{
     try{synchronized(fileLock){if(epoch==generation){
      val dir=File(folder(c),"$token-moving-D$display-$started").apply{mkdirs()}
      images.forEach{(name,image)->File(dir,"$name.png").outputStream().use{check(image.compress(Bitmap.CompressFormat.PNG,100,it))}}
      File(dir,"evidence.txt").writeText(notes.toString()+"result=$outcome sceneStillCurrent=$current spanMs=${SystemClock.elapsedRealtime()-started}\n")
      CompositionRetention.expired(folder(c).listFiles()?:emptyArray(),24).forEach{it.deleteRecursively()}
     }}}catch(e:Exception){notes.append("SAVE_FAILED ${e.message}")}
     finally{images.values.forEach{it.recycle()};main.post{busy=false;record("MOVING session=$token D$display $stage $outcome current=$current; $notes")}}
    }
   }
  }
 }
 fun clear(c:Context){HomeInnerDiagnostic.stop("cleared");initialize(c);generation++;remaining=0;persist();attempts=4;worker.execute{synchronized(fileLock){folder(c).deleteRecursively();File(c.cacheDir,"exports").listFiles()?.filter{it.name.startsWith("Duo-composition-")&&it.extension=="zip"}?.forEach{it.delete()}};main.post{record("CLEARED stored image evidence; capture stopped")}}}
 fun archive(c:Context):File=synchronized(fileLock){
  val root=folder(c);val dirs=root.listFiles()?.filter{it.isDirectory&&File(it,"evidence.txt").isFile&&File(it,"evidence.txt").useLines{lines->lines.firstOrNull()=="build=${BuildConfig.VERSION_NAME}"}}?:emptyList()
  val dest=File(c.cacheDir,"exports").apply{mkdirs()};val zip=File(dest,"Duo-composition-${System.currentTimeMillis()}.zip")
  dest.listFiles()?.filter{it.name.startsWith("Duo-composition-")&&it.extension=="zip"}?.sortedByDescending{it.name}?.drop(2)?.forEach{it.delete()}
  val diagnostic=report()+"\nPersisted last status: "+c.getSharedPreferences("composition-diagnostic",0).getString("lastStatus","none")
  ZipOutputStream(zip.outputStream().buffered()).use{out->
   out.putNextEntry(ZipEntry("capture-status.txt"));out.write(("build=${BuildConfig.VERSION_NAME}\ncurrentBuildSampleFolders=${dirs.size}\n"+diagnostic+"\n"+if(dirs.isEmpty()) "NO CURRENT-BUILD IMAGES: diagnostic-only export; not a capture pass.\n" else "").toByteArray());out.closeEntry()
   out.putNextEntry(ZipEntry("README.txt"));out.write("Export build=${BuildConfig.VERSION_NAME}; current-build samples only.\nHome-inner-sequence folders contain numbered physical PNGs, source thumbnails, frames.json and review.html with playback. Extract the ZIP before opening review.html. Only the inner physical panel is sampled for Home; the sequence continues for up to one second after cleanup. Target 8 samples/s; slow or unavailable captures are gaps, not passes. App diagnostics retain their existing four-sample behavior.\nMoving-scene folders: 01 source and 02/03 physical compositor samples; sequential moving images, no comparison verdict. Missing physical capture is INCONCLUSIVE. Captured app images: review before sharing. 01 source; 02 intended render; 03/08 PixelCopy buffer; 04/07 display composition with NO explicit exclusions; 05 underlay excluding Duo presentation; 06 underlay also excluding registered photo layers. Capture APIs may omit secure/skip-screenshot layers. No optical proof. Observed shield intervals rejected; software phase sampling may miss transitions. Thresholds: central 80%, RGB delta>24 in <=1% and mean max RGB<=6 is a tolerant match, not exact equality.\n".toByteArray());out.closeEntry()
   dirs.forEach{dir->dir.listFiles()?.filter{it.isFile}?.forEach{f->out.putNextEntry(ZipEntry("${dir.name}/${f.name}"));f.inputStream().use{it.copyTo(out)};out.closeEntry()}}
  }
  zip
 }
 fun export(c:Context):Intent {
  val uri=FileProvider.getUriForFile(c,c.packageName+".files",archive(c))
  return Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/zip";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("Composition evidence",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share composition images")
 }
}
