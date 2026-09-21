package org.duofold.live
import org.junit.Assert.*
import org.junit.Test
class BackgroundRecoveryTest {
 @Test fun captureGateMeasuresActualSubmissionAndRetriesThrottle() {
  val gate=CaptureGate()
  assertEquals(0L,gate.delay(100))
  gate.submitted(150)
  assertEquals(400L,gate.delay(250))
  gate.failed(200,3)
  assertEquals(1000L,gate.intervalMs)
  assertEquals(950L,gate.delay(250))
  assertEquals(0L,gate.delay(1200))
  gate.submitted(1200);gate.succeeded()
  assertEquals(1000L,gate.delay(1200))
 }
 @Test fun captureBackoffIsBoundedAndCannotBeShortenedByEvents() {
  val gate=CaptureGate()
  repeat(20){gate.failed(100,3)}
  assertEquals(4000L,gate.intervalMs)
  assertEquals(3900L,gate.delay(200))
  assertEquals(0L,gate.delay(4100))
 }
 @Test fun sleepingReaderLeaseCanRecoverWithoutPermissionPrompt() {
  assertTrue(ReaderRecovery.needsRestart("Stopped"))
  assertTrue(ReaderRecovery.needsRestart("Log reader ended"))
  assertTrue(ReaderRecovery.needsRestart("Reader error: IO"))
  assertFalse(ReaderRecovery.needsRestart("Listening"))
  assertFalse(ReaderRecovery.needsRestart("Receiving"))
 }
}
