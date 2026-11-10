package org.duofold.live.experimental.b8;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
/** Preparation only, before any task move/primary switch. Never changes saved brightness. */
final class LiveB8BrightnessRefresh {
 interface Pause {void sleep(long ms)throws InterruptedException;}
 interface Watchdog {void start(File marker,int transaction)throws Exception;}
 private final File dir;private final Pause pause;private final Watchdog watchdog;private Object display;private Method setter;private final Map<Integer,File> pending=new HashMap<>();private volatile boolean stopped;private final Map<Integer,Long> identities=new HashMap<>();private final Map<Long,Long> clearedAt=new HashMap<>();private File journal;
 LiveB8BrightnessRefresh(File dir){this(dir,Thread::sleep,null);}
 LiveB8BrightnessRefresh(File dir,Pause pause,Watchdog watchdog){this.dir=dir;this.pause=pause;this.watchdog=watchdog;}
 private void log(String s){try(FileWriter w=new FileWriter(new File(dir,"brightness-refresh.txt"),true)){w.write(SystemClock.elapsedRealtime()+" uptime="+SystemClock.uptimeMillis()+" "+s+"\n");}catch(IOException ignored){}}
 private long physical(int id)throws Exception{Object info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(display,id);if(info==null)throw new IOException("Display missing");if(info.getClass().getField("state").getInt(info)!=2)throw new IOException("Panel is not ON during preparation");String uid=(String)info.getClass().getField("uniqueId").get(info);if(!uid.matches("local:[0-9]+"))throw new IOException("Unexpected physical identity");return Long.parseLong(uid.substring(6));}
 void refresh(Set<Long> missing)throws Exception{
  display=PowerExperiment.service("display","android.hardware.display.IDisplayManager");Class<?> api=Class.forName("android.hardware.display.IDisplayManager");setter=api.getMethod("setTemporaryBrightness",int.class,float.class);
  Field field=Class.forName("android.hardware.display.IDisplayManager$Stub").getDeclaredField("TRANSACTION_setTemporaryBrightness");field.setAccessible(true);int transaction=field.getInt(null);if(transaction<=0)throw new IOException("Invalid brightness transaction");
  // In concurrent mode the incoming display can follow D0's brightness.
  // Android's follower strategy takes precedence over that display's own
  // temporary brightness. Nudging only missing D1 can therefore do nothing.
  // Refresh the primary once and observe BOTH physical restored commands;
  // never copy D0's brightness/nits to D1 or accept either temporary probe.
  for(int id=0;id<2;id++)identities.put(id,physical(id));
  if(identities.get(0).equals(identities.get(1))||missing.isEmpty()||!identities.values().containsAll(missing))throw new IOException("Unexpected refresh panel identities");
  final int id=0;final long identity=identities.get(id);
  try{
   Object info=api.getMethod("getBrightnessInfo",int.class).invoke(display,id);if(info==null)throw new IOException("Missing brightness info");
   float original=info.getClass().getField("brightness").getFloat(info);
   if(!Float.isFinite(original)||original<=0||original>1)throw new IOException("Unsupported current brightness "+original);
   float probe=original>=0.5f?original-1f/255f:original+1f/255f;
   // Durable marker precedes mutation. Detached shell can clear even if this worker dies.
   if(journal==null){journal=new File(dir.getParentFile(),"fold-power-lab-brightness-lease-"+UUID.randomUUID());if(!journal.mkdir())throw new IOException("Cannot create independent brightness cleanup journal");}
   File marker=new File(journal,"D"+id+".pending");
   synchronized(this){if(stopped)throw new IOException("Stopped before refresh");try(FileOutputStream f=new FileOutputStream(marker)){f.write((identity+"\n").getBytes("UTF-8"));f.getFD().sync();}pending.put(id,marker);}
   String script="sleep 6; if [ -f '"+marker.getAbsolutePath()+"' ]; then service call display "+transaction+" i32 "+id+" i32 2143289344; fi";
   if(watchdog!=null)watchdog.start(marker,transaction);else new ProcessBuilder("sh","-c",script).redirectErrorStream(true).redirectOutput(new File(dir,"brightness-refresh-watchdog-D"+id+".txt")).start();
   synchronized(this){if(stopped)throw new IOException("Stopped before mutation");verifyPair();setter.invoke(display,id,probe);log("PRIMARY_PAIR_REFRESH missing="+missing+" primary="+identity+" affected="+identities.values()+" from="+original+" probe="+probe+"; saved brightness unchanged; watchdog6s");}
   pause.sleep(180);
   clear(id); // NaN returns control to existing brightness policy, including automatic brightness.
  }finally{clearAll();}
  synchronized(this){if(stopped||!pending.isEmpty())throw new IOException("Temporary brightness refresh stopped or cleanup pending");verifyPair();}
  pause.sleep(250);
  synchronized(this){if(stopped)throw new IOException("Stopped after refresh");verifyPair();}
 }
 private void verifyPair()throws Exception{for(int id=0;id<2;id++)if(physical(id)!=identities.get(id))throw new IOException("Mapping changed during primary brightness refresh");}
 private synchronized void clear(int id){File marker=pending.get(id);if(marker==null)return;try{long before=SystemClock.uptimeMillis();setter.invoke(display,id,Float.NaN);for(long physical:identities.values())clearedAt.put(physical,before);log("TEMPORARY_CLEARED D"+id+"; both physical panels require commands after uptime="+before+"; policy resumes, asynchronous hardware update");if(!marker.delete())log("MARKER_DELETE_FAILED D"+id);else pending.remove(id);}catch(Exception e){log("CLEAR_PENDING D"+id+" "+e+"; detached watchdog retained");}}
 private synchronized void clearAll(){for(Integer id:new ArrayList<>(pending.keySet()))clear(id);}
 synchronized long clearedAt(long id){Long time=clearedAt.get(id);return time==null?Long.MAX_VALUE:time;}
 synchronized Set<Long> refreshedPhysical(){return new HashSet<>(clearedAt.keySet());}
 synchronized void close(){stopped=true;clearAll();}
}
