package org.duofold.live;
import java.util.function.BooleanSupplier;
/**
 * The bounded "wait for the hinge to report CLOSED" timer behind every override release (CloseReleaseGate), shared
 * by CoverHandoff and ConcurrentController (plan E8; it was duplicated in both). The base-state query is supplied
 * lazily and only made after the cheap escapes (nothing held, screen off, reopening, cap), because it is a
 * system_server call on the 4 ms angle poll.
 */
final class ReleaseDeferral {
 private boolean active;private long since;
 /** True = keep the override for now. Starts the 3 s window on the first deferral; clears it on release. */
 boolean keep(long now,boolean held,boolean screenOn,boolean reopening,BooleanSupplier baseClosed){
  long deferred=active?now-since:0;
  if(!held||CloseReleaseGate.allow(false,screenOn,reopening,deferred)||!CloseReleaseGate.defer(true,baseClosed.getAsBoolean(),screenOn,reopening,deferred)){active=false;return false;}
  if(!active){active=true;since=now;}
  return true;
 }
 boolean deferring(){return active;}
 void reset(){active=false;}
}
