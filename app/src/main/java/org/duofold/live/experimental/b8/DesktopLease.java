package org.duofold.live.experimental.b8;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Journalled desktop-policy experiment; native bars are measured separately. */
final class DesktopLease {
 private static final File BASE=new File("/data/local/tmp/fold-power-lab-desktop-leases");
 private final File report;private File lease;boolean restored=true;
 DesktopLease(File report){this.report=report;}
 private void log(String s){try(FileWriter w=new FileWriter(new File(report,"desktop-lease.txt"),true)){w.write(android.os.SystemClock.elapsedRealtime()+" "+s+"\n");}catch(IOException ignored){}}
 private static String run(String...args)throws Exception{
  Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
  if(!p.waitFor(4,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Settings command timed out");}
  ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] bytes=new byte[1024];int n;while((n=p.getInputStream().read(bytes))!=-1)b.write(bytes,0,n);
  String result=b.toString("UTF-8").trim();if(p.exitValue()!=0)throw new IOException("Settings command failed: "+result);return result;
 }
 private static void save(File file,String value)throws Exception{try(FileOutputStream s=new FileOutputStream(file)){s.write(value.getBytes("UTF-8"));s.getFD().sync();}}
 static synchronized void recover()throws Exception{
  if(!BASE.exists())return;File[] dirs=BASE.listFiles();if(dirs==null)throw new IOException("Cannot inspect desktop journal");
  for(File d:dirs)if(new File(d,"active").exists()){
   File script=new File(d,"restore.sh");if(!script.isFile())throw new IOException("Animator journal incomplete: "+d);
   run("sh",script.getPath());if(new File(d,"active").exists())throw new IOException("Desktop restoration still pending: "+d);
  }
 }
 synchronized void acquire()throws Exception{
  recover();
  if(!BASE.exists()&&!BASE.mkdir())throw new IOException("Cannot create desktop journal");
  android.system.Os.chmod(BASE.getPath(),0700);
  String old=run("settings","get","global","force_desktop_mode_on_external_displays");
  if(!old.equals("null")&&!old.equals("0")&&!old.equals("1"))throw new IOException("Unexpected original desktop policy; refusing to overwrite");
  lease=new File(BASE,UUID.randomUUID().toString());if(!lease.mkdir())throw new IOException("Cannot create lease");android.system.Os.chmod(lease.getPath(),0700);
  String marker=new File(lease,"active").getPath();
  String script=DesktopRestorePlan.script(marker,old);
  File restoreScript=new File(lease,"restore.sh");save(restoreScript,script);save(new File(lease,"original.txt"),old+"\n");
  // Journal before mutation. This lives outside the report directory and survives report cleanup.
  save(new File(lease,"active"),"pending\n");restored=false;
  File watchdogLog=new File(lease,"watchdog.txt");
  new ProcessBuilder("sh","-c","sleep 45; sh '"+restoreScript.getPath()+"'").redirectOutput(watchdogLog).redirectErrorStream(true).start();
  log("Original force_desktop_mode_on_external_displays="+old+"; independent watchdog armed for 45s; journal="+lease);
  try{
   NavigationProbe.sample(report,"before desktop policy write");
   run("settings","put","global","force_desktop_mode_on_external_displays","1");
   if(!run("settings","get","global","force_desktop_mode_on_external_displays").equals("1"))throw new IOException("Desktop setting write not observed");
   log("Desktop setting verified 1. Server policy/feature gates and visible bars NOT verified by setting readback.");
   NavigationProbe.sample(report,"after desktop policy write");
  }catch(Exception e){restore();throw e;}
 }
 synchronized void restore(){if(restored||lease==null)return;try{
  log(run("sh",new File(lease,"restore.sh").getPath()));
  restored=!new File(lease,"active").exists();log("Restore completed="+restored+" current setting="+run("settings","get","global","force_desktop_mode_on_external_displays"));
 }catch(Exception e){log("RESTORE PENDING: "+e+"; watchdog and next-test recovery remain armed");}}
}
