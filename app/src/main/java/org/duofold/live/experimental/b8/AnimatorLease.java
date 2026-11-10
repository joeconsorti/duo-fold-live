package org.duofold.live.experimental.b8;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Narrow, journalled global animator-scale experiment. Never claims to remove ColorFade. */
final class AnimatorLease {
 private static final File BASE=new File("/data/local/tmp/fold-power-lab-animator-leases");
 private final File report;private File lease;boolean restored=true;
 AnimatorLease(File report){this.report=report;}
 private void log(String s){try(FileWriter w=new FileWriter(new File(report,"animator-lease.txt"),true)){w.write(android.os.SystemClock.elapsedRealtime()+" "+s+"\n");}catch(IOException ignored){}}
 private static String run(String...args)throws Exception{
  Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
  if(!p.waitFor(4,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Settings command timed out");}
  ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] bytes=new byte[1024];int n;while((n=p.getInputStream().read(bytes))!=-1)b.write(bytes,0,n);
  String result=b.toString("UTF-8").trim();if(p.exitValue()!=0)throw new IOException("Settings command failed: "+result);return result;
 }
 private static void save(File file,String value)throws Exception{try(FileOutputStream s=new FileOutputStream(file)){s.write(value.getBytes("UTF-8"));s.getFD().sync();}}
 static synchronized void recover()throws Exception{
  if(!BASE.exists())return;File[] dirs=BASE.listFiles();if(dirs==null)throw new IOException("Cannot inspect animator journal");
  for(File d:dirs)if(new File(d,"active").exists()){
   File script=new File(d,"restore.sh");if(!script.isFile())throw new IOException("Animator journal incomplete: "+d);
   run("sh",script.getPath());if(new File(d,"active").exists())throw new IOException("Animator restoration still pending: "+d);
  }
 }
 synchronized void acquire()throws Exception{
  recover();
  if(!BASE.exists()&&!BASE.mkdir())throw new IOException("Cannot create animator journal");
  android.system.Os.chmod(BASE.getPath(),0700);
  String old=run("settings","get","global","animator_duration_scale");
  if(!old.equals("null")){float v;try{v=Float.parseFloat(old);}catch(Exception e){throw new IOException("Unrecognized original animator scale");}if(!Float.isFinite(v)||v<0||v>20)throw new IOException("Original animator scale outside supported range");old=Float.toString(v);}
  lease=new File(BASE,UUID.randomUUID().toString());if(!lease.mkdir())throw new IOException("Cannot create lease");android.system.Os.chmod(lease.getPath(),0700);
  String marker=new File(lease,"active").getPath();
  String script=AnimatorRestorePlan.script(marker,old);
  File restoreScript=new File(lease,"restore.sh");save(restoreScript,script);save(new File(lease,"original.txt"),old+"\n");
  // Journal before mutation. This lives outside the report directory and survives report cleanup.
  save(new File(lease,"active"),"pending\n");restored=false;
  File watchdogLog=new File(lease,"watchdog.txt");
  new ProcessBuilder("sh","-c","sleep 45; sh '"+restoreScript.getPath()+"'").redirectOutput(watchdogLog).redirectErrorStream(true).start();
  log("Original animator_duration_scale="+old+"; independent watchdog armed for 45s; journal="+lease);
  try{
   run("settings","put","global","animator_duration_scale","0");
   if(Float.parseFloat(run("settings","get","global","animator_duration_scale"))!=0)throw new IOException("Scale write not observed");
   Object wm=PowerExperiment.service("window","android.view.IWindowManager");Class<?> api=Class.forName("android.view.IWindowManager");
   long deadline=android.os.SystemClock.elapsedRealtime()+1500;boolean verified=false;
   while(android.os.SystemClock.elapsedRealtime()<deadline){float v=(float)api.getMethod("getCurrentAnimatorScale").invoke(wm);if(v==0){verified=true;break;}Thread.sleep(50);}
   if(!verified)throw new IOException("WindowManager effective scale did not become zero");
   log("Settings and WindowManager effective animator scale verified ZERO. Forced-black ColorFade level remains a separate path.");
  }catch(Exception e){restore();throw e;}
 }
 synchronized void restore(){if(restored||lease==null)return;try{
  log(run("sh",new File(lease,"restore.sh").getPath()));
  restored=!new File(lease,"active").exists();log("Restore completed="+restored+" current setting="+run("settings","get","global","animator_duration_scale"));
 }catch(Exception e){log("RESTORE PENDING: "+e+"; watchdog and next-test recovery remain armed");}}
}
