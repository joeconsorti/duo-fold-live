package org.duofold.live

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.exp

/** Relative tilt only: never writes hinge angles, display policy, or rotation settings.
 * All calls and sensor callbacks run on the main looper. No captures are requested. */
internal object MotionGlass : SensorEventListener {
 private var manager:SensorManager?=null
 private var sensor:Sensor?=null
 private var running=false
 private var baseline=false
 private var startNs=0L
 private var lastNs=0L
 private var angle=Float.NaN
 private var strength=1f
 private var smoothing=90f
 private var invert=false
 private var x=0f
 private var y=0f
 private val matrix=FloatArray(9)
 private val reference=FloatArray(9)
 private val delta=FloatArray(3)
 private val listeners=mutableSetOf<()->Unit>()
 private var state="Off"
 private val innerReturn=InnerMotionReturn()
 private var lastReturn="none"
 private var returnOpen=172f
 fun add(listener:()->Unit){listeners.add(listener)}
 fun remove(listener:()->Unit){listeners.remove(listener)}
 fun angle(value:Float){angle=value}
 fun refresh(context:Context,usable:Boolean){
  val p=RuntimeSettings.get(context)
  val enabled=p.getBoolean("motion_glass",false)
  val allowed=enabled && usable && p.getBoolean("enabled",false) && !p.getBoolean("dual",false) && LiveAngles.fresh() && LiveAngles.effectAllowed && angle.isFinite()
  val open=FoldThreshold.sanitize(p.getFloat("open_threshold",172f));returnOpen=open
  val wanted=allowed && angle>p.getFloat("closed_threshold",2f) && angle<open
  strength=MotionGlassMath.safe(p.getFloat("motion_glass_projection_strength",1f),1f,0f,2f)
  smoothing=MotionGlassMath.safe(p.getFloat("motion_glass_smoothing",90f),90f,0f,300f)
  invert=p.getBoolean("motion_glass_invert",false)
  if(allowed && angle>=open && running && baseline){
   beginReturn()
   if(innerReturn.pending && !innerReturn.finished(SystemClock.elapsedRealtime())){state="Returning inner glass to neutral";return}
   if(innerReturn.pending){lastReturn="completed 300 ms";RecoveryLog.add("Motion inner return complete; 300 ms")}
  }
  if(!wanted){stop();state=if(enabled)"Waiting for live fold (Live Screen only)" else "Off";return}
  if(innerReturn.pending){innerReturn.cancel(SystemClock.elapsedRealtime());lastReturn="interrupted by refold";RecoveryLog.add("Motion inner return interrupted by refold")}
  if(running)return
  if(manager==null){manager=context.applicationContext.getSystemService(SensorManager::class.java);sensor=manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR,false) ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR,false)}
  val s=sensor
  if(s==null){state="No supported rotation-vector sensor";return}
  startNs=SystemClock.elapsedRealtimeNanos();baseline=false;lastNs=0L
  running=runCatching{manager!!.registerListener(this,s,16_667,0)}.getOrDefault(false)
  state=if(running)"Active: ${s.name}" else "Sensor registration failed"
 }
 private fun beginReturn(){if(innerReturn.begin(SystemClock.elapsedRealtime())){lastReturn="started";RecoveryLog.add("Motion inner return start; amount=${innerReturn.lastAmount}; duration=300 ms")}}
 fun returning()=innerReturn.pending && !innerReturn.finished(SystemClock.elapsedRealtime())
 fun innerAmount(normal:Float,atOpen:Boolean,record:Boolean=false):Float {
  if(!running || !baseline)return if(atOpen)0f else normal
  val endpoint=if(angle.isFinite())angle>=returnOpen else atOpen
  if(endpoint){beginReturn();return if(innerReturn.pending)innerReturn.amount(SystemClock.elapsedRealtime()) else 0f}
  if(innerReturn.pending)innerReturn.cancel(SystemClock.elapsedRealtime())
  if(record)innerReturn.observe(normal)
  return normal
 }
 fun recenter(){innerReturn.clear();baseline=false;lastNs=0L;x=0f;y=0f;listeners.forEach{it()}}
 fun stop(){innerReturn.clear();if(running)manager?.unregisterListener(this);running=false;state="Stopped";baseline=false;lastNs=0L;if(x!=0f||y!=0f){x=0f;y=0f;listeners.forEach{it()}}}
 override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
 override fun onSensorChanged(event:SensorEvent){
  if(innerReturn.pending)return
  if(!running || event.timestamp<startNs || event.timestamp<=lastNs)return
  if(event.values.any{!it.isFinite()})return
  SensorManager.getRotationMatrixFromVector(matrix,event.values)
  if(!baseline || (lastNs>0L && event.timestamp-lastNs>500_000_000L)){
   matrix.copyInto(reference);baseline=true;lastNs=event.timestamp;x=0f;y=0f;listeners.forEach{it()};return
  }
  SensorManager.getAngleChange(delta,matrix,reference)
  if(!delta[1].isFinite() || !delta[2].isFinite())return
  val dt=((event.timestamp-lastNs)/1_000_000f).coerceIn(1f,100f);lastNs=event.timestamp
  val sign=if(invert)-1f else 1f
  x=MotionGlassMath.smooth(x,MotionGlassMath.offset(delta[2],strength*.5f)*sign,dt,smoothing)
  y=MotionGlassMath.smooth(y,-MotionGlassMath.offset(delta[1],strength*.5f)*sign,dt,smoothing)
  listeners.forEach{it()}
 }
 fun offset(rotation:Int,out:FloatArray){
  if(!running || lastNs==0L || (!innerReturn.pending && SystemClock.elapsedRealtimeNanos()-lastNs>500_000_000L)){out[0]=0f;out[1]=0f;return}
  MotionGlassMath.rotate(x,y,rotation,out)
 }
 fun report()="Motion glass: anchored projection; $state; response=${(strength*100).toInt()}%; smoothing=${smoothing.toInt()} ms; inverted=$invert; baseline=$baseline; inner return=$lastReturn; returning=${returning()}; sensor age=${if(lastNs==0L)-1 else (SystemClock.elapsedRealtimeNanos()-lastNs)/1_000_000} ms"
}

internal object MotionGlassMath {
 fun safe(value:Float,fallback:Float,low:Float,high:Float)=if(value.isFinite())value.coerceIn(low,high) else fallback
 fun offset(radians:Float,strength:Float)=if(radians.isFinite())(radians/.5235988f).coerceIn(-2f,2f)*strength else 0f
 fun smooth(current:Float,target:Float,dt:Float,ms:Float)=if(ms<=0f)target else current+(target-current)*(1f-exp(-dt/ms))
 fun rotate(x:Float,y:Float,rotation:Int,out:FloatArray){when(rotation){
  1->{out[0]=y;out[1]=-x}
  2->{out[0]=-x;out[1]=-y}
  3->{out[0]=-y;out[1]=x}
  else->{out[0]=x;out[1]=y}
 }}
}
