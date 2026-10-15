package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReleaseDeferralTest {
 // Plan E8: the defer timer shared by CoverHandoff and ConcurrentController (was duplicated, untested).
 @Test public void defersUntilClosedThenReleases(){
  ReleaseDeferral d=new ReleaseDeferral();
  assertTrue(d.keep(1000,true,true,false,()->false));
  assertTrue(d.deferring());
  assertFalse(d.keep(1016,true,true,false,()->true));   // base CLOSED
  assertFalse(d.deferring());
 }
 @Test public void boundedByTheCap(){
  ReleaseDeferral d=new ReleaseDeferral();
  assertTrue(d.keep(0,true,true,false,()->false));
  assertTrue(d.keep(CloseReleaseGate.MAX_DEFER_MS-1,true,true,false,()->false));
  assertFalse(d.keep(CloseReleaseGate.MAX_DEFER_MS,true,true,false,()->false));
 }
 @Test public void cheapEscapesSkipTheBaseStateQuery(){
  ReleaseDeferral d=new ReleaseDeferral();
  assertFalse(d.keep(0,true,false,false,()->{throw new AssertionError("queried with the screen off");}));
  assertFalse(d.keep(0,true,true,true,()->{throw new AssertionError("queried while reopening");}));
  assertFalse(d.keep(0,false,true,false,()->{throw new AssertionError("queried with nothing held");}));
 }
 @Test public void resetRestartsTheTimer(){
  ReleaseDeferral d=new ReleaseDeferral();
  d.keep(0,true,true,false,()->false);d.reset();
  assertTrue(d.keep(CloseReleaseGate.MAX_DEFER_MS+5,true,true,false,()->false));   // fresh 3 s window
 }
}
