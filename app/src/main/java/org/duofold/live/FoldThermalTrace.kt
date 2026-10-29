package org.duofold.live
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
/** Session-scoped, no wake locks; one-second samples only while a presentation is active. */
internal object FoldThermalTrace {
 private var session=0L;private var start=0L;private var cpu=0L;private var next=0L
 private var peak=0;private var peakBattery=-1;private var firstBattery=-1;private var samples=0
 private val rows=ArrayDeque<String>()
 private fun reading(c:Context):Pair<Int,Int>{val battery=c.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED));return c.getSystemService(PowerManager::class.java).currentThermalStatus to (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1)?:-1)}
 fun begin(c:Context,id:Long){session=id;start=SystemClock.elapsedRealtime();cpu=Process.getElapsedCpuTime();next=0;samples=0;peak=0;peakBattery=-1;firstBattery=-1;sample(c)}
 fun sample(c:Context){if(session==0L||SystemClock.elapsedRealtime()<next)return;next=SystemClock.elapsedRealtime()+1000;runCatching{val (status,temp)=reading(c);if(samples==0)firstBattery=temp;samples++;peak=maxOf(peak,status);peakBattery=maxOf(peakBattery,temp)}}
 fun end(c:Context?,reason:String,panelStarts:String){if(session==0L)return;next=0;if(c!=null)sample(c);val elapsed=SystemClock.elapsedRealtime()-start;val used=Process.getElapsedCpuTime()-cpu
  val row="build=${BuildConfig.VERSION_NAME} session=$session durationMs=$elapsed appCpuMs=$used appCpuOneCorePercent=${if(elapsed>0)String.format(java.util.Locale.US,"%.2f",100.0*used/elapsed) else "unknown"} sampledPeakThermal=${if(samples>0) peak.toString() else "unknown"} batteryStartTenthsC=$firstBattery batteryPeakTenthsC=$peakBattery samples=$samples reason=$reason panelStarts=[$panelStarts]"
  rows.addLast(row);while(rows.size>20)rows.removeFirst();RecoveryLog.add("FOLD_HEALTH $row");session=0
 }
 fun report()="Fold health (this process/build only; sampled peaks; app CPU excludes shell helper, compositor and GPU):\n"+rows.joinToString("\n")
}
