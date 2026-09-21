package com.mccal.folio
import kotlin.math.abs
import kotlin.math.exp
/** Causal, bounded interpolation; no prediction or overshoot when folding reverses. */
internal object FrameSmoothing {
 fun step(current:Float,target:Float,dtMs:Float):Float {
  if(!target.isFinite())return current
  val goal=target.coerceIn(0f,180f)
  if(!current.isFinite())return goal
  val next=current+(goal-current)*(1f-exp(-dtMs.coerceIn(1f,50f)/24f))
  return if(abs(next-goal)<.01f)goal else next.coerceIn(minOf(current,goal),maxOf(current,goal))
 }
}
