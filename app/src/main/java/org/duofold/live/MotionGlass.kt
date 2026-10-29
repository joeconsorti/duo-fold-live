package org.duofold.live

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.exp
import kotlin.math.abs
import kotlin.math.sign

/** Relative tilt only: never writes hinge angles, display policy, or rotation settings.
 * All calls and sensor callbacks run on the main looper. No captures are requested. */
internal object MotionGlass : SensorEventListener {
 private var manager:SensorManager?=null
 private var sensor:Sensor?=null
 private var running=false
 private var baseline=false
 private var startNs=0L
 private var lastNs=0L
 private var immediateBaseline=false
 private val startup=MotionBaselineGate()
 private val history=java.util.ArrayDeque<String>()
 private var lastTraceNs=0L
 private fun trace(message:String){if(history.size>=200)history.removeFirst();history.addLast("${SystemClock.elapsedRealtime()}: $message")}
 private var angle=Float.NaN
 private var innerEnabled=false
 private var coverEnabled=false
 private var coverStrength=1f
 private var innerStrength=1f
 private var smoothing=90f
 private val invert=false // Reverse-direction option retired; use the normal direction in both modes.
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
  innerEnabled=p.getBoolean("motion_glass",true)
  coverEnabled=p.getBoolean("motion_glass_cover_enabled",false)
  val enabled=innerEnabled || coverEnabled
  val allowed=enabled && usable && p.getBoolean("enabled",false) && !p.getBoolean("dual",false) && LiveAngles.fresh() && LiveAngles.effectAllowed && angle.isFinite()
  val open=FoldThreshold.sanitize(p.getFloat("open_threshold",172f));returnOpen=open
  val wanted=allowed && angle>p.getFloat("closed_threshold",2f) && angle<open
  val legacyStrength=MotionGlassMath.safe(p.getFloat("motion_glass_projection_strength",1f),1f,0f,2f)
  coverStrength=MotionGlassMath.safe(p.getFloat("motion_glass_cover_strength",legacyStrength),legacyStrength,0f,2f)
  innerStrength=MotionGlassMath.safe(p.getFloat("motion_glass_inner_strength",legacyStrength),legacyStrength,0f,2f)
  smoothing=MotionGlassMath.safe(p.getFloat("motion_glass_smoothing",90f),90f,0f,300f)
  if(allowed && innerEnabled && angle>=open && running && baseline){
   beginReturn()
   if(innerReturn.pending && !innerReturn.finished(SystemClock.elapsedRealtime())){state="Returning inner glass to neutral";return}
   if(innerReturn.pending){lastReturn="completed ${InnerMotionReturn.DURATION_MS} ms";RecoveryLog.add("Motion inner return complete; ${InnerMotionReturn.DURATION_MS} ms")}
  }
  if(!wanted){stop();state=if(enabled)"Waiting for fold" else "Off";return}
  if(innerReturn.pending){innerReturn.cancel(SystemClock.elapsedRealtime());lastReturn="interrupted by refold";RecoveryLog.add("Motion inner return interrupted by refold")}
  if(running)return
  if(manager==null){manager=context.applicationContext.getSystemService(SensorManager::class.java);sensor=manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR,false) ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR,false)}
  val s=sensor
  if(s==null){state="No supported rotation-vector sensor";return}
  startNs=SystemClock.elapsedRealtimeNanos();baseline=false;lastNs=0L;x=0f;y=0f
  immediateBaseline=angle>open/2f
  trace("START hinge=$angle; baseline=${if(immediateBaseline)"first fresh sample (inner close)" else "settling (cover open)"}; dead zone=1.5 deg")
  running=runCatching{manager!!.registerListener(this,s,16_667,0)}.getOrDefault(false)
  state=if(running)"Active: ${s.name}" else "Sensor registration failed"
 }
 private fun beginReturn(){if(innerReturn.begin(SystemClock.elapsedRealtime())){lastReturn="started";RecoveryLog.add("Motion inner return start; amount=${innerReturn.lastAmount}; duration=${InnerMotionReturn.DURATION_MS} ms")}}
 fun returning()=innerEnabled && innerReturn.pending && !innerReturn.finished(SystemClock.elapsedRealtime())
 fun innerAmount(normal:Float,atOpen:Boolean,record:Boolean=false):Float {
  if(!innerEnabled || !running || !baseline)return if(atOpen)0f else normal
  val endpoint=if(angle.isFinite())angle>=returnOpen else atOpen
  if(endpoint){beginReturn();return if(innerReturn.pending)innerReturn.amount(SystemClock.elapsedRealtime()) else 0f}
  if(innerReturn.pending)innerReturn.cancel(SystemClock.elapsedRealtime())
  if(record)innerReturn.observe(normal)
  return normal
 }
 fun recenter(){trace("RECENTER");innerReturn.clear();baseline=false;lastNs=0L;x=0f;y=0f;listeners.forEach{it()}}
 fun stop(){innerReturn.clear();if(running){trace("STOP hinge=$angle; x=$x y=$y");manager?.unregisterListener(this)};running=false;state="Stopped";baseline=false;lastNs=0L;if(x!=0f||y!=0f){x=0f;y=0f;listeners.forEach{it()}}}
 override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int)=Unit
 override fun onSensorChanged(event:SensorEvent){
  if(innerReturn.pending)return
  if(!running || event.timestamp<startNs || event.timestamp<=lastNs)return
  if(event.values.any{!it.isFinite()})return
  SensorManager.getRotationMatrixFromVector(matrix,event.values)
  if(lastNs==0L || event.timestamp-lastNs>500_000_000L){
   val lockImmediately=immediateBaseline && lastNs==0L
   trace("BASELINE ${if(lockImmediately)"locked immediately for inner close" else "settling"}; hinge=$angle; reason=${if(lastNs==0L)"start" else "sensor gap"}")
   matrix.copyInto(reference);startup.reset(event.timestamp);baseline=lockImmediately;lastNs=event.timestamp;x=0f;y=0f;listeners.forEach{it()};return
  }
  if(!baseline){
   SensorManager.getAngleChange(delta,matrix,reference)
   if(delta.any{!it.isFinite()})return
   if(delta.any{abs(it)>.006108652f}){matrix.copyInto(reference);startup.moved(event.timestamp)}
   lastNs=event.timestamp
   if(startup.ready(event.timestamp)){
    matrix.copyInto(reference);baseline=true
    trace("BASELINE locked; hinge=$angle; settleMs=${startup.ageMs(event.timestamp)}; bounded=${startup.ageMs(event.timestamp)>=350}")
   }
   listeners.forEach{it()};return
  }
  SensorManager.getAngleChange(delta,matrix,reference)
  if(!delta[1].isFinite() || !delta[2].isFinite())return
  val dt=((event.timestamp-lastNs)/1_000_000f).coerceIn(1f,100f);lastNs=event.timestamp
  val sign=if(invert)-1f else 1f
  x=MotionGlassMath.smooth(x,MotionGlassMath.offset(MotionGlassMath.deadZone(delta[2]),.5f)*sign,dt,smoothing)
  y=MotionGlassMath.smooth(y,-MotionGlassMath.offset(MotionGlassMath.deadZone(delta[1]),.5f)*sign,dt,smoothing)
  if(event.timestamp-lastTraceNs>=200_000_000L){lastTraceNs=event.timestamp;trace("SAMPLE hinge=$angle; tiltDeg=${delta[2]*57.29578f},${delta[1]*57.29578f}; x=$x y=$y; deliveryMs=${(SystemClock.elapsedRealtimeNanos()-event.timestamp)/1_000_000}")}
  listeners.forEach{it()}
 }
 fun offset(rotation:Int,out:FloatArray,inner:Boolean){
  if(!(if(inner)innerEnabled else coverEnabled) || !running || lastNs==0L || (!innerReturn.pending && SystemClock.elapsedRealtimeNanos()-lastNs>500_000_000L)){out[0]=0f;out[1]=0f;return}
  // Smooth neutral sensor motion once; scale independently for each physical panel.
  val strength=if(inner)innerStrength else coverStrength
  MotionGlassMath.rotate(x*strength,y*strength,rotation,out)
 }
 fun report()="Motion glass: anchored projection; $state; inner enabled=$innerEnabled; cover experiment enabled=$coverEnabled; cover response=${(coverStrength*100).toInt()}%; inner response=${(innerStrength*100).toInt()}%; smoothing=${smoothing.toInt()} ms; inverted=$invert; baseline=$baseline; inner return=$lastReturn; returning=${returning()}; sensor age=${if(lastNs==0L)-1 else (SystemClock.elapsedRealtimeNanos()-lastNs)/1_000_000} ms; startup baseline=immediate inner close / 120–350 ms cover open; dead zone=1.5 deg"+"\nMotion history (software samples; 200 ms target):\n"+history.joinToString("\n")
}

/** Bounded startup window: ignore the opening tug, then freeze the reference.
 * Never follows the user after locking; intentional subsequent tilt remains relative. */
internal class MotionBaselineGate {
 private var start=0L
 private var stableSince=0L
 fun reset(ns:Long){start=ns;stableSince=ns}
 fun moved(ns:Long){stableSince=ns}
 fun ageMs(ns:Long)=(ns-start)/1_000_000
 fun ready(ns:Long)=ns-start>=350_000_000L || (ns-start>=120_000_000L && ns-stableSince>=80_000_000L)
}

internal object MotionGlassMath {
 // Continuous soft dead zone: zero for tiny movements, normal slope beyond 3 degrees.
 fun deadZone(radians:Float):Float {
  if(!radians.isFinite())return 0f
  val d=.02617994f
  val a=abs(radians)
  if(a<=d)return 0f
  val t=((a-d)/d).coerceIn(0f,1f)
  return sign(radians)*(a-d)*t*t*(3f-2f*t)
 }
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
