package org.duofold.live

import android.content.Context
import android.graphics.Bitmap
import android.os.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Armed Home-only physical sequence. No handoff acknowledgements or renderer writes.
 * One frame in flight, bounded pixels/disk/time, no catch-up queue. */
internal object HomeInnerDiagnostic {
 private val main=Handler(Looper.getMainLooper())
 private val worker=Executors.newSingleThreadExecutor()
 @Volatile private var generation=0
 private var current:Episode?=null
 private var busy=false
 private var status="Home inner sequence: idle"
 private data class Observation(val physical:String="",val stage:String="PREPARING",val source:GlassFrame?=null,val details:String="",val at:Long=0)
 private class Episode(val context:Context,val token:Long,val epoch:Int,val began:Long) {
  val budget=HomeInnerBudget();var observation=Observation();var ended=0L;var reason="";var next=0L
  var lastWindow=0L;var sampled=0;var recorded=0;var lastSample=0L;var maxGap=0L;var lastSource=""
  val rows=JSONArray()
  val dir=File(CompositionDiagnostic.folder(context),"$token-home-inner-sequence")
 }
 private val tick=object:Runnable{override fun run(){sample();if(current!=null)main.postDelayed(this,HomeInnerBudget.PERIOD)}}
 fun report()=status
 fun begin(c:Context,token:Long){
  stop("superseded");val e=Episode(c.applicationContext,token,generation,SystemClock.elapsedRealtime());current=e
  status="Home inner sequence: armed session=$token; target 8 samples/s, <=320px; through cleanup +1s; gaps are recorded"
  main.post(tick)
 }
 fun observe(token:Long,physical:String,width:Int,height:Int,stage:String,frame:GlassFrame?,details:String){
  val e=current?:return
  if(e.token!=token||!HomeInnerBudget.inner(width,height)||e.ended>0)return
  e.observation=Observation(physical,stage,frame,details,SystemClock.elapsedRealtime())
 }
 fun end(token:Long,reason:String){
  val e=current?:return;if(e.token!=token||e.ended>0)return
  e.ended=SystemClock.elapsedRealtime();e.reason=reason
  e.observation=e.observation.copy(stage="AFTER_CLEANUP",details=e.observation.details+"; coverage released; $reason",at=e.ended)
 }
 fun stop(reason:String){
  generation++;main.removeCallbacks(tick)
  current?.let{e->status="Home inner sequence: session=${e.token} stopped: $reason";worker.execute{finish(e,reason)}}
  current=null
 }
 private fun finish(e:Episode,reason:String){
  // Already queued images remain evidence for their own session; stop prevents new captures.
  runCatching{synchronized(CompositionDiagnostic.fileLock){e.dir.mkdirs();writeIndex(e,reason)}}.onFailure{RecoveryLog.add("HOME_INNER_SEQUENCE SAVE_FAILED session=${e.token} ${it.message}")}
 }
 private fun sample(){
  val e=current?:return;val now=SystemClock.elapsedRealtime()
  if(now-e.began>=HomeInnerBudget.MAX_DURATION||e.ended>0&&now-e.ended>=HomeInnerBudget.TAIL){
   current=null;worker.execute{finish(e,e.reason.ifEmpty{"time limit"})};return
  }
  val tail=e.ended>0
  if(busy||now<e.next||!e.budget.available(tail))return
  val observed=e.observation
  if(!PanelIdentityPolicy.known(observed.physical))return
  e.budget.attempt(tail);busy=true;e.next=now+HomeInnerBudget.PERIOD
  val raw=LiveAngles.currentAngle();val sequence=e.sampled++
  val window=now-e.lastWindow>=750;if(window)e.lastWindow=now
  worker.execute{
   var image:Bitmap?=null;var sourceImage:Bitmap?=null
   val row=JSONObject().put("index",sequence).put("requested",now).put("angle",if(raw.isFinite())raw.toDouble() else JSONObject.NULL)
    .put("stage",observed.stage).put("observationAt",observed.at).put("details",observed.details).put("physical",observed.physical)
   try{
    check(e.epoch==generation){"superseded/rearmed"}
    val p=Parcel.obtain();val r=Parcel.obtain()
    try{
     p.writeInterfaceToken(GlassCapture.TOKEN);p.writeString(observed.physical);p.writeInt(if(window)1 else 0)
     check(LiveAngles.captureBinder().transact(8,p,r,0)){"Home inner capture unavailable"};r.readException()
     val b=r.readBundle(Bitmap::class.java.classLoader)?:error("No capture result")
     image=b.getParcelable("bitmap",Bitmap::class.java)
     check(b.getBoolean("ok")&&image!=null){b.getString("error")?:"No inner pixels"}
     row.put("stamp",b.getLong("stamp")).put("captureCompleted",b.getLong("completed")).put("display",b.getInt("display"))
      .put("width",b.getInt("width")).put("height",b.getInt("height")).put("rotation",b.getInt("rotation"))
      .put("window",b.getString("provenance")).put("launcher",b.getString("focusedLauncher"))
     PhysicalCaptureSize.requireBounded(image!!.width,image!!.height)
    }finally{p.recycle();r.recycle()}
    check(e.epoch==generation){"superseded/rearmed"}
    row.put("result","RECORDED")
   }catch(failure:Exception){image?.recycle();image=null;row.put("result","UNAVAILABLE: ${failure.message}")}
   try{
    synchronized(CompositionDiagnostic.fileLock){if(e.epoch==generation){
     e.dir.mkdirs()
     val gap=if(e.lastSample>0)now-e.lastSample else 0;e.maxGap=maxOf(e.maxGap,gap);e.lastSample=now;row.put("sampleGapMs",gap)
     val frame=observed.source
     if(frame!=null){
      val key="${frame.physicalId}:${frame.stamp}"
      row.put("sourceStamp",frame.stamp).put("sourcePhysical",frame.physicalId).put("sourceWidth",frame.width).put("sourceHeight",frame.height)
      if(key!=e.lastSource){
       val scale=minOf(1f,320f/maxOf(frame.bitmap.width,frame.bitmap.height))
       sourceImage=Bitmap.createScaledBitmap(frame.bitmap,(frame.bitmap.width*scale).toInt().coerceAtLeast(1),(frame.bitmap.height*scale).toInt().coerceAtLeast(1),true).let{if(it===frame.bitmap)it.copy(Bitmap.Config.ARGB_8888,false)?:error("Source unreadable") else it}
       val sourceName="source-${frame.stamp}.png"
       val sourceFile=File(e.dir,sourceName)
       sourceFile.outputStream().use{check(sourceImage!!.compress(Bitmap.CompressFormat.PNG,100,it))}
       e.budget.saved(tail,sourceFile.length())
       frame.homeLayer?.let{launcher->
        val file=File(e.dir,"launcher-${frame.stamp}.png")
        file.outputStream().use{check(launcher.compress(Bitmap.CompressFormat.PNG,100,it))}
        e.budget.saved(tail,file.length())
       }
       e.lastSource=key
      }
      row.put("sourceFile","source-${frame.stamp}.png")
      if(frame.homeLayer!=null)row.put("launcherFile","launcher-${frame.stamp}.png").put("launcherEvidence",frame.homeContent.toString())
     }
     image?.let{
      val name="frame-%03d.png".format(sequence);val file=File(e.dir,name)
      file.outputStream().use{out->check(it.compress(Bitmap.CompressFormat.PNG,100,out))}
      e.budget.saved(tail,file.length());e.recorded++;row.put("file",name)
     }
     row.put("finished",SystemClock.elapsedRealtime());e.rows.put(row);writeIndex(e,"recording")
     CompositionRetention.expired(CompositionDiagnostic.folder(e.context).listFiles()?:emptyArray(),24).forEach{it.deleteRecursively()}
    }}
   }catch(failure:Exception){RecoveryLog.add("HOME_INNER_SEQUENCE SAVE_FAILED session=${e.token} ${failure.message}")}
   finally{image?.recycle();sourceImage?.recycle();main.post{
    busy=false
    if(e.epoch==generation){status="Home inner sequence: session=${e.token} recorded=${e.recorded}/${e.sampled} maxSampleGapMs=${e.maxGap}; inner only; capture evidence, not optical proof"
     if(current==null)RecoveryLog.add(status)}
   }}
  }
 }
 private fun writeIndex(e:Episode,state:String){
  val meta=JSONObject().put("session",e.token).put("build",BuildConfig.VERSION_NAME).put("state",state).put("started",e.began)
   .put("cleanupObserved",e.ended).put("recorded",e.recorded).put("attempted",e.sampled).put("maxSampleGapMs",e.maxGap).put("frames",e.rows)
  File(e.dir,"frames.json").writeText(meta.toString(2))
  File(e.dir,"frames.js").writeText("window.evidence="+meta.toString()+";")
  File(e.dir,"evidence.txt").writeText("build=${BuildConfig.VERSION_NAME}\nHOME INNER SEQUENCE session=${e.token} state=$state\nrecorded=${e.recorded} attempted=${e.sampled} maxSampleGapMs=${e.maxGap}\ncleanupObserved=${e.ended} reason=${e.reason}\nPhysical compositor only, <=320px, target 8 samples/s; missed intervals and OFF/unstable mapping are not passes. Frame times are capture-request intervals, not optical timing. Composited source thumbnails, isolated launcher alpha thumbnails when available, and renderer observations are sequential aids, not synchronized pixel comparisons. Alpha counts do not prove all icons are present. Window observations at most every 750ms. Open review.html after extracting the ZIP.\n")
  if(!File(e.dir,"review.html").exists())File(e.dir,"review.html").writeText(VIEWER)
 }
 private val VIEWER="""<!doctype html><meta charset="utf-8"><title>Home inner sequence</title>
<style>body{background:#111;color:#eee;font:16px system-ui;margin:24px}button{padding:10px;margin:5px}img{max-width:30vw;max-height:65vh;object-fit:contain;background:#222}pre{white-space:pre-wrap}input{width:90%}</style>
<h1>Home inner display</h1><p>Left: physical compositor. Middle: retained Home composite. Right, when available: isolated launcher on a checkerboard. Source images are separate, earlier captures. Missing frames remain gaps; this is not a camera recording or synchronized pixel comparison. Alpha counts are a heuristic, not proof every icon is present.</p>
<style>#launcher{background:repeating-conic-gradient(#444 0% 25%,#888 0% 50%) 0/16px 16px}</style>
<button id="prev">Previous</button><button id="play">Play</button><button id="next">Next</button><input id="slider" type="range" min="0" value="0"><p id="label"></p>
<img id="panel" alt="No physical frame for this sample"><img id="source" alt="Composited source unavailable"><img id="launcher" alt="Isolated launcher unavailable"><pre id="info"></pre><script src="frames.js"></script><script>
const rows=window.evidence.frames,slider=document.getElementById('slider');let index=0,timer=null;
slider.max=Math.max(0,rows.length-1);function show(n){index=Math.max(0,Math.min(rows.length-1,n));slider.value=index;const r=rows[index];if(!r)return;
document.getElementById('label').textContent=(index+1)+' / '+rows.length+' | '+r.stage+' | '+r.angle+'° | '+r.result;
for(const [id,key] of [['panel','file'],['source','sourceFile'],['launcher','launcherFile']]){const im=document.getElementById(id);if(r[key]){im.src=r[key];im.style.display='inline'}else{im.removeAttribute('src');im.style.display='none'}}document.getElementById('info').textContent=JSON.stringify(r,null,2)}
function stop(){clearTimeout(timer);timer=null;document.getElementById('play').textContent='Play'}
function step(){if(index>=rows.length-1){stop();return}const delay=Math.max(1,rows[index+1].requested-rows[index].requested);timer=setTimeout(()=>{show(index+1);step()},delay)}
document.getElementById('prev').onclick=()=>{stop();show(index-1)};document.getElementById('next').onclick=()=>{stop();show(index+1)};slider.oninput=()=>{stop();show(+slider.value)};
document.getElementById('play').onclick=()=>{if(timer){stop();return}if(index>=rows.length-1)show(0);document.getElementById('play').textContent='Pause';step()};show(0);
</script>"""
}
