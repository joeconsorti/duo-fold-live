package org.duofold.live

import android.graphics.RuntimeShader
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Only unused programs cross threads. Each panel owns its mutable shader after take(). */
internal object B8GlassPrograms {
 private val ready=ArrayBlockingQueue<RuntimeShader>(2)
 private val compiling=AtomicBoolean(false)
 private val worker=Executors.newSingleThreadExecutor{job->Thread(job,"Duo-glass-prep").apply{isDaemon=true}}
 fun prepare(){
  val missing=ready.remainingCapacity()
  if(missing==0||!compiling.compareAndSet(false,true))return
  worker.execute{
   try{repeat(missing){ready.offer(RuntimeShader(DuoGlassShader.source))}}
   catch(e:Exception){RecoveryLog.add("B8 glass prewarm unavailable: ${e.javaClass.simpleName}")}
   finally{compiling.set(false)}
  }
 }
 fun take():RuntimeShader?=ready.poll()
}
