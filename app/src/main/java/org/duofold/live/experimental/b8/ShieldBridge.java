package org.duofold.live.experimental.b8;
import java.io.File;
/** Selects a backend once. The archived mirror implementation is untouched. */
final class ShieldBridge {
 static volatile boolean screenshotOnly=false;
 private final LiveWindowBridge mirror;
 private final ScreenshotOnlyBridge screenshot;
 ShieldBridge(File d){this(d,false);}
 ShieldBridge(File d,boolean freeze){boolean only=freeze&&screenshotOnly;mirror=only?null:new LiveWindowBridge(d,freeze);screenshot=only?new ScreenshotOnlyBridge(d,true):null;}
 void arm()throws Exception{if(mirror!=null)mirror.arm();else screenshot.arm();}
 boolean ready(){return mirror!=null?mirror.ready():screenshot.ready();}
 boolean isClosed(){return mirror!=null?mirror.isClosed():screenshot.isClosed();}
 void followPhysicalStacks(){if(mirror!=null)mirror.followPhysicalStacks();else screenshot.followPhysicalStacks();}
 void reveal(){if(mirror!=null)mirror.reveal();else screenshot.reveal();}
 void close(){if(mirror!=null)mirror.close();else screenshot.close();}
}
