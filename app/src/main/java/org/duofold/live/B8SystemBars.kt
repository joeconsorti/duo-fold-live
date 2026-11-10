package org.duofold.live

import android.graphics.*
import android.os.*
import android.app.KeyguardManager
import android.hardware.display.DisplayManager
import android.view.Display
import java.util.concurrent.Executors

/** Three independent session-pinned experiments. No cross-panel image fallback. */
internal class B8SystemBars(private val session:Long,private val source:String,private val mode:Int,private val changed:()->Unit) {
 private data class Tile(val kind:String,val bounds:IntArray,val bitmap:Bitmap?,val stamp:Long,val sourceBounds:Rect?=null)
 private data class Snapshot(val physical:String,val display:Int,val width:Int,val height:Int,val rotation:Int,val stamp:Long,val tiles:List<Tile>)
 private data class PanelSpec(val width:Int,val height:Int,val rotation:Int,val maxSide:Int)
 companion object {
  private val worker=Executors.newSingleThreadExecutor{job->Thread(job,"Duo-bars").apply{priority=Thread.MIN_PRIORITY}}
  private var workerBusy=false // Main-thread gate also prevents work queuing across sessions.
  private val compositionWorker=Executors.newSingleThreadExecutor{job->Thread(job,"Duo-bar-compose").apply{priority=Thread.MIN_PRIORITY}}
  private var compositionBusy=false
  private val cache=SystemBarMemoryCache<Snapshot>(2,8L*1024*1024,300_000)
  private var cacheEpoch=0
  fun clearCache(){cache.clear();cacheEpoch++}
  private fun prune(now:Long){cache.prune(now)}
 }
 private val epoch=cacheEpoch
 private val paint=Paint(Paint.FILTER_BITMAP_FLAG)
 private val main=Handler(Looper.getMainLooper())
 private val captured=HashMap<String,Snapshot>()
 private val budget=SystemBarCaptureBudget(if(mode==1)60 else if(mode==2)5 else 12,if(mode==1)400 else if(mode==2)300 else 250)
 private val pixelRefresh=SystemBarPixelRefresh(if(mode==2)1 else 2)
 private val panels=HashMap<String,PanelSpec>()
 private val discovery=HashMap<String,SystemBarWorkPolicy>()
 private var nativeReady=""
 private var workAllowed=false
 private var workEpoch=0
 private var skipped=0
 fun registerPanel(physical:String,w:Int,h:Int,rotation:Int,maxSide:Int){panels[physical]=PanelSpec(w,h,rotation,SystemBarWorkPolicy.rasterSide(w,h,maxSide))}
 fun nativeReady(physical:String){if(nativeReady!=physical){nativeReady=physical;diagnostic("native","READY physical=$physical")}}
 fun setWorkAllowed(allowed:Boolean){if(workAllowed!=allowed){workAllowed=allowed;workEpoch++;diagnostic("work","OPTIONAL_WORK allowed=$allowed epoch=$workEpoch nativeReady=$nativeReady primary=$primary")}}
 private var bakedBase:GlassFrame?=null
 private var bakedSnapshot:Snapshot?=null
 private var baked:GlassFrame?=null
 private var reportedUse:Snapshot?=null
 private var reportedUseBase:GlassFrame?=null
 private var failedBase:GlassFrame?=null
 private var failedSnapshot:Snapshot?=null
 private var compositions=0
 private var compositionMs=0L
 private var pixelCaptures=0
 private var metadataChecks=0
 private var closed=false
 private var revision=0
 private var primary=""
 private val drawing=HashMap<String,String>()
 private val events=ArrayDeque<String>()
 private val diagnostics=HashMap<String,String>()
 private val committedRevisions=HashMap<String,Int>()
 private fun diagnostic(key:String,value:String){if(diagnostics.put(key,value)!=value)FoldDiagnostics.event("BARS","session=$session mode=$mode $value")}
 fun evidence(physical:String)="barMode=$mode barRevision=$revision workAllowed=$workAllowed nativeReady=$nativeReady; ${drawing[physical]?:"bar drawing not observed"}"
 fun committed(physical:String,version:Int,at:Long){if(committedRevisions.put(physical,version)!=version)diagnostic("commit-$physical","RENDERER_COMMIT physical=$physical barRevisionAtSubmit=$version commitAt=$at; submission/commit only, not optical visibility")}

 private fun note(value:String){if(events.size>=8)events.removeFirst();events.addLast(value);RecoveryLog.add("B8 SYSTEM_BARS session=$session mode=$mode $value")}
 fun report()="System bars experiment: ${listOf("OFF","NATIVE_EXPOSURE","DESTINATION_ONLY","PER_SCREEN_CACHE").getOrElse(mode){"OFF"}}; revision=$revision; pixelCaptures=$pixelCaptures; metadataChecks=$metadataChecks; effectCompositions=$compositions; lastCompositionMs=$compositionMs; skippedOptionalResults=$skipped; optionalWorkAllowed=$workAllowed; destination discovery budget=1500ms; software evidence only\n"+events.joinToString("\n")+"\n"+drawing.values.joinToString("\n")
 fun revision()=revision
 private fun publish(value:Snapshot){
  // Retain missing current-session edges from this exact screen. Status-only
  // readiness must not erase a taskbar captured earlier in this same unfold.
  val previous=matches(captured[value.physical],value.physical,value.width,value.height,value.rotation)
  val bounds=value.tiles.map{it.bounds}.toTypedArray()
  val retained=if(mode==2)previous?.tiles.orEmpty().filter{SystemBarExperimentPolicy.keepSession(it.bounds,it.stamp,SystemClock.elapsedRealtime(),bounds)} else emptyList()
  captured[value.physical]=if(retained.isEmpty())value else value.copy(tiles=retained+value.tiles);revision++
  if(mode==3&&epoch==cacheEpoch&&value.display==0&&value.tiles.any{it.bitmap!=null}){
   prune(SystemClock.elapsedRealtime())
   // At most two physical screens and 8 MiB of cropped images; process-local only.
   val now=SystemClock.elapsedRealtime()
   val old=matches(cache.get(value.physical,now),value.physical,value.width,value.height,value.rotation)
   val freshBounds=value.tiles.map{it.bounds}.toTypedArray()
   val retained=old?.tiles.orEmpty().filter{SystemBarExperimentPolicy.keepCached(it.bounds,value.width,value.height,it.stamp,now,freshBounds)}
   val merged=value.copy(tiles=retained+value.tiles)
   val bytes=merged.tiles.sumOf{it.bitmap?.allocationByteCount?.toLong()?:0L}
   cache.put(value.physical,merged,value.stamp,bytes,now)
  }
  diagnostic("publish-${value.physical}","PUBLISHED physical=${value.physical} revision=$revision stamp=${value.stamp} geometry=${value.width}x${value.height} r=${value.rotation} tiles=${value.tiles.joinToString{it.kind+":"+it.bounds.joinToString(",")}}")
  changed()
 }
 fun accept(frame:GlassFrame){
  if(closed||mode==0||mode==1)return
  if(frame.systemBars.isEmpty()){diagnostic("metadata-${frame.physicalId}","NATIVE_FRAME_NO_BAR_METADATA physical=${frame.physicalId}");return}
  if(mode==2&&frame.physicalId==source)return
  val bitmap=frame.bitmap
  val tiles=frame.systemBars.mapNotNull{row->
   val bounds=row.getIntArray("bounds")?:return@mapNotNull null
   if(bounds.size!=4)return@mapNotNull null
   val l=bounds[0]*bitmap.width/frame.width;val t=bounds[1]*bitmap.height/frame.height
   val r=kotlin.math.ceil(bounds[2].toDouble()*bitmap.width/frame.width).toInt();val b=kotlin.math.ceil(bounds[3].toDouble()*bitmap.height/frame.height).toInt()
   if(l<0||t<0||r>bitmap.width||b>bitmap.height||r<=l||b<=t)return@mapNotNull null
   // Borrow the immutable guarded image; no crop/copy on the app-frame callback.
   // Only the comparison cache needs independently owned, bounded pixel crops.
   if(mode==2)Tile(row.getString("kind")?:"bar",bounds,bitmap,frame.stamp,Rect(l,t,r,b))
   else{val piece=Bitmap.createBitmap(bitmap,l,t,r-l,b-t);if(piece===bitmap)return@mapNotNull null;Tile(row.getString("kind")?:"bar",bounds,piece,frame.stamp)}
  }
  if(tiles.isEmpty())return
  publish(Snapshot(frame.physicalId,frame.displayId,frame.width,frame.height,frame.rotation,frame.stamp,tiles))
  note("REUSED_NATIVE_PIXELS physical=${frame.physicalId} kinds=${tiles.joinToString{it.kind}}")
 }
 fun tick(){
  if(closed||mode==0)return
  val host=StandaloneService.instance?:return
  if(host.getSystemService(KeyguardManager::class.java).isKeyguardLocked){clearCache();captured.clear();revision++;changed();return}
  // Destination-only discovery can wait for the app before querying either display.
  if(mode==2&&(!workAllowed||nativeReady.isEmpty()))return
  prune(SystemClock.elapsedRealtime())
  val d=host.getSystemService(DisplayManager::class.java).getDisplay(0)
  val physical=PanelIdentity.identity(d)
  if(physical!=primary){primary=physical;if(mode==1){captured.clear();revision++};changed()}
  // Refresh native geometry, retry missing/partial destination captures, and warm
  // each screen's own cache only while that screen is actually primary.
  val spec=panels[physical]
  val size=d?.let{B8PanelGeometry.size(it)}
  val gate=when{
   !workAllowed->"critical handoff/capture/paused phase"
   nativeReady!=physical->"native readiness pending"
   spec==null||size==null->"unknown panel geometry"
   d.state!=Display.STATE_ON->"display OFF/non-ON"
   !SystemBarWorkPolicy.geometry(spec.width,spec.height,spec.rotation,size.x,size.y,d.rotation)->"geometry mismatch"
   mode==2&&physical==source->"source panel excluded by destination-only mode"
   else->"eligible"
  }
  diagnostic("gate","GATE physical=$physical reason=$gate")
  if(gate=="eligible"&&spec!=null)request(0,physical,spec)
  if(mode==1||mode==3)changed() // Revoke expired native geometry/cached pixels even at a stationary hinge.
 }
 private fun request(display:Int,physical:String,spec:PanelSpec){
  if(closed)return
  if(workerBusy||!PanelIdentityPolicy.known(physical)){diagnostic("request","SKIP physical=$physical workerBusy=$workerBusy known=${PanelIdentityPolicy.known(physical)}");return}
  val key="$physical:D$display";val now=SystemClock.elapsedRealtime()
  val opportunity=discovery.getOrPut(physical){SystemBarWorkPolicy()}
  if(mode==2&&!opportunity.available(now)){diagnostic("request","SKIP physical=$physical discovery complete/expired");return}
  if(!budget.available(key,now)){diagnostic("request","SKIP physical=$physical capture budget/pacing");return}
  val surfaces=GlassFrames.probeExclusions()
  if(surfaces.isEmpty()||surfaces.size>64){diagnostic("request","SKIP physical=$physical invalid exclusions count=${surfaces.size}");return}
  val p=Parcel.obtain()
  try{p.writeInterfaceToken(GlassCapture.TOKEN);p.writeInt(surfaces.size);surfaces.forEach{p.writeTypedObject(it,0)};p.writeInt(display);p.writeString(physical);p.writeBoolean(mode==1);p.writeString(pixelRefresh.known(physical));p.writeInt(spec.width);p.writeInt(spec.height);p.writeInt(spec.rotation);p.writeInt(if(mode==2)spec.maxSide else 1440)}
  catch(e:Exception){p.recycle();return}
  if(!budget.start(key,now)){p.recycle();return}
  workerBusy=true
  val requestEpoch=workEpoch
  diagnostic("request","REQUEST physical=$physical requestedAt=$now rasterSide=${spec.maxSide} attempt=${budget.count(key)}")
  worker.execute{
   val workerAt=SystemClock.elapsedRealtime()
   val r=Parcel.obtain();var snapshot:Snapshot?=null;var error="No bar response";var unchanged=false;var signature="";var evidence=""
   try{
    check(LiveAngles.captureBinder().transact(15,p,r,0)){"System-bar capture unavailable"};r.readException()
    val b=r.readBundle(Bitmap::class.java.classLoader)?:error("No bar bundle")
    error=b.getString("error")?:error
    if(b.getBoolean("ok")){
     unchanged=b.getBoolean("unchanged");signature=b.getString("signature")?:"";evidence=b.getString("evidence")?:""
     val tiles=b.getParcelableArrayList("tiles",Bundle::class.java).orEmpty().map{tile->Tile(tile.getString("kind")?:"bar",tile.getIntArray("bounds")!!,tile.getParcelable("bitmap",Bitmap::class.java),b.getLong("stamp"))}
     snapshot=Snapshot(b.getString("physical")?:"",b.getInt("display"),b.getInt("width"),b.getInt("height"),b.getInt("rotation"),b.getLong("stamp"),tiles)
    }
   }catch(e:Exception){error=e.message?:e.javaClass.simpleName}finally{p.recycle();r.recycle()}
   val result=snapshot;val message=error;val finishedAt=SystemClock.elapsedRealtime()
   main.post{
    workerBusy=false
    diagnostic("delivery","DELIVERY physical=$physical requestedAt=$now workerQueueMs=${workerAt-now} captureMs=${finishedAt-workerAt} mainQueueMs=${SystemClock.elapsedRealtime()-finishedAt} result=${if(result!=null)"received" else message}")
    if(closed){result?.tiles?.forEach{it.bitmap?.recycle()};return@post}
    if(!workAllowed||requestEpoch!=workEpoch||mode==2&&!opportunity.accepts(SystemClock.elapsedRealtime())){
     result?.tiles?.forEach{it.bitmap?.recycle()};budget.finish(key,SystemClock.elapsedRealtime(),false);skipped++;diagnostic("discard","DISCARDED physical=$physical workAllowed=$workAllowed requestEpoch=$requestEpoch currentEpoch=$workEpoch discoveryAccepts=${opportunity.accepts(SystemClock.elapsedRealtime())}");return@post
    }
    val host=StandaloneService.instance
    val d=host?.getSystemService(DisplayManager::class.java)?.getDisplay(display)
    val size=d?.let{B8PanelGeometry.size(it)}
    val unlocked=host?.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked==false
    if(result!=null&&SystemBarWorkPolicy.geometry(spec.width,spec.height,spec.rotation,result.width,result.height,result.rotation)&&unlocked&&result.physical==physical&&d?.state==Display.STATE_ON&&PanelIdentity.identity(d)==physical&&d.rotation==result.rotation&&size?.x==result.width&&size.y==result.height){
     budget.finish(key,SystemClock.elapsedRealtime(),true)
     if(unchanged){metadataChecks++}else{
      if(mode!=1){pixelCaptures++;pixelRefresh.pixels(physical,signature)}
      publish(result)
     }
     val kinds=captured[physical]?.tiles.orEmpty().flatMap{it.kind.split('+')}
     if(mode==2&&SystemBarWorkPolicy.complete(minOf(spec.width,spec.height).toFloat()/maxOf(spec.width,spec.height)>.7f,"status" in kinds,"navigation" in kinds,"taskbar" in kinds))opportunity.complete()
     note("${if(mode==1)"NATIVE_GEOMETRY" else if(unchanged)"UNCHANGED_NO_SCREENSHOT" else "DESTINATION_CAPTURE"} D$display ${result.width}x${result.height} physical=$physical kinds=${result.tiles.joinToString{it.kind}}; stamp=${result.stamp}; $evidence")
    }else{
     result?.tiles?.forEach{it.bitmap?.recycle()};budget.finish(key,SystemClock.elapsedRealtime(),false)
     if(mode==1){captured.remove(physical);revision++;changed()}
     note("UNAVAILABLE D$display physical=$physical attempt=${budget.count(key)} reason=${if(result==null)message else "mapping/lock changed before delivery"}")
    }
   }
  }
 }
 private fun matches(s:Snapshot?,physical:String,w:Int,h:Int,rotation:Int)=s?.takeIf{SystemBarExperimentPolicy.matches(physical,w,h,rotation,it.physical,it.width,it.height,it.rotation)}
 /** Cosmetic input only: never replaces the original app-readiness frame. Bake
  * at no more than the session's output resolution off-thread; every later frame uses the same texture
  * and blur pyramid through the normal animation, motion and glass renderer. */
 fun effectFrame(frame:GlassFrame,physical:String,w:Int,h:Int,rotation:Int):GlassFrame{
  if(closed||mode!=2||physical==source||!SystemBarExperimentPolicy.matches(physical,w,h,rotation,frame.physicalId,frame.width,frame.height,frame.rotation))return frame
  val snapshot=matches(captured[physical],physical,w,h,rotation)?:run{drawing[physical]="physical=$physical effect bars=MISSING; waiting for destination windows";return frame}
  if(snapshot.tiles.all{it.stamp==frame.stamp}){drawing[physical]="physical=$physical bars already in native app capture; normal effects";return frame}
  if(bakedBase===frame&&bakedSnapshot===snapshot){if(reportedUse!==snapshot||reportedUseBase!==frame){reportedUse=snapshot;reportedUseBase=frame;diagnostic("used-$physical","EFFECT_TEXTURE_USED physical=$physical barStamp=${snapshot.stamp} baseStamp=${frame.stamp} revision=$revision; draw input, not optical proof")};return baked?:frame}
  if(workAllowed&&!compositionBusy&&!(failedBase===frame&&failedSnapshot===snapshot)){
   compositionBusy=true
   val composeEpoch=workEpoch
   val requested=SystemClock.elapsedRealtime()
   diagnostic("compose-$physical","EFFECT_COMPOSE_REQUEST physical=$physical requestedAt=$requested barStamp=${snapshot.stamp} baseStamp=${frame.stamp}")
   val maxSide=panels[physical]?.maxSide?:maxOf(frame.bitmap.width,frame.bitmap.height)
   compositionWorker.execute{
    val start=SystemClock.elapsedRealtime();var output:Bitmap?=null;val levels=ArrayList<Bitmap>();var result:GlassFrame?=null;var failure=""
    try{
     val scale=minOf(1f,maxSide.toFloat()/maxOf(frame.bitmap.width,frame.bitmap.height))
     val image=Bitmap.createBitmap(maxOf(1,(frame.bitmap.width*scale).toInt()),maxOf(1,(frame.bitmap.height*scale).toInt()),Bitmap.Config.ARGB_8888);output=image
     val canvas=Canvas(image);val filter=Paint(Paint.FILTER_BITMAP_FLAG)
     canvas.drawBitmap(frame.bitmap,null,Rect(0,0,image.width,image.height),filter)
     canvas.scale(image.width.toFloat()/w,image.height.toFloat()/h)
     for(tile in snapshot.tiles){val b=tile.bounds;tile.bitmap?.let{canvas.drawBitmap(it,tile.sourceBounds,Rect(b[0],b[1],b[2],b[3]),filter)}}
     var level=image
     repeat(7){level=Bitmap.createScaledBitmap(level,maxOf(1,level.width/2),maxOf(1,level.height/2),true);levels.add(level)}
     result=frame.copy(bitmap=image,levels=levels.toList())
    }catch(e:Exception){failure=e.javaClass.simpleName}catch(e:OutOfMemoryError){failure="optional image allocation failed"}
    val ready=result;val elapsed=SystemClock.elapsedRealtime()-start;val reason=failure
    main.post{
     compositionBusy=false
     diagnostic("compose-result-$physical","EFFECT_COMPOSE_RESULT physical=$physical requestedAt=$requested queueMs=${start-requested} composeMs=$elapsed deliveryAt=${SystemClock.elapsedRealtime()} current=${!closed&&workAllowed&&composeEpoch==workEpoch&&captured[physical]===snapshot} failure=$reason")
     if(closed||!workAllowed||composeEpoch!=workEpoch||captured[physical]!==snapshot||ready==null){
      (levels+listOfNotNull(output)).distinct().forEach{if(!it.isRecycled)it.recycle()}
      skipped++
      if(!closed&&ready==null){failedBase=frame;failedSnapshot=snapshot;note("EFFECT_COMPOSITION_FAILED $reason")}
     }else{
      bakedBase=frame;bakedSnapshot=snapshot;baked=ready;compositions++;compositionMs=elapsed;revision++
      drawing[physical]="physical=$physical ${snapshot.tiles.joinToString{it.kind}}=DESTINATION_EFFECT_TEXTURE; capture raster=${ready.bitmap.width}x${ready.bitmap.height}; visual only, not interactive"
     }
     if(!closed)changed()
    }
   }
  }
  return if(bakedBase===frame)baked?:frame else frame
 }
 fun nativeCrop(physical:String,w:Int,h:Int,rotation:Int):Rect?{
  if(closed||mode!=1)return null
  val host=StandaloneService.instance?:return null
  val d=host.getSystemService(DisplayManager::class.java).getDisplay(0)
  val size=d?.let{B8PanelGeometry.size(it)}
  val s=matches(captured[physical],physical,w,h,rotation)
  if(host.getSystemService(KeyguardManager::class.java).isKeyguardLocked||d?.state!=Display.STATE_ON||PanelIdentity.identity(d)!=physical||d.rotation!=rotation||size?.x!=w||size.y!=h||s==null||!SystemBarExperimentPolicy.fresh(SystemClock.elapsedRealtime(),s.stamp,1000)){
   drawing[physical]="physical=$physical native edges=COVERED; no fresh primary geometry";return null
  }
  val b=SystemBarExperimentPolicy.contentCrop(w,h,s.tiles.map{it.bounds}.toTypedArray())
  drawing[physical]="physical=$physical native content crop=${b.joinToString()}; no copied bar pixels; window state is not visible-pixel proof"
  return Rect(b[0],b[1],b[2],b[3])
 }
 fun draw(canvas:Canvas,physical:String,w:Int,h:Int,rotation:Int){
  if(closed||mode!=3||physical==source)return
  val now=SystemClock.elapsedRealtime()
  val current=matches(captured[physical],physical,w,h,rotation)
  val remembered=if(mode==3)matches(cache.get(physical,now),physical,w,h,rotation)?.takeIf{SystemBarExperimentPolicy.fresh(now,it.stamp,300_000)} else null
  val freshBounds=current?.tiles.orEmpty().map{it.bounds}.toTypedArray()
  val retained=remembered?.tiles.orEmpty().filter{SystemBarExperimentPolicy.keepCached(it.bounds,w,h,it.stamp,now,freshBounds)}
  val tiles=retained+current?.tiles.orEmpty()
  if(tiles.isEmpty()){drawing[physical]="physical=$physical bars=MISSING; no matching screen image";return}
  val save=canvas.save()
  try{canvas.clipRect(0,0,w,h);for(tile in tiles){val b=tile.bounds;tile.bitmap?.let{canvas.drawBitmap(it,null,Rect(b[0],b[1],b[2],b[3]),paint)}}}
  finally{canvas.restoreToCount(save)}
  drawing[physical]="physical=$physical "+tiles.joinToString{tile->"${tile.kind}=${if(tile in retained)"CACHED_PANEL" else "CURRENT_PANEL"}(ageMs=${now-tile.stamp})"}+"; visual only, not interactive"
 }
 fun close(){closed=true;budget.close();captured.clear();drawing.clear();bakedBase=null;bakedSnapshot=null;baked=null;failedBase=null;failedSnapshot=null;reportedUse=null;reportedUseBase=null}
}
