package org.duofold.live
import kotlin.math.abs
import kotlin.math.exp
/** Causal, bounded interpolation; no prediction or overshoot when folding reverses. */
internal object FrameSmoothing {
 fun sanitize(value:Float)=if(value.isFinite())value.coerceIn(12f,120f) else 30f
 fun step(current:Float,target:Float,dtMs:Float,smoothingMs:Float=30f):Float {
  if(!target.isFinite())return current
  val goal=target.coerceIn(0f,180f)
  if(!current.isFinite())return goal
  val next=current+(goal-current)*(1f-exp(-dtMs.coerceIn(1f,50f)/sanitize(smoothingMs)))
  return if(abs(next-goal)<.01f)goal else next.coerceIn(minOf(current,goal),maxOf(current,goal))
 }
}
