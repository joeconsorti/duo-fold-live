package org.duofold.live.experimental.b8;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
/** Preparation only, before any task move/primary switch. Never changes saved brightness. */
final class BrightnessRefresh {
 private final File dir;private Object display;private Method setter;private final Map<Integer,File> pending=new HashMap<>();private volatile boolean stopped;private final Map<Integer,Long> identities=new HashMap<>();private final Map<Long,Long> clearedAt=new HashMap<>();private File journal;
 BrightnessRefresh(File dir){this.dir=dir;}
 private void log(String s){try(FileWriter w=new FileWriter(new File(dir,"brightness-refresh.txt"),true)){w.write(SystemClock.elapsedRealtime()+" uptime="+SystemClock.uptimeMillis()+" "+s+"\n");}catch(IOException ignored){}}
 private long physical(int id)throws Exception{Object info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(display,id);if(info==null)throw new IOException("Display missing");if(info.getClass().getField("state").getInt(info)!=2)throw new IOException("Panel is not ON during preparation");String uid=(String)info.getClass().getField("uniqueId").get(info);if(!uid.matches("local:[0-9]+"))throw new IOException("Unexpected physical identity");return Long.parseLong(uid.substring(6));}
 void refresh(Set<Long> missing)throws Exception{
  display=PowerExperiment.service("display","android.hardware.display.IDisplayManager");Class<?> api=Class.forName("android.hardware.display.IDisplayManager");setter=api.getMethod("setTemporaryBrightness",int.class,float.class);
  Field field=Class.forName("android.hardware.display.IDisplayManager$Stub").getDeclaredField("TRANSACTION_setTemporaryBrightness");field.setAccessible(true);int transaction=field.getInt(null);if(transaction<=0)throw new IOException("Invalid brightness transaction");
  try{for(int id=0;id<2;id++){
   long identity=physical(id);if(!missing.contains(identity))continue;
   Object info=api.getMethod("getBrightnessInfo",int.class).invoke(display,id);if(info==null)throw new IOException("Missing brightness info");
   float original=info.getClass().getField("brightness").getFloat(info);
   if(!Float.isFinite(original)||original<=0||original>1)throw new IOException("Unsupported current brightness "+original);
   float probe=original>=0.5f?original-1f/255f:original+1f/255f;
   // Durable marker precedes mutation. Detached shell can clear even if this worker dies.
   if(journal==null){journal=new File(dir.getParentFile(),"fold-power-lab-brightness-lease-"+UUID.randomUUID());if(!journal.mkdir())throw new IOException("Cannot create independent brightness cleanup journal");}
   File marker=new File(journal,"D"+id+".pending");
   synchronized(this){if(stopped)throw new IOException("Stopped before refresh");try(FileOutputStream f=new FileOutputStream(marker)){f.write((identity+"\n").getBytes("UTF-8"));f.getFD().sync();}pending.put(id,marker);identities.put(id,identity);}
   String script="sleep 6; if [ -f '"+marker.getAbsolutePath()+"' ]; then service call display "+transaction+" i32 "+id+" i32 2143289344; fi";
   new ProcessBuilder("sh","-c",script).redirectErrorStream(true).redirectOutput(new File(dir,"brightness-refresh-watchdog-D"+id+".txt")).start();
   synchronized(this){if(stopped)throw new IOException("Stopped before mutation");if(physical(id)!=identity)throw new IOException("Mapping changed before refresh");setter.invoke(display,id,probe);log("TEMPORARY_NUDGE D"+id+" physical="+identity+" from="+original+" probe="+probe+"; saved brightness unchanged; watchdog6s");}
   Thread.sleep(180);
   clear(id); // NaN returns control to existing brightness policy, including automatic brightness.
  }}finally{clearAll();}
  synchronized(this){if(!pending.isEmpty())throw new IOException("Temporary brightness cleanup pending");}
  Thread.sleep(250);
 }
 private synchronized void clear(int id){File marker=pending.get(id);if(marker==null)return;try{long before=SystemClock.uptimeMillis();setter.invoke(display,id,Float.NaN);clearedAt.put(identities.get(id),before);log("TEMPORARY_CLEARED D"+id+"; policy resumes, asynchronous hardware update");if(!marker.delete())log("MARKER_DELETE_FAILED D"+id);else pending.remove(id);}catch(Exception e){log("CLEAR_PENDING D"+id+" "+e+"; detached watchdog retained");}}
 private synchronized void clearAll(){for(Integer id:new ArrayList<>(pending.keySet()))clear(id);}
 synchronized long clearedAt(long id){Long time=clearedAt.get(id);return time==null?Long.MAX_VALUE:time;}
 synchronized void close(){stopped=true;clearAll();}
}
