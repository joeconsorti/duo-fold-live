package org.duofold.live;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
/**
 * The angle reader spawns `logcat`. When the Shizuku user-service process is killed (every reinstall,
 * or a crash) that child is re-parented to init and keeps parsing the wallpaper log forever; one was
 * found running for hours on the Fold 7, doubling the parsing load. Reap such orphans before starting.
 * Runs in the service process (uid shell), so it can only signal its own uid's processes.
 */
final class OrphanReaders {
 private OrphanReaders(){}
 static final String TAG_FILTER="SprWallpaper|FoldInteractive";
 static final class Proc {
  final int pid,ppid;final String cmdline;
  Proc(int pid,int ppid,String cmdline){this.pid=pid;this.ppid=ppid;this.cmdline=cmdline;}
 }
 /** Our reader command (logcat ... with our tag filter) whose parent is init, excluding ourselves. */
 static List<Integer> orphans(List<Proc> procs,int self){
  List<Integer> out=new ArrayList<>();
  for(Proc p:procs)if(p.pid!=self&&p.ppid==1&&p.cmdline!=null&&p.cmdline.startsWith("logcat ")&&p.cmdline.contains(TAG_FILTER))out.add(p.pid);
  return out;
 }
 /** Parent pid from /proc/<pid>/stat; comm may contain spaces or ')', so parse after the last ')'. */
 static int ppid(String stat){
  int close=stat==null?-1:stat.lastIndexOf(')');
  if(close<0)return -1;
  String[] f=stat.substring(close+1).trim().split("\\s+");
  try{return f.length>1?Integer.parseInt(f[1]):-1;}catch(NumberFormatException e){return -1;}
 }
 /** Returns how many orphans were signalled. Never throws. */
 static int reap(){
  int n=0;
  try{
   List<Proc> procs=new ArrayList<>();
   File[] dirs=new File("/proc").listFiles();
   if(dirs==null)return 0;
   for(File d:dirs){
    if(!d.getName().matches("\\d+"))continue;
    try{
     String cmd=new String(Files.readAllBytes(new File(d,"cmdline").toPath()),StandardCharsets.UTF_8).replace('\0',' ').trim();
     if(!cmd.startsWith("logcat "))continue;
     String stat=new String(Files.readAllBytes(new File(d,"stat").toPath()),StandardCharsets.UTF_8);
     procs.add(new Proc(Integer.parseInt(d.getName()),ppid(stat),cmd));
    }catch(Exception ignored){}
   }
   for(int pid:orphans(procs,android.os.Process.myPid())){android.os.Process.sendSignal(pid,15);n++;}
  }catch(Exception ignored){}
  return n;
 }
}
