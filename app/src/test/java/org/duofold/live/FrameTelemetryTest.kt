package org.duofold.live

import org.junit.Assert.*
import org.junit.Test

class FrameTelemetryTest {
 @Test fun consecutiveWindowsCountTheBoundaryIntervalOnce() {
  val owner=Any()
  for(i in 0..200)FrameTelemetry.record(false,owner,1_000_000_000L+i*10_000_000L,300_000,120f)
  val report=FrameTelemetry.report(3_500_000_000)
  assertTrue(report.contains("Cover: 100.0 submitted fps; 0.30 ms draw; 120 Hz requested; 100 intervals / 1000 ms"))
  assertTrue(report.contains("sampleEndedMs=3000; ageMs=500"))
 }
 @Test fun replacingRendererCannotPoolTwoHalfWindows() {
  val first=Any();val second=Any()
  for(i in 0..100)FrameTelemetry.record(false,first,10_000_000_000L+i*10_000_000L,100_000,120f)
  for(i in 101..150)FrameTelemetry.record(false,first,10_000_000_000L+i*10_000_000L,100_000,120f)
  for(i in 0..50)FrameTelemetry.record(false,second,11_510_000_000L+i*10_000_000L,100_000,120f)
  assertTrue(FrameTelemetry.report(12_010_000_000).contains("sampleEndedMs=11000"))
  for(i in 51..100)FrameTelemetry.record(false,second,11_510_000_000L+i*10_000_000L,100_000,120f)
  assertTrue(FrameTelemetry.report(12_510_000_000).contains("sampleEndedMs=12510"))
 }
 @Test fun panelsAndLongGapsStaySeparate() {
  val cover=Any();val inner=Any()
  for(i in 0..100)FrameTelemetry.record(false,cover,20_000_000_000L+i*10_000_000L,300_000,120f)
  for(i in 0..50)FrameTelemetry.record(true,inner,20_000_000_000L+i*20_000_000L,2_000_000,120f)
  val report=FrameTelemetry.report(21_000_000_000)
  assertTrue(report.contains("Cover: 100.0 submitted fps"))
  assertTrue(report.contains("Inner: 50.0 submitted fps"))
  for(i in 0..25)FrameTelemetry.record(true,inner,22_000_000_000L+i*20_000_000L,2_000_000,120f)
  assertTrue(FrameTelemetry.report(22_500_000_000).contains("sampleEndedMs=21000; ageMs=1500"))
  assertTrue(report.contains("Duo: use the per-session"))
 }
}
