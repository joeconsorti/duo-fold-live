package org.duofold.live
import android.content.Context
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView

/** AGSL adaptation of chuspeeism/iphone-duo's screenColor: 25 weighted taps,
 * 72px progressive radius, projected UI rays and edge darkening. MIT notice bundled. */
internal object DuoGlassShader {
 val source="""
 uniform shader content;
 uniform shader mip1; uniform shader mip2; uniform shader mip3; uniform shader mip4; uniform shader mip5; uniform shader mip6; uniform shader mip7;
 half3 sampleLevel(float2 p,float lod){
  if(lod<1.0)return mix(content.eval(p).rgb,mip1.eval(p).rgb,half(lod));
  if(lod<2.0)return mix(mip1.eval(p).rgb,mip2.eval(p).rgb,half(lod-1.0));
  if(lod<3.0)return mix(mip2.eval(p).rgb,mip3.eval(p).rgb,half(lod-2.0));
  if(lod<4.0)return mix(mip3.eval(p).rgb,mip4.eval(p).rgb,half(lod-3.0));
  if(lod<5.0)return mix(mip4.eval(p).rgb,mip5.eval(p).rgb,half(lod-4.0));
  if(lod<6.0)return mix(mip5.eval(p).rgb,mip6.eval(p).rgb,half(lod-5.0));
  return mix(mip6.eval(p).rgb,mip7.eval(p).rgb,half(clamp(lod-6.0,0.0,1.0)));
 }
 uniform float2 texSize;
 uniform float2 origin;
 uniform float2 extent;
 uniform float2 sampleScale;
 uniform float2 sampleOffset;
 uniform float progress;
 uniform float foldRadians;
 uniform float inner;
 uniform float fallback;
 uniform float horizontal;
 uniform float reverse;
 uniform float intensity;
 half4 main(float2 p) {
  float2 uv=(p-origin)/extent;
  if(any(lessThan(uv,float2(0))) || any(greaterThan(uv,float2(1)))) return half4(0);
  float axis=mix(uv.x,uv.y,horizontal);
  axis=mix(axis,1.0-axis,reverse);
  // Project the folded physical panel through the reference's fixed eye at z=40.
  // Work in canonical hinge coordinates, then map back to screenshot orientation.
  if(inner>0.5 && fallback<0.5 && axis>=0.5) return half4(0);
  float across=mix(uv.y,uv.x,horizontal);
  float a=foldRadians;
  float c=cos(a), sn=sin(a);
  float width=inner>0.5 ? 15.7987 : 7.73936;
  float px=inner>0.5 ? (-7.89935+axis*width) : (-0.23396-axis*width);
  if(inner>0.5 && fallback>0.5) px=(axis-1.0)*7.89935;
  float py=(1.0-across)*11.1035+0.34562-5.8974;
  float pz=inner>0.5 ? 0.24948 : -0.27463;
  if(inner<0.5 || px<0.0){float z=pz-0.275454;float oldX=px;px=c*oldX+sn*z;pz=-sn*oldX+c*z+0.275454;}
  float depth=(0.24948-40.0)/(pz-40.0);
  float projectedX=px*depth;
  float projectedY=py*depth;
  float mapped=(projectedX+7.89935)/15.7987;
  float edge=(mapped-0.5)/(-0.5);
  if(inner>0.5 && fallback>0.5){mapped=1.0+projectedX/7.89935;edge=1.0-mapped;}
  if(inner<0.5){
   float hx=-0.23396, hz=-0.27463-0.275454;
   float foldedX=c*hx+sn*hz;
   float foldedZ=-sn*hx+c*hz+0.275454;
   float anchor=foldedX*(0.24948-40.0)/(foldedZ-40.0);
   float frameWidth=7.73936*(40.0-0.24948)/(40.0-0.825538);
   mapped=(projectedX-anchor)/frameWidth;
   edge=mapped;
  }
  float corrected=mix(mapped,1.0-mapped,reverse);
  float projectedAcross=1.0-(projectedY-(0.34562-5.8974))/11.1035;
  float2 sourceUV=horizontal>0.5 ? float2(projectedAcross,corrected) : float2(corrected,projectedAcross);
  sourceUV=sourceUV*sampleScale+sampleOffset;
  float shaderProgress=inner>0.5 ? clamp(a/1.570796327,0.0,1.0) : clamp((3.141592654-a)/1.570796327,0.0,1.0);
  float motion=smoothstep(0.0,1.0,shaderProgress);
  // Keep the 72 source-pixel reference radius, including the image's black margins.
  float radius=72.0*motion*pow(clamp(edge,0.0,1.0),1.35);
  float2 footprint=max(0.5/texSize,float2(radius)*0.75/texSize);
  half3 color=half3(0);
  if(radius<0.01){
   float2 coverage=smoothstep(-footprint,footprint,sourceUV)*(1.0-smoothstep(1.0-footprint,1.0+footprint,sourceUV));
   color=sampleLevel(clamp(sourceUV,float2(0),float2(1))*texSize,0.0)*half(coverage.x*coverage.y);
  }else{
  for(int y=-2;y<=2;y++) {
   for(int x=-2;x<=2;x++) {
    float wx=x==0?6.0:(abs(float(x))==1.0?4.0:1.0);
    float wy=y==0?6.0:(abs(float(y))==1.0?4.0:1.0);
    float2 sampleUV=sourceUV+float2(float(x),float(y))*radius/texSize;
    float2 coverage=smoothstep(-footprint,footprint,sampleUV)*(1.0-smoothstep(1.0-footprint,1.0+footprint,sampleUV));
    color+=sampleLevel(clamp(sampleUV,float2(0),float2(1))*texSize,log2(max(1.0,radius)))*half(coverage.x*coverage.y*wx*wy/256.0);
   }
  }
  }
  float effect=motion*pow(clamp((edge-0.2)/0.8,0.0,1.0),1.35);
  color*=half(1.0-min(1.0,effect*2.0*intensity));
  float alpha=smoothstep(0.0,0.035,progress);
  return half4(color*half(alpha),half(alpha));
 }
 """.trimIndent()
}
@Composable internal fun DuoGlassSurface(angle:Float,amount:Float,intensity:Float,inner:Boolean,rotation:Int,frozenFrame:GlassFrame?=null){
 val frame=frozenFrame ?: GlassFrames.frame
 val running=frozenFrame==null && amount>.003f && LiveAngles.fresh()
 DisposableEffect(running){if(running)GlassFrames.acquire();onDispose{if(running)GlassFrames.release()}}
 AndroidView(factory={FrostSurface(it)},modifier=Modifier.fillMaxSize(),update={it.configure(frame,amount,intensity,inner,rotation,frozenFrame!=null,angle)})
}
internal class FrostSurface(context:Context,private val preview:Boolean=false):SurfaceView(context),SurfaceHolder.Callback {
 private var hingeAngle=Float.NaN
 private var targetAngle=Float.NaN
 private var renderedAngle=Float.NaN
 private var lastFrameNanos=0L
 private var angleListening=false
 private var openThreshold=172f
 private var smoothingMs=12f
 private var bufferWidth=0;private var bufferHeight=0
 private fun updateBufferSize(){
  if(preview || width<=0 || height<=0)return
  val full=context.getSharedPreferences("standalone",0).getBoolean("full_resolution_glass",false)
  val w=if(full)width else maxOf(1,width/2);val h=if(full)height else maxOf(1,height/2)
  if(w!=bufferWidth || h!=bufferHeight){bufferWidth=w;bufferHeight=h;holder.setFixedSize(w,h)}
 }
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);updateBufferSize()}
 private val angleListener=LiveAngles.Listener { value,_ ->
  if(value.isFinite() && value!=targetAngle){targetAngle=value;requestDraw()}
 }
 private var frozen=false;private var frame:GlassFrame?=null;private var amount=0f;private var intensity=1f;private var inner=false;private var rotation=0
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private var program:RuntimeShader?=null
 private var bitmap:Bitmap?=null
 private var classic=false
 private var readinessGeneration=0
 private var readinessPending=false
 private var lastReadyCapture=-1L
 private var lastReadyEndpoint=-1L
 /** Tie readiness to this SurfaceView's next buffer, not its parent UI draw. */
 private fun trackReadyFrame(rendered:GlassFrame?,endpoint:Boolean){
  if(preview || frozen || !inner || readinessPending)return
  val now=SystemClock.elapsedRealtime()
  if(endpoint){if(now-lastReadyEndpoint<50)return}
  else if(rendered==null || rendered.stamp==lastReadyCapture)return
  val gen=readinessGeneration
  readinessPending=true
  try{
   SurfaceControl.Transaction().use { transaction ->
    transaction.addTransactionCommittedListener(context.mainExecutor){
     if(gen==readinessGeneration && holder.surface.isValid){
      readinessPending=false
      if(endpoint)lastReadyEndpoint=SystemClock.elapsedRealtime() else lastReadyCapture=rendered!!.stamp
      HandoffFadeFrames.committed(true,rendered?.stamp ?: -1,endpoint)
      if(rendered!=null)PreviewTransition.innerFrameSubmitted(rendered) else if(endpoint)PreviewTransition.innerEndpointCommitted()
     }
    }
    applyTransactionToFrame(transaction)
   }
  }catch(e:Exception){readinessPending=false;RecoveryLog.add("Glass frame readiness unavailable: ${e.javaClass.simpleName}")}
 }
 private var frameQueued=false
 private var dirty=true
 private var appliedRate=0f
 private val choreographer=Choreographer.getInstance()
 private val vsync=Choreographer.FrameCallback { now ->
  frameQueued=false
  if(holder.surface.isValid && dirty){
   dirty=false
   if(!preview && targetAngle.isFinite() && LiveAngles.fresh()){
    val dt=if(lastFrameNanos==0L)8.33f else ((now-lastFrameNanos)/1_000_000f).coerceIn(1f,50f)
    renderedAngle=FrameSmoothing.step(renderedAngle,targetAngle,dt,smoothingMs)
    hingeAngle=renderedAngle
    amount=if(inner && targetAngle>=FoldThreshold.sanitize(openThreshold))0f
      else DuoShadeCurve.progress(renderedAngle,inner,openThreshold)
   }
   lastFrameNanos=now
   drawFrame()
   if(!preview && LiveAngles.fresh() && targetAngle.isFinite() && kotlin.math.abs(renderedAngle-targetAngle)>=.01f)requestDraw()
  }
 }
 private fun requestDraw(){dirty=true;if(!frameQueued && holder.surface.isValid){frameQueued=true;choreographer.postFrameCallback(vsync)}}
 private fun preferFastRefresh(){
  val rate=display?.supportedModes?.filter{it.physicalWidth==display?.mode?.physicalWidth && it.physicalHeight==display?.mode?.physicalHeight}?.maxOfOrNull{it.refreshRate}?.coerceAtMost(120f) ?: 60f
  if(rate!=appliedRate && holder.surface.isValid)runCatching{holder.surface.setFrameRate(rate,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);appliedRate=rate}
 }
 init{setZOrderOnTop(true);holder.setFormat(PixelFormat.TRANSLUCENT);holder.addCallback(this);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
  try{classic=context.getSharedPreferences("standalone",0).getString("animation_style","duo")=="classic";program=RuntimeShader(if(classic)ClassicGlassShader.source else DuoGlassShader.source)}catch(e:Exception){RecoveryLog.add("Glass shader compilation failed: ${e.message}")}
 }
 fun configure(next:GlassFrame?,amount:Float,intensity:Float,inner:Boolean,rotation:Int,frozen:Boolean=false,angle:Float=Float.NaN){this.hingeAngle=angle;this.frozen=frozen;frame=next;this.amount=amount;this.intensity=intensity;this.inner=inner;this.rotation=rotation
  smoothingMs=FrameSmoothing.sanitize(context.getSharedPreferences("standalone",0).getFloat("smoothing_ms",12f))
  openThreshold=context.getSharedPreferences("standalone",0).getFloat("open_threshold",172f)
  val selected=context.getSharedPreferences("standalone",0).getString("animation_style","duo")=="classic"
  if(selected!=classic){classic=selected;bitmap=null;program=runCatching{RuntimeShader(if(classic)ClassicGlassShader.source else DuoGlassShader.source)}.getOrElse{RecoveryLog.add("Glass shader compilation failed: ${it.message}");null}}
  requestDraw()}
 override fun surfaceCreated(h:SurfaceHolder){
  if(!preview){
   GlassFrames.surface(this,surfaceControl)
   if(context.getSharedPreferences("standalone",0).getBoolean("cover_preview",true) && !context.getSharedPreferences("standalone",0).getBoolean("dual",false))PreviewTransition.markAnimation(surfaceControl)
   smoothingMs=FrameSmoothing.sanitize(context.getSharedPreferences("standalone",0).getFloat("smoothing_ms",12f))
  openThreshold=context.getSharedPreferences("standalone",0).getFloat("open_threshold",172f)
   if(!angleListening){angleListening=true;LiveAngles.add(angleListener)}
  }
  updateBufferSize();preferFastRefresh();requestDraw()
 }
 override fun surfaceChanged(h:SurfaceHolder,format:Int,w:Int,height:Int){if(!preview)GlassFrames.surface(this,surfaceControl);preferFastRefresh();requestDraw()}
 override fun surfaceDestroyed(h:SurfaceHolder){readinessGeneration++;readinessPending=false;lastReadyCapture=-1;lastReadyEndpoint=-1;if(angleListening){LiveAngles.remove(angleListener);angleListening=false};targetAngle=Float.NaN;renderedAngle=Float.NaN;lastFrameNanos=0L;choreographer.removeFrameCallback(vsync);frameQueued=false;appliedRate=0f;if(!preview)GlassFrames.surface(this,null);bitmap=null;frame=null;paint.shader=null}
 private fun drawFrame(){
  if(!holder.surface.isValid || width<=0 || height<=0)return
  runCatching{
   val started=SystemClock.elapsedRealtimeNanos()
   val canvas=holder.lockHardwareCanvas()
   var rendered:GlassFrame?=null
   var endpoint=false
   try{
    canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR)
    canvas.scale(canvas.width.toFloat()/width,canvas.height.toFloat()/height)
    if(preview && frame!=null)canvas.drawBitmap(frame!!.bitmap,null,RectF(0f,0f,width.toFloat(),height.toFloat()),null)
    if(amount<=.003f || (!preview && (!LiveAngles.fresh() || !LiveAngles.effectAllowed))){
     endpoint=!preview && inner && LiveAngles.fresh() && LiveAngles.effectAllowed && targetAngle.isFinite() && targetAngle>=FoldThreshold.sanitize(openThreshold)
     return@runCatching
    }
    val f=frame
    val size=Point()
    if(!preview && !frozen)context.getSystemService(DisplayManager::class.java).getDisplay(0)?.getRealSize(size)
    val fresh=f!=null && (preview || frozen || (GlassFramePolicy.usable(f.stamp,SystemClock.elapsedRealtime(),f.width,f.height,size.x,size.y)))
    val shader=program
    if(fresh && shader!=null && f!=null){
     if(bitmap!==f.bitmap){
      bitmap=f.bitmap
      shader.setInputShader("content",BitmapShader(f.bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP).apply{setFilterMode(BitmapShader.FILTER_MODE_LINEAR)})
      var level=f.bitmap
      for(i in 1..(if(classic)5 else 7)){
       level=Bitmap.createScaledBitmap(level,maxOf(1,level.width/2),maxOf(1,level.height/2),true)
       val map=BitmapShader(level,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
       map.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
       map.setLocalMatrix(Matrix().apply{setScale(f.bitmap.width.toFloat()/level.width,f.bitmap.height.toFloat()/level.height)})
       shader.setInputShader("mip$i",map)
      }
     }
     val fallback=inner && f.width.toFloat()/f.height<.7f
     // Match the live cover mirror exactly so blur never jumps between two content crops.
     val projectedCover=!inner && minOf(f.width,f.height).toFloat()/maxOf(f.width,f.height)>.7f
     val fit=if(projectedCover)LiveMirrorLayout.fill(f.bitmap.width,f.bitmap.height,width,height) else null
     val scale=fit?.get(0) ?: if(fallback)minOf(width.toFloat()/f.bitmap.width,height.toFloat()/f.bitmap.height) else 0f
     val ew=if(fallback || projectedCover)f.bitmap.width*scale else width.toFloat();val eh=if(fallback || projectedCover)f.bitmap.height*scale else height.toFloat()
     shader.setFloatUniform("texSize",f.bitmap.width.toFloat(),f.bitmap.height.toFloat())
     shader.setFloatUniform("origin",if(fallback)width-ew else 0f,if(fallback)(height-eh)/2f else 0f)
     shader.setFloatUniform("sampleScale",if(projectedCover)width/ew else 1f,if(projectedCover)height/eh else 1f)
     shader.setFloatUniform("sampleOffset",if(fit!=null)-fit[1]/ew else 0f,if(fit!=null)-fit[2]/eh else 0f)
     shader.setFloatUniform("extent",if(projectedCover)width.toFloat() else ew,if(projectedCover)height.toFloat() else eh);shader.setFloatUniform("progress",amount.coerceIn(0f,1f));shader.setFloatUniform("intensity",intensity)
     // Inner progress already includes the user's fully-open threshold (default 172°).
     val radians=if(inner) amount.coerceIn(0f,1f)*(Math.PI.toFloat()/2f) else
      Math.PI.toFloat()-(if(hingeAngle.isFinite())hingeAngle.coerceIn(0f,180f) else amount*110f)*(Math.PI.toFloat()/180f)
     if(!classic)shader.setFloatUniform("foldRadians",radians)
     shader.setFloatUniform("inner",if(inner)1f else 0f);shader.setFloatUniform("fallback",if(fallback)1f else 0f)
     shader.setFloatUniform("horizontal",if(rotation==Surface.ROTATION_90||rotation==Surface.ROTATION_270)1f else 0f)
     shader.setFloatUniform("reverse",if(rotation==Surface.ROTATION_90||rotation==Surface.ROTATION_180)1f else 0f)
     paint.shader=shader;canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
     if(!preview && !frozen && f.width==size.x && f.height==size.y)rendered=f
    }else{
     // Honest, live black-fade fallback; never leave stale captured content visible.
     val horizontal=rotation==Surface.ROTATION_90||rotation==Surface.ROTATION_270
     val reversed=rotation==Surface.ROTATION_90||rotation==Surface.ROTATION_180
     val colors=IntArray(65){i->var x=i/64f;if(reversed)x=1f-x;val edge=if(inner)1f-2*x else x;Color.argb((255*DuoShadeCurve.alpha(amount,edge,intensity)).toInt(),0,0,0)}
     paint.shader=LinearGradient(0f,0f,if(horizontal)0f else width.toFloat(),if(horizontal)height.toFloat() else 0f,colors,null,Shader.TileMode.CLAMP)
     canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
    }
   }finally{
    // Only successful glass rendering or a deliberate fully-open clear qualifies.
    // Attach before posting this buffer. A fallback draw never sends readiness.
    try{if(rendered!=null || endpoint)trackReadyFrame(rendered,endpoint)}finally{holder.unlockCanvasAndPost(canvas)}
    if(!preview)FrameTelemetry.record(inner,SystemClock.elapsedRealtimeNanos(),SystemClock.elapsedRealtimeNanos()-started,appliedRate)
   }
  }.onFailure{readinessGeneration++;readinessPending=false;RecoveryLog.add("Glass draw error: ${it.javaClass.simpleName}")}
 }
}

@Composable internal fun GlassPreview(progress:Float){
 val sample=remember{
  val bitmap=Bitmap.createBitmap(900,600,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);val p=Paint(Paint.ANTI_ALIAS_FLAG)
  p.shader=LinearGradient(0f,0f,900f,600f,intArrayOf(Color.rgb(27,73,111),Color.rgb(192,143,114)),null,Shader.TileMode.CLAMP);canvas.drawRect(0f,0f,900f,600f,p);p.shader=null
  for(i in 0..17){p.color=intArrayOf(Color.rgb(242,181,87),Color.rgb(86,191,161),Color.WHITE)[i%3];val x=75f+(i%6)*150f;val y=95f+(i/6)*185f;canvas.drawRoundRect(x-38,y-38,x+38,y+38,18f,18f,p);p.color=Color.WHITE;p.textSize=20f;canvas.drawText("App ${i+1}",x-30,y+67,p)}
  GlassFrame(bitmap,900,600,0)
 }
 AndroidView(factory={FrostSurface(it,true)},modifier=Modifier.fillMaxSize(),update={it.configure(sample,progress,1f,true,Surface.ROTATION_0)})
}
