package org.duofold.live

import android.content.Context
import android.graphics.Bitmap
import android.os.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Armed Home/app physical sequence. No handoff acknowledgements or renderer writes.
 * One frame in flight, bounded pixels/disk/time, no catch-up queue. */
internal object FoldImageDiagnostic {
 private val main=Handler(Looper.getMainLooper())
 private val worker=Executors.newSingleThreadExecutor{job->Thread(job,"Duo-diagnostic").apply{priority=Thread.MIN_PRIORITY}}
 @Volatile private var generation=0
 @Volatile private var discardBefore=0
 private var current:Episode?=null
 private var busy=false
 private var status="Fold image sequence: idle"
 private data class Observation(val physical:String,val width:Int,val height:Int,val stage:String="PREPARING",val source:GlassFrame?=null,val details:String="",val at:Long=0)
 private class Episode(val context:Context,val token:Long,val epoch:Int,val began:Long) {
  val budget=FoldImageBudget();val observations=linkedMapOf<String,Observation>();var ended=0L;var reason="";var next=0L
  var sampled=0;var recorded=0;var lastSample=0L;var maxGap=0L;val sources=HashSet<String>();var skippedBusy=0
  val rows=JSONArray();val darkness=DarkFrameEvidence();var nearBlack=0;var flashCandidates=0
  val dir=File(CompositionDiagnostic.folder(context),"$token-fold-sequence")
 }
 private val tick=object:Runnable{override fun run(){sample();if(current!=null)main.postDelayed(this,FoldImageBudget.PERIOD)}}
 fun report()=status
 fun observing()=current!=null
 fun active(token:Long)=current?.let{it.token==token&&it.ended==0L}==true
 fun begin(c:Context,token:Long){
  stop("superseded");val e=Episode(c.applicationContext,token,generation,SystemClock.elapsedRealtime());current=e
  status="Fold image sequence: armed session=$token; target 8 total samples/s alternating panels, <=320px; cleanup tail 10s at 2 total samples/s; gaps are recorded"
  main.post(tick)
 }
 fun observe(token:Long,physical:String,width:Int,height:Int,stage:String,frame:GlassFrame?,details:String){
  val e=current?:return
  if(e.token!=token||!PanelIdentityPolicy.known(physical)||e.ended>0)return
  e.observations[physical]=Observation(physical,width,height,stage,frame,"panel=${if(FoldImageBudget.inner(width,height))"inner" else "cover"} width=$width height=$height; $details",SystemClock.elapsedRealtime())
 }
 fun end(token:Long,reason:String){
  val e=current?:return;if(e.token!=token||e.ended>0)return
  e.ended=SystemClock.elapsedRealtime();e.reason=reason
  e.observations.replaceAll{_,o->o.copy(stage="AFTER_CLEANUP",source=null,details=o.details+"; last renderer observation before release; $reason",at=e.ended)}
 }
 fun stop(reason:String,discard:Boolean=false){
  generation++;if(discard)discardBefore=generation;main.removeCallbacks(tick)
  current?.let{e->status="Fold image sequence: session=${e.token} stopped: $reason";worker.execute{finish(e,reason)}}
  current=null
 }
 private fun finish(e:Episode,reason:String){
  // Already queued images remain evidence for their own session; stop prevents new captures.
  runCatching{synchronized(CompositionDiagnostic.fileLock){if(e.epoch>=discardBefore){e.dir.mkdirs();writeIndex(e,reason)}}}.onFailure{RecoveryLog.add("FOLD_IMAGE_SEQUENCE SAVE_FAILED session=${e.token} ${it.message}")}
 }
 private fun sample(){
  val e=current?:return;val now=SystemClock.elapsedRealtime()
  if(now-e.began>=FoldImageBudget.MAX_DURATION||e.ended>0&&now-e.ended>=FoldImageBudget.TAIL){
   current=null;worker.execute{finish(e,e.reason.ifEmpty{"time limit"})};return
  }
  val tail=e.ended>0
  if(now<e.next||!e.budget.available(tail))return
  if(busy){e.skippedBusy++;e.next=now+if(tail)FoldImageBudget.TAIL_PERIOD else FoldImageBudget.PERIOD;return}
  if(e.observations.isEmpty())return
  val observed=e.observations.values.elementAt(e.sampled%e.observations.size)
  e.budget.attempt(tail);busy=true;e.next=now+if(tail)FoldImageBudget.TAIL_PERIOD else FoldImageBudget.PERIOD
  val raw=LiveAngles.currentAngle();val sequence=e.sampled++
  val correlation=FoldDiagnostics.scene();val base=LiveAngles.freshPhysicalBase()
  val pm=e.context.getSystemService(PowerManager::class.java)
  val skip=if(pm.currentThermalStatus>=2)"thermal >=2; diagnostic load suppressed" else if(!pm.isInteractive)"not interactive; no wake requested" else ""
  worker.execute{
   var image:Bitmap?=null;var sourceImage:Bitmap?=null;var launcherImage:Bitmap?=null
   val row=JSONObject().put("index",sequence).put("requested",now).put("angle",if(raw.isFinite())raw.toDouble() else JSONObject.NULL)
    .put("stage",observed.stage).put("observationAt",observed.at).put("details",observed.details).put("physical",observed.physical).put("baseState",base).put("correlation",correlation)
   try{
    check(e.epoch==generation){"superseded/rearmed"}
    check(skip.isEmpty()){skip}
    val p=Parcel.obtain();val r=Parcel.obtain()
    try{
     p.writeInterfaceToken(GlassCapture.TOKEN);p.writeString(observed.physical)
     check(LiveAngles.captureBinder().transact(16,p,r,0)){"Physical sequence capture unavailable"};r.readException()
     val b=r.readBundle(Bitmap::class.java.classLoader)?:error("No capture result")
     image=b.getParcelable("bitmap",Bitmap::class.java)
     check(b.getBoolean("ok")&&image!=null){b.getString("error")?:"No physical pixels"}
     row.put("stamp",b.getLong("stamp")).put("captureCompleted",b.getLong("completed")).put("display",b.getInt("display"))
      .put("width",b.getInt("width")).put("height",b.getInt("height")).put("rotation",b.getInt("rotation"))
      .put("window",b.getString("provenance")).put("launcher",b.getString("focusedLauncher"))
     PhysicalCaptureSize.requireBounded(image!!.width,image!!.height)
     PhysicalCaptureSize.requirePanelExtent(observed.width,observed.height,b.getInt("width"),b.getInt("height"))
    }finally{p.recycle();r.recycle()}
    check(e.epoch==generation){"superseded/rearmed"}
    val pixels=IntArray(image!!.width*image!!.height);image!!.getPixels(pixels,0,image!!.width,0,0,image!!.width,image!!.height)
    val sample=DarkFrameEvidence.measure(pixels,image!!.width,image!!.height)
    val candidate=e.darkness.observe(observed.physical,"${row.optInt("width")}x${row.optInt("height")}r${row.optInt("rotation")}",row.getLong("stamp"),sample)
    if(sample.nearBlack())e.nearBlack++
    if(candidate.startsWith("BRIGHT_DARK_BRIGHT"))e.flashCandidates++
    row.put("darkness",sample.toString()).put("blackFrameCheck",candidate+"; heuristic only; masks, dark apps and omitted layers can explain black captures")
    if(sample.nearBlack()||candidate.startsWith("BRIGHT_DARK_BRIGHT"))FoldDiagnostics.event("BLACK_FRAME_CHECK","session=${e.token} physical=${observed.physical} stamp=${row.getLong("stamp")} $candidate $sample; capture evidence only")
    row.put("result","RECORDED")
   }catch(failure:Exception){image?.recycle();image=null;row.put("result","UNAVAILABLE: ${failure.message}")}
   try{
    synchronized(CompositionDiagnostic.fileLock){if(e.epoch==generation){
     e.dir.mkdirs()
     val gap=if(e.lastSample>0)now-e.lastSample else 0;e.maxGap=maxOf(e.maxGap,gap);e.lastSample=now;row.put("sampleGapMs",gap)
     val frame=observed.source
     if(frame!=null&&image!=null){
      val key="${frame.physicalId.replace(':','-')}-${frame.stamp}"
      row.put("sourceStamp",frame.stamp).put("sourcePhysical",frame.physicalId).put("sourceWidth",frame.width).put("sourceHeight",frame.height)
      if(key !in e.sources){
       val scale=minOf(1f,320f/maxOf(frame.bitmap.width,frame.bitmap.height))
       sourceImage=Bitmap.createScaledBitmap(frame.bitmap,(frame.bitmap.width*scale).toInt().coerceAtLeast(1),(frame.bitmap.height*scale).toInt().coerceAtLeast(1),true).let{if(it===frame.bitmap)it.copy(Bitmap.Config.ARGB_8888,false)?:error("Source unreadable") else it}
       val sourceName="source-$key.png"
       val sourceFile=File(e.dir,sourceName)
       sourceFile.outputStream().use{check(sourceImage!!.compress(Bitmap.CompressFormat.PNG,100,it))}
       e.budget.saved(tail,sourceFile.length())
       frame.homeLayer?.let{launcher->
        val file=File(e.dir,"launcher-$key.png")
        val factor=minOf(1f,320f/maxOf(launcher.width,launcher.height))
        launcherImage=Bitmap.createScaledBitmap(launcher,maxOf(1,(launcher.width*factor).toInt()),maxOf(1,(launcher.height*factor).toInt()),true).let{if(it===launcher)it.copy(Bitmap.Config.ARGB_8888,false) else it}
        file.outputStream().use{check(launcherImage!!.compress(Bitmap.CompressFormat.PNG,100,it))}
        e.budget.saved(tail,file.length())
       }
       e.sources.add(key)
      }
      row.put("sourceFile","source-$key.png")
      if(frame.homeLayer!=null)row.put("launcherFile","launcher-$key.png").put("launcherEvidence",frame.homeContent.toString())
     }
     image?.let{
      val name="frame-%03d.png".format(sequence);val file=File(e.dir,name)
      file.outputStream().use{out->check(it.compress(Bitmap.CompressFormat.PNG,100,out))}
      e.budget.saved(tail,file.length());e.recorded++;row.put("file",name)
     }
     row.put("finished",SystemClock.elapsedRealtime());e.rows.put(row);writeIndex(e,"recording");FoldDiagnostics.event("IMAGE", "session=${e.token} sample=$sequence physical=${observed.physical} requested=$now result=${row.optString("result")} captureMs=${if(row.has("captureCompleted"))row.getLong("captureCompleted")-now else "unmeasured"}")
     CompositionRetention.expired(CompositionDiagnostic.folder(e.context).listFiles()?:emptyArray(),24).forEach{it.deleteRecursively()}
    }}
   }catch(failure:Exception){RecoveryLog.add("FOLD_IMAGE_SEQUENCE SAVE_FAILED session=${e.token} ${failure.message}")}
   finally{image?.recycle();sourceImage?.recycle();launcherImage?.recycle();main.post{
    busy=false
    if(e.epoch==generation){status="Fold image sequence: session=${e.token} recorded=${e.recorded}/${e.sampled} maxSampleGapMs=${e.maxGap}; nearBlack=${e.nearBlack} flashCandidates=${e.flashCandidates}; skippedBusy=${e.skippedBusy}; alternating physical panels; capture evidence, not optical proof"
     if(current==null)RecoveryLog.add(status)}
   }}
  }
 }
 private fun writeIndex(e:Episode,state:String){
  val meta=JSONObject().put("session",e.token).put("build",BuildConfig.VERSION_NAME).put("state",state).put("started",e.began)
   .put("cleanupObserved",e.ended).put("recorded",e.recorded).put("attempted",e.sampled).put("maxSampleGapMs",e.maxGap).put("skippedBusy",e.skippedBusy).put("nearBlackCaptures",e.nearBlack).put("flashCandidates",e.flashCandidates).put("frames",e.rows)
  File(e.dir,"frames.json").writeText(meta.toString(2))
  File(e.dir,"frames.js").writeText("window.evidence="+meta.toString()+";")
  File(e.dir,"evidence.txt").writeText("build=${BuildConfig.VERSION_NAME}\nFOLD IMAGE SEQUENCE session=${e.token} state=$state\nrecorded=${e.recorded} attempted=${e.sampled} maxSampleGapMs=${e.maxGap}\ncleanupObserved=${e.ended} reason=${e.reason}\nPhysical compositor only, <=320px, target 8 total samples/s alternating physical panels (about 4 per panel), 2 total samples/s for 10s after cleanup; missed intervals and OFF/unstable mapping are not passes. Frame times are capture-request intervals, not optical timing. Composited source thumbnails, isolated launcher alpha thumbnails when available, and renderer observations are sequential aids, not synchronized pixel comparisons. Alpha counts do not prove all icons are present. No additional window dumps on the capture path. Native readiness/window observations and system-bar timing are in the root fold-timeline.tsv and runtime-state.txt. No wake requests. Thermal >=2 suppresses images. Busy intervals are counted; no catch-up queue. Top/bottom edge views are crops of the same frame, not extra captures. Open review.html after extracting the ZIP.\n")
  if(!File(e.dir,"review.html").exists())File(e.dir,"review.html").writeText(VIEWER)
 }
 private val VIEWER="""<!doctype html><meta charset="utf-8"><title>Fold image sequence</title>
<style>body{background:#111;color:#eee;font:16px system-ui;margin:24px}button{padding:10px;margin:5px}img{max-width:30vw;max-height:65vh;object-fit:contain;background:#222}pre{white-space:pre-wrap}input{width:90%}</style>
<h1>Home / app physical panels</h1><p>Left: physical compositor. Middle: retained app/Home source. Right, when available: isolated launcher on a checkerboard. Source images are separate, earlier captures. Missing frames remain gaps; this is not a camera recording or synchronized pixel comparison. Alpha counts are a heuristic, not proof every icon is present.</p>
<style>#launcher{background:repeating-conic-gradient(#444 0% 25%,#888 0% 50%) 0/16px 16px}</style>
<button id="prev">Previous</button><button id="play">Play</button><button id="next">Next</button><input id="slider" type="range" min="0" value="0"><p id="label"></p>
<img id="panel" alt="No physical frame for this sample"><img id="source" alt="Retained source unavailable"><img id="launcher" alt="Isolated launcher unavailable"><p>Top / bottom edges of this same captured frame (status bar / taskbar may be missing or omitted by capture):</p><canvas id="top"></canvas> <canvas id="bottom"></canvas><pre id="info"></pre><script src="frames.js"></script><script>
const rows=window.evidence.frames,slider=document.getElementById('slider');let index=0,timer=null;
slider.max=Math.max(0,rows.length-1);function show(n){index=Math.max(0,Math.min(rows.length-1,n));slider.value=index;const r=rows[index];if(!r)return;
document.getElementById('label').textContent=(index+1)+' / '+rows.length+' | '+r.physical+' | '+r.stage+' | '+r.angle+'° | '+r.result;
const selected=r.file;const p=document.getElementById('panel');p.onload=()=>{if(rows[index]?.file!==selected)return;for(const [id,lower] of [['top',false],['bottom',true]]){const c=document.getElementById(id),h=Math.max(1,Math.round(p.naturalHeight*.2));c.width=p.naturalWidth;c.height=h;c.style.width='40vw';c.getContext('2d').drawImage(p,0,lower?p.naturalHeight-h:0,p.naturalWidth,h,0,0,c.width,h)}};for(const id of ['top','bottom']){const c=document.getElementById(id);c.getContext('2d').clearRect(0,0,c.width,c.height)}for(const [id,key] of [['panel','file'],['source','sourceFile'],['launcher','launcherFile']]){const im=document.getElementById(id);if(r[key]){im.src=r[key];im.style.display='inline'}else{im.removeAttribute('src');im.style.display='none'}}document.getElementById('info').textContent=JSON.stringify(r,null,2)}
function stop(){clearTimeout(timer);timer=null;document.getElementById('play').textContent='Play'}
function step(){if(index>=rows.length-1){stop();return}const delay=Math.max(1,rows[index+1].requested-rows[index].requested);timer=setTimeout(()=>{show(index+1);step()},delay)}
document.getElementById('prev').onclick=()=>{stop();show(index-1)};document.getElementById('next').onclick=()=>{stop();show(index+1)};slider.oninput=()=>{stop();show(+slider.value)};
document.getElementById('play').onclick=()=>{if(timer){stop();return}if(index>=rows.length-1)show(0);document.getElementById('play').textContent='Pause';step()};show(0);
</script>"""
}
