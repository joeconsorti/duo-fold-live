package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class MirrorAttachGateTest {
 // An attach that read "preview allowed" before a revoke/stop must not leave a mirror attached.
 @Test public void ticketStaysValidWithoutRevoke(){
  MirrorAttachGate g=new MirrorAttachGate();
  int t=g.ticket();
  assertTrue(g.valid(t));
 }
 @Test public void revokeDuringAttachInvalidatesTheTicket(){
  MirrorAttachGate g=new MirrorAttachGate();
  int t=g.ticket();
  g.revoke();
  assertFalse(g.valid(t));
  assertTrue(g.valid(g.ticket()));   // the next attempt (client retries in 100 ms) is fine
 }
}
