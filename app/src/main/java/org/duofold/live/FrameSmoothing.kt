package org.duofold.live
import kotlin.math.abs
import kotlin.math.exp
/** Causal, bounded interpolation; no prediction or overshoot when folding reverses. */
internal object FrameSmoothing {
 const val SMOOTH_MS=120f
 const val FAST_MS=30f
 const val DEFAULT_MS=SMOOTH_MS
 fun sanitize(value:Float)=if(value.isFinite())value.coerceIn(12f,120f) else DEFAULT_MS
 fun start(current:Float,target:Float,inner:Boolean,windowReveal:Boolean,startupEasing:Boolean):Float =
  if(!current.isFinite()&&!inner&&target<=12f&&windowReveal&&startupEasing)0f else current
 fun step(current:Float,target:Float,dtMs:Float,smoothingMs:Float=DEFAULT_MS):Float {
  return interpolate(current,target,dtMs.coerceIn(1f,50f),smoothingMs)
 }
 /** Independent buffers can pause during a display remap. Account for that
  * elapsed time instead of discarding it, then converge before endpoint removal.
  * Only lag toward the endpoint is limited; reversals still start at this pose. */
 fun handoffStep(current:Float,target:Float,dtMs:Float,smoothingMs:Float,openThreshold:Float):Float {
  val next=interpolate(current,target,dtMs.coerceIn(1f,250f),smoothingMs)
  if(!target.isFinite())return next
  val goal=target.coerceIn(0f,180f)
  val open=FoldThreshold.sanitize(openThreshold)
  val remaining=if(next>goal)goal else (open-goal).coerceAtLeast(0f)
  val lag=remaining*.25f
  return next.coerceIn(goal-lag,goal+lag)
 }
 private fun interpolate(current:Float,target:Float,elapsed:Float,smoothingMs:Float):Float {
  if(!target.isFinite())return current
  val goal=target.coerceIn(0f,180f)
  if(!current.isFinite())return goal
  val next=current+(goal-current)*(1f-exp(-elapsed/sanitize(smoothingMs)))
  return if(abs(next-goal)<.01f)goal else next.coerceIn(minOf(current,goal),maxOf(current,goal))
 }
}
