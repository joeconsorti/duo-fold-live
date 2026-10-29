package org.duofold.live.experimental.b8;

import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Bounded, local recovery evidence. No screenshots, uploads, or repeated dumpsys loop. */
final class LiveB8Evidence {
 private volatile String recovered="Recovery collection pending";
 private final String boot=read(new File("/proc/sys/kernel/random/boot_id"),256).trim();
 LiveB8Evidence(File root,boolean collect){
  if(!collect){recovered="Recovery collector disabled in host tests";return;}
  File[] dirs=root.listFiles(f->f.isDirectory()&&f.getName().startsWith("duo-live-b8-"+android.os.Process.myUid()+"-"));
  if(dirs!=null)Arrays.sort(dirs,Comparator.comparingLong(File::lastModified));
  final File previous=dirs!=null&&dirs.length>0?dirs[dirs.length-1]:null;
  Thread t=new Thread(()->{
   StringBuilder out=new StringBuilder("Recovery evidence: bootId="+boot+" shellPid="+android.os.Process.myPid()+" elapsed="+SystemClock.elapsedRealtime()+"\n");
   if(previous!=null)out.append("Previous session ").append(previous.getName()).append(" (may precede this version):\n").append(read(new File(previous,"live-journal.txt"),16000));
   out.append("\nBoot reason: ").append(command(root,"getprop","sys.boot.reason"));
   out.append("\nRecent crash buffer (may be older/unrelated; absence is not proof):\n").append(command(root,"logcat","-b","crash","-d"));
   recovered=out.toString();
  },"duo-b8-recovery");t.setDaemon(true);t.start();
 }
 String report(){return recovered;}
 synchronized void record(File dir,String message){
  if(dir==null)return;
  File journal=new File(dir,"live-journal.txt");
  try{
   if(journal.length()>128*1024){File old=new File(dir,"live-journal.previous.txt");if(old.exists())old.delete();if(!journal.renameTo(old))return;}
   String[] fds=new File("/proc/self/fd").list(),threads=new File("/proc/self/task").list();
   Runtime rt=Runtime.getRuntime();
   try(FileWriter w=new FileWriter(journal,true)){w.write("wall="+System.currentTimeMillis()+" elapsed="+SystemClock.elapsedRealtime()+" boot="+boot+" pid="+android.os.Process.myPid()+" fds="+(fds==null?-1:fds.length)+" threads="+(threads==null?-1:threads.length)+" javaUsed="+(rt.totalMemory()-rt.freeMemory())+" "+message+"\n");}
  }catch(IOException ignored){}
 }
 private static String read(File f,int cap){
  try(RandomAccessFile in=new RandomAccessFile(f,"r")){long size=in.length();if(size>cap)in.seek(size-cap);byte[] bytes=new byte[cap];int n=in.read(bytes);return n<0?"":new String(bytes,0,n,StandardCharsets.UTF_8);}
  catch(Exception e){return "unavailable: "+e.getClass().getSimpleName();}
 }
 private static String command(File root,String...args){
  File temp=null;java.lang.Process process=null;
  try{
   temp=File.createTempFile("duo-b8-evidence-",".tmp",root);android.system.Os.chmod(temp.getPath(),0600);
   process=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(temp).start();
   if(!process.waitFor(800,TimeUnit.MILLISECONDS)){process.destroyForcibly();process.waitFor(200,TimeUnit.MILLISECONDS);return "Timed out: "+args[0]+"; partial output follows:\n"+read(temp,256*1024);}
   return read(temp,256*1024);
  }catch(Exception e){return "Unavailable: "+e;}
  finally{if(process!=null&&process.isAlive())process.destroyForcibly();if(temp!=null)temp.delete();}
 }
}
