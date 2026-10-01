package org.duofold.live;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
/** Short, read-only exact-app window observation after primary physical remapping. */
final class EndpointWindowTrace {
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Duo-endpoint-window");t.setDaemon(true);return t;});
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private volatile int generation;
 private synchronized void log(String s){events.addLast(SystemClock.elapsedRealtime()+": "+s);while(events.size()>24)events.removeFirst();}
 synchronized String report(){return "App window after remap (sampled readiness, not first-frame/optical timing):\n"+String.join("\n",events);}
 void start(int task,long remap){final int epoch=++generation;if(task<0){log("No exact fullscreen task selected; readiness not measured");return;}
  worker.execute(()->{if(epoch!=generation)return;long deadline=remap+2000,lastEnd=remap;String previous="";log("WATCH task="+task+"; remap observed="+remap);
   while(epoch==generation&&SystemClock.elapsedRealtime()<deadline){
    long began=SystemClock.elapsedRealtime();String state;
    try{state=EndpointWindowState.summarize(dump(),task);}catch(Exception e){state="UNAVAILABLE: "+e.getClass().getSimpleName();}
    long ended=SystemClock.elapsedRealtime();if(epoch!=generation)return;
    if(!state.equals(previous)){log("task="+task+" "+state+"; dump interval="+began+".."+ended+"; previous sample ended="+lastEnd);previous=state;}
    if(state.startsWith("READY")){log("First READY observation finished +"+(ended-remap)+" ms after observed remap; actual readiness may precede sample");return;}
    lastEnd=ended;try{Thread.sleep(150);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
   }
   if(epoch==generation)log("Window watch ended without READY; task="+task);
  });
 }
 private String dump()throws Exception{
  java.lang.Process process=new ProcessBuilder("dumpsys","window","windows").redirectErrorStream(true).start();
  StringBuilder out=new StringBuilder();
  Thread reader=new Thread(()->{try(BufferedReader in=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=in.readLine())!=null)synchronized(out){if(out.length()<1000000)out.append(line).append('\n');}}catch(IOException ignored){}},"Duo-window-dump-reader");reader.setDaemon(true);reader.start();
  try{if(!process.waitFor(500,TimeUnit.MILLISECONDS))throw new IOException("window dump timed out");reader.join(100);synchronized(out){return out.toString();}}
  finally{process.destroyForcibly();}
 }
}
