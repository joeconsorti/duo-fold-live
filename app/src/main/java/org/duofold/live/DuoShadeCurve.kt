package org.duofold.live
import kotlin.math.pow
/** Darkening equations ported from chuspeeism/iphone-duo main.js (MIT, jadon7 2026).
 * Its texture blur and 3D projection cannot be implemented by translucent paint. */
internal object DuoShadeCurve {
 fun progress(angle:Float,inner:Boolean,openThreshold:Float=172f):Float = if(!angle.isFinite())0f else
  (if(inner)(FoldThreshold.sanitize(openThreshold)-angle)/(FoldThreshold.sanitize(openThreshold)-90f) else angle/110f).coerceIn(0f,1f)
 fun alpha(progress:Float,edge:Float,intensity:Float=1f):Float {
  val p=progress.coerceIn(0f,1f)
  val motion=p*p*(3f-2f*p)
  val dark=((edge-.2f)/.8f).coerceIn(0f,1f).pow(1.35f)
  return (2f*motion*dark*intensity).coerceIn(0f,1f)
 }
}
