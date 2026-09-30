package org.duofold.live;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
/** Bounded read-only window snapshots, never on the angle polling thread. */
final class ScreenshotWindowTrace {
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"screenshot-window-trace");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private final ArrayDeque<String> history=new ArrayDeque<>();
 synchronized String report(){return String.join("\n",history);}
 void capture(String phase){
  long requested=SystemClock.elapsedRealtime();
  try{worker.execute(()->{
   StringBuilder out=new StringBuilder("Window snapshot ").append(phase).append(" requested=").append(requested).append(" started=").append(SystemClock.elapsedRealtime()).append("\n");
   java.lang.Process process=null;
   try{
    process=new ProcessBuilder("dumpsys","window","windows").redirectErrorStream(true).start();
    final java.lang.Process running=process;final StringBuilder lines=new StringBuilder();
    Thread reader=new Thread(()->{
     try(BufferedReader in=new BufferedReader(new InputStreamReader(running.getInputStream(),StandardCharsets.UTF_8))){
      String line;while((line=in.readLine())!=null){
       if(line.contains("Window #")||line.contains("mDisplayId=")||line.contains("mHasSurface=")||line.contains("isOnScreen=")||line.contains("mCurrentFocus=")||line.contains("mFocusedApp=")||line.contains("mFrame=")){
        synchronized(lines){if(lines.length()<7000)lines.append(line.length()>350?line.substring(0,350):line).append('\n');}
       }
      }
     }catch(IOException ignored){}
    },"screenshot-dump-reader");reader.setDaemon(true);reader.start();
    if(!process.waitFor(1500,TimeUnit.MILLISECONDS)){process.destroyForcibly();out.append("dump timed out; partial results\n");}
    reader.join(200);
    synchronized(lines){out.append(lines.length()==0?"No matching window fields returned\n":lines);}
   }catch(Exception e){out.append("unavailable: ").append(e);}
   finally{if(process!=null)process.destroy();}
   synchronized(this){history.addLast(out.toString());while(history.size()>4)history.removeFirst();}
  });}catch(RejectedExecutionException ignored){}
 }
}
