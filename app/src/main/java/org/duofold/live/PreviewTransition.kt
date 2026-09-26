package org.duofold.live

import android.graphics.*
import android.os.*
import android.view.SurfaceControl
import android.view.View
import java.util.concurrent.Executors

/** Only the experimental normal-mode preview uses this preloaded transition. */
internal object PreviewTransition {
 private val executor=Executors.newSingleThreadExecutor()
 private val main=Handler(Looper.getMainLooper())
 private var remote:IBinder?=null
 @Volatile private var blurStrength=.3f
 @Volatile private var seamOffset=.07f
 @Volatile private var frostedReflection=false
 @Volatile private var rightPreviewReady=false
 fun previewReady(ready:Boolean){rightPreviewReady=ready}
 fun configure(context:android.content.Context){val p=context.getSharedPreferences("standalone",0);frostedReflection=p.getBoolean("frosted_reflection",false);blurStrength=RenderQuality.blur(p.getFloat("blur_strength",.3f));seamOffset=RenderQuality.seam(p.getFloat("seam_offset",.07f))}
 private var pending=false
 private var lastStamp=0L
 private var wasCover=false
 private var active=false
 private var readySent=false
 private var serial=0
 var status="Preview transition idle";private set
 // Main-thread registration; failed startup attempts remain pending until the helper is ready.
 private val exclusions=java.util.WeakHashMap<SurfaceControl,Boolean>()
 private val exclusionExecutor=Executors.newSingleThreadExecutor()
 private val exclusionListeners=LinkedHashSet<Runnable>()
 fun listenForExclusions(listener:Runnable){exclusionListeners.add(listener)}
 fun stopListeningForExclusions(listener:Runnable){exclusionListeners.remove(listener)}
 private fun exclusionsChanged(){exclusionListeners.toList().forEach{it.run()}}
 var exclusionRevision=0;private set
 fun exclusionsReady():Boolean{
  exclusions.keys.removeAll{!it.isValid}
  return exclusions.isNotEmpty() && exclusions.values.all{it}
 }
 fun forgetAnimation(surface:SurfaceControl){exclusions.remove(surface)}
 fun markAnimation(view:View){
  view.post(object:Runnable{override fun run(){
   if(!view.isAttachedToWindow)return
   try{
    val root=View::class.java.getDeclaredMethod("getViewRootImpl").invoke(view) ?: throw IllegalStateException("Overlay root not ready")
    val surface=root.javaClass.getMethod("getSurfaceControl").invoke(root) as SurfaceControl
    if(!surface.isValid)throw IllegalStateException("Overlay surface not ready")
    markAnimation(surface)
   }catch(e:Exception){view.postDelayed(this,500)}
  }})
 }
 fun markAnimation(surface:SurfaceControl){
  if(!surface.isValid || exclusions.containsKey(surface))return
  exclusions[surface]=false
  attemptExclusion(surface,0)
 }
 private fun attemptExclusion(surface:SurfaceControl,attempt:Int){
  if(!surface.isValid || !exclusions.containsKey(surface)){exclusions.remove(surface);return}
  exclusionExecutor.execute{
   val p=Parcel.obtain();val r=Parcel.obtain();var failure:String?=null
   try{p.writeInterfaceToken(AngleReader.DESCRIPTOR);p.writeTypedObject(surface,0);LiveAngles.previewCommand(7,p,r);r.readException()}
   catch(e:Exception){failure=e.message ?: e.javaClass.simpleName}finally{p.recycle();r.recycle()}
   val error=failure
   main.post{
    if(!surface.isValid || !exclusions.containsKey(surface)){exclusions.remove(surface);return@post}
    if(error==null){exclusions[surface]=true;exclusionRevision++;exclusionsChanged();status="Animation excluded from mirror; startup retry count=$attempt";RecoveryLog.add(status)}
    else{
     if(attempt==0){status="Waiting to exclude animation: $error";RecoveryLog.add(status)}
     main.postDelayed({attemptExclusion(surface,attempt+1)},500)
    }
   }
  }
 }
 private fun binder():IBinder{
  remote?.takeIf{it.isBinderAlive}?.let{return it}
  val p=Parcel.obtain();val r=Parcel.obtain()
  try{p.writeInterfaceToken(AngleReader.DESCRIPTOR);LiveAngles.previewCommand(8,p,r);r.readException();return r.readStrongBinder()!!.also{remote=it}}finally{p.recycle();r.recycle()}
 }
 private fun send(code:Int,frame:GlassFrame?=null,endpoint:Boolean=false):String{
  val p=Parcel.obtain();val r=Parcel.obtain();var blurred:Bitmap?=null
  try{
   p.writeInterfaceToken(PreviewExpansion.TOKEN)
   if(code==2)p.writeInt(if(endpoint)1 else 0)
   if(frame!=null){blurred=frost(frame.bitmap);p.writeTypedObject(blurred,0);p.writeTypedObject(frame.bitmap,0);p.writeLong(frame.stamp);p.writeFloat(seamOffset);p.writeInt(if(frostedReflection)1 else 0);p.writeInt(if(rightPreviewReady)1 else 0)}
   binder().transact(code,p,r,0);r.readException();return r.readString()?:"Expansion ready"
  }catch(e:Exception){remote=null;throw e}finally{blurred?.recycle();p.recycle();r.recycle()}
 }
 fun update(enabled:Boolean,primaryInner:Boolean){
  if(!enabled){if(active){serial++;executor.execute{runCatching{send(3)}}};active=false;wasCover=false;readySent=false;return}
  active=true
  val cover=LiveAngles.coverPreview && !primaryInner
  if(cover && !wasCover){lastStamp=0;readySent=false;executor.execute{runCatching{send(3)}}}
  wasCover=cover
  val frame=GlassFrames.frame?:return
  val now=SystemClock.elapsedRealtime()
  val isInner=minOf(frame.width,frame.height).toFloat()/maxOf(frame.width,frame.height)>.7f
  if(!cover || isInner || pending || frame.stamp==lastStamp || !PreviewExpansionPolicy.fresh(frame.stamp,now))return
  lastStamp=frame.stamp;pending=true;val gen=serial
  executor.execute{
   val note=runCatching{send(1,frame)}.getOrElse{"Expansion preparation: ${it.message}"}
   main.post{pending=false;if(gen==serial)status=note}
  }
 }
 fun innerFrameSubmitted(frame:GlassFrame?){
  if(!active || wasCover || readySent || frame==null)return
  if(minOf(frame.width,frame.height).toFloat()/maxOf(frame.width,frame.height)<=.7f ||
     !PreviewExpansionPolicy.fresh(frame.stamp,SystemClock.elapsedRealtime()))return
  readySent=true
  executor.execute{runCatching{send(2)}.onSuccess{note->main.post{status=note}}.onFailure{main.post{readySent=false}}}
 }
 fun innerEndpointCommitted(){
  if(!active || wasCover || readySent)return
  readySent=true
  executor.execute{runCatching{send(2,endpoint=true)}.onSuccess{note->main.post{status=note}}.onFailure{main.post{readySent=false}}}
 }
 // Small separable box blur, prepared off the render and angle-reader threads.
 private fun frost(source:Bitmap):Bitmap{
  val scale=minOf(1f,320f/maxOf(source.width,source.height))
  val w=maxOf(1,(source.width*scale).toInt());val h=maxOf(1,(source.height*scale).toInt())
  val reduced=Bitmap.createScaledBitmap(source,w,h,true)
  val pixels=IntArray(w*h);reduced.getPixels(pixels,0,w,0,0,w,h)
  if(reduced!==source)reduced.recycle()
  val out=IntArray(pixels.size);val radius=(5*blurStrength).toInt().coerceIn(0,15)
  for(pass in 0..1){
   val rows=if(pass==0)h else w;val length=if(pass==0)w else h
   fun index(row:Int,col:Int)=if(pass==0)row*w+col else col*w+row
   for(row in 0 until rows){
    var rr=0;var gg=0;var bb=0
    for(k in -radius..radius){val c=pixels[index(row,k.coerceIn(0,length-1))];rr+=Color.red(c);gg+=Color.green(c);bb+=Color.blue(c)}
    for(col in 0 until length){
     val n=radius*2+1;out[index(row,col)]=Color.rgb(rr/n,gg/n,bb/n)
     val a=pixels[index(row,(col-radius).coerceIn(0,length-1))];val b=pixels[index(row,(col+radius+1).coerceIn(0,length-1))]
     rr+=Color.red(b)-Color.red(a);gg+=Color.green(b)-Color.green(a);bb+=Color.blue(b)-Color.blue(a)
    }
   }
   out.copyInto(pixels)
  }
  val result=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
  // Preserve content brightness: no white tint or flash at handoff.
  return result
 }
}
