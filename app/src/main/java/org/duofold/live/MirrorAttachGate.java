package org.duofold.live;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Generation guard for InnerLiveMirror attaches. Code 4 takes a ticket before it reads "preview
 * allowed"; every revoke/stop bumps the generation. An attach whose ticket is stale after its compositor commit
 * closes what it built instead of leaving the cover mirror attached until the next start.
 */
final class MirrorAttachGate {
 private final AtomicInteger generation=new AtomicInteger();
 int ticket(){return generation.get();}
 boolean valid(int ticket){return generation.get()==ticket;}
 void revoke(){generation.incrementAndGet();}
}
