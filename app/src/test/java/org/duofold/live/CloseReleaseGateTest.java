package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class CloseReleaseGateTest {
 // Releasing the cover-concurrent override while the base state is still HALF_OPENED makes the device go
 // 5 -> 2 -> 0, and 2 -> 0 is a sleep transition (sleepDevice=true): the cover blanks. Release only once
 // the base state is CLOSED (5 -> 0, no sleep), when re-opening, or when the screen is already off.
 @Test public void releasesWhenBaseStateIsClosed(){
  assertTrue(CloseReleaseGate.allow(true,true,false,0));
 }
 @Test public void defersWhileHingeIsStillHalfOpen(){
  assertFalse(CloseReleaseGate.allow(false,true,false,0));        // Duo's 2 deg threshold hit first
  assertFalse(CloseReleaseGate.allow(false,true,false,2999));
 }
 @Test public void escapesAfterBoundedDefer(){
  assertTrue(CloseReleaseGate.allow(false,true,false,CloseReleaseGate.MAX_DEFER_MS));
 }
 @Test public void reopeningOrScreenOffReleaseImmediately(){
  assertTrue(CloseReleaseGate.allow(false,true,true,0));          // reopened past the handoff
  assertTrue(CloseReleaseGate.allow(false,false,false,0));        // not interactive: nothing to blank
 }
 // DirectHandoffPolicy's angle<=0 RELEASE of either override must pass the gate.
 @Test public void directReleaseOfBothOverridesIsGated(){
  assertTrue(CloseReleaseGate.gates(DirectHandoffPolicy.RELEASE,false));
  assertTrue(CloseReleaseGate.gates(DirectHandoffPolicy.RELEASE,true));
 }
 @Test public void nonReleaseActionsAreNotGated(){
  assertFalse(CloseReleaseGate.gates(DirectHandoffPolicy.HOLD,false));
  assertFalse(CloseReleaseGate.gates(DirectHandoffPolicy.INNER,false));
 }
 // Fold-Only turns effectAllowed false at the closed endpoint (direction flips at <= 2 deg); the
 // resulting release of a held cover override must be deferred like any other.
 @Test public void foldOnlyCloseDefersTheDisallowedRelease(){
  AnimationModePolicy mode=new AnimationModePolicy();
  mode.update("fold_only",179,true);assertTrue(mode.update("fold_only",60,true));
  assertFalse(mode.update("fold_only",0,true));                       // effect disallowed at the endpoint
  assertTrue(CloseReleaseGate.defer(true,false,true,false,0));          // held, base HALF_OPENED, screen on
  assertFalse(CloseReleaseGate.defer(true,true,true,false,0));          // base CLOSED: release now
  assertFalse(CloseReleaseGate.defer(false,false,true,false,0));        // nothing held: nothing to defer
 }
 // The escape is "screen off", not "locked / service missing / disabled".
 @Test public void lockedScreenOnStillDefers(){
  assertTrue(CloseReleaseGate.defer(true,false,true,false,0));
  assertFalse(CloseReleaseGate.defer(true,false,false,false,0));        // genuinely off: nothing to blank
 }
 // Teardown waits (bounded) for CLOSED before cancelling a held cover override.
 @Test public void teardownWaitsForClosedBounded(){
  assertTrue(CloseReleaseGate.waitBeforeTeardown(true,false,true,0));
  assertTrue(CloseReleaseGate.waitBeforeTeardown(true,false,true,CloseReleaseGate.MAX_DEFER_MS-1));
  assertFalse(CloseReleaseGate.waitBeforeTeardown(true,false,true,CloseReleaseGate.MAX_DEFER_MS));
  assertFalse(CloseReleaseGate.waitBeforeTeardown(true,true,true,0));
  assertFalse(CloseReleaseGate.waitBeforeTeardown(true,false,false,0));
  assertFalse(CloseReleaseGate.waitBeforeTeardown(false,false,true,0));
 }
 // Dual mode's outer (cover) session is the same override; endpoint/stale releases are gated.
 @Test public void dualOuterSessionReleaseIsGated(){
  assertTrue(CloseReleaseGate.defer(true,false,true,false,400));        // 350 ms endpoint timer no longer enough
  assertFalse(CloseReleaseGate.defer(true,false,true,true,0));          // reopening past 98 deg
 }
}
