package org.duofold.powerlab;

import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;

/** Shizuku shell worker. Predefined observations and explicit bounded token-owned experiments. */
public class Probe extends Binder {
 static final String TOKEN="org.duofold.powerlab.Probe";
 private int owner=-1;
 private volatile String status="Idle";
 private volatile boolean busy=false,cancel=false;
 private volatile java.lang.Process traceProcess;
 private volatile File result;
 private volatile PowerExperiment experiment;
 private final List<java.lang.Process> processes=Collections.synchronizedList(new ArrayList<>());
 public Probe() {}
 public Probe(android.content.Context unused) {}
 @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(code==16777115){abort();if(result!=null)delete(result.getParentFile());System.exit(0);return true;}
  if(code==INTERFACE_TRANSACTION){reply.writeString(TOKEN);return true;}
  data.enforceInterface(TOKEN);
  synchronized(this){int uid=Binder.getCallingUid();if(owner<0)owner=uid;if(owner!=uid)throw new SecurityException("Wrong caller");}
  if(code==1||code==7){final String recipe=code==7?data.readString():null;if(code==7)ExperimentConfig.parse(recipe);int mode=code==7?14:data.readInt(); if(mode<0||mode>14)throw new IllegalArgumentException("Unknown test"); synchronized(this){if(busy)throw new IllegalStateException("Recording already active");busy=true;cancel=false;if(result!=null)delete(result.getParentFile());result=null;status="Preparing";}new Thread(()->record(mode,recipe),"observer-capture").start();reply.writeNoException();return true;}
  if(code==2){reply.writeNoException();reply.writeString(status);reply.writeLong(result==null?0:result.length());reply.writeInt(busy?1:0);return true;}
  if(code==3){long offset=data.readLong();byte[] bytes=new byte[48*1024];try{if(result==null)throw new IOException("No completed recording");try(RandomAccessFile f=new RandomAccessFile(result,"r")){if(offset<0||offset>f.length())throw new IOException("Invalid offset");f.seek(offset);int n=f.read(bytes);reply.writeNoException();reply.writeByteArray(n<0?new byte[0]:Arrays.copyOf(bytes,n));}}catch(IOException e){reply.writeException(new IllegalStateException(e));}return true;}
  if(code==4){abort();reply.writeNoException();return true;}
  if(code==6){if(!busy&&result!=null){delete(result.getParentFile());result=null;}reply.writeNoException();return true;}
  return super.onTransact(code,data,reply,flags);
 }
 private void abort(){cancel=true;PowerExperiment e=experiment;if(e!=null)e.close();synchronized(processes){for(java.lang.Process p:processes)if(p!=traceProcess)p.destroy();}}
 static String modeName(int mode){switch(mode){case 14:return "custom-sequence";case 10:return "concurrent-inner";case 11:return "concurrent-cover";case 12:return "inner-plus-on";case 13:return "cover-plus-on";case 5:return "power-baseline";case 6:return "on-D0";case 7:return "on-D1";case 8:return "on-both";case 9:return "bright-wakelock";case 1:return "recents";case 2:return "notification-shade";case 3:return "quick-settings";case 4:return "duo-compositor";default:return "handoff";}}
 private void record(int mode,String recipe){
  final ExperimentConfig cfg=recipe==null?null:ExperimentConfig.parse(recipe);
  final int seconds=cfg==null?10:cfg.duration;
  boolean detail=mode>0&&mode<5;
  File dir=null;
  try{
   dir=new File("/data/local/tmp/fold-power-lab-"+UUID.randomUUID());if(!dir.mkdir())throw new IOException("Cannot create diagnostic folder");
   android.system.Os.chmod(dir.getPath(),0700);
   write(dir,"README.txt","Fold Power Lab 0.3.0\nMode="+(modeName(mode)+(detail?" DETAIL (higher overhead)":" LIGHT"))+"\nShell UID="+ProcessUid()+"\nSoftware event timestamps are not photon visibility. No screen pixels recorded. Logs may contain app/window names. OEM/private shaders and binder commands are not guaranteed observable. Missing trace sources do not mean absent animation.\nAll capture is opt-in, local, bounded; no internet permission.\nCompare several identical folds with Duo disabled and enabled, same foreground app, orientation, wallpaper and motion. Do not compare detailed tracing timing to light timing.\nApp callback times are elapsedRealtimeNanos; sensor event timestamps use elapsed realtime; logcat uses monotonic (may diverge across sleep); Perfetto includes clock snapshots.\n");
   write(dir,"power-api-inventory.txt",PowerExperiment.inventory());
   if(cfg!=null)write(dir,"recipe.txt",cfg.describe());
   command(dir,"sensorservice-before.txt",3,null,"dumpsys","sensorservice");
   command(dir,"capabilities.txt",6,null,"perfetto","--query");
   if(detail){
    write(dir,"blur-api-inventory.txt",blurApiInventory());
    command(dir,"blur-disabled-setting.txt",2,null,"settings","get","global","disable_window_blurs");
    command(dir,"reduce-transparency-setting.txt",2,null,"settings","get","system","accessibility_reduce_transparency");
   }
   command(dir,"display-before.txt",4,null,"dumpsys","display");
   command(dir,"device-state-before.txt",3,null,"dumpsys","device_state");
   command(dir,"power-before.txt",3,null,"dumpsys","power");
   command(dir,"windows-before.txt",4,null,"dumpsys","window","windows");
   command(dir,"surface-before.txt",4,null,"dumpsys","SurfaceFlinger");
   if(cancel)throw new IOException("Cancelled during preparation");
   File trace=new File(dir,"capture.perfetto-trace");
   String config="buffers { size_kb: 8192 fill_policy: RING_BUFFER }\n"+
    (detail?"buffers { size_kb: 32768 fill_policy: RING_BUFFER }\nwrite_into_file: true\nfile_write_period_ms: 250\nmax_file_size_bytes: 201326592\n":"")+
    "duration_ms: "+(seconds*1000)+"\n"+
    "data_sources { config { name: \"android.surfaceflinger.frametimeline\" } }\n"+
    "data_sources { config { name: \"com.android.wm.shell.transition\" } }\n";
   if(detail)config+="data_sources { config { name: \"android.surfaceflinger.layers\" target_buffer: 1 surfaceflinger_layers_config { mode: MODE_ACTIVE trace_flags: TRACE_FLAG_COMPOSITION } } }\n";
   write(dir,"trace-config.txt",config);
   write(dir,"capture-policy.txt",detail?"Streaming detailed layers every 250 ms; separate 32 MiB layer and 8 MiB timing buffers. Perfetto limit 192 MiB. Source coverage and buffer-loss counters must still be checked after recording.":"Light capture: 8 MiB ring buffer, no detailed layer tracing. Includes 25 ms target shell power sampling; account for this overhead.");
   final File out=dir;
   Thread log=new Thread(()->{try{command(out,"events-logcat.txt",seconds+2,null,"logcat","-v","monotonic","-T","1","WindowManager:I","DisplayManagerService:V","DeviceStateManagerService:V",detail?"SurfaceFlinger:D":"SurfaceFlinger:I","ShellTransitions:V","NotificationShadeDepthController:D","BlurUtils:D","SecQpBlurController:D","CapturedBlurContainerController:D","SecPanelBackgroundController:D","DisplayPowerController:V","DisplayPowerState:V","LogicalDisplayMapper:V","LocalDisplayAdapter:V","PowerManagerService:V","ActivityTaskManager:I","*:S");}catch(Exception e){try{write(out,"logcat-error.txt",e.toString());}catch(Exception ignored){}}});
   write(dir,"recording-start.txt","elapsedRealtimeNanos="+SystemClock.elapsedRealtimeNanos()+" wallMs="+System.currentTimeMillis());
   status="RECORDING: "+modeName(mode)+" — "+seconds+" seconds. "+(cfg!=null&&cfg.alternate?"Hold hinge near 90 degrees; switching is automatic.":"Perform the gestures now.");
   log.start();
   experiment=new PowerExperiment(out,label->{try{command(out,label+"-device-state.txt",2,null,"dumpsys","device_state");if(!cancel)command(out,label+"-display.txt",2,null,"dumpsys","display");}catch(Exception e){try{write(out,label+"-snapshot-error.txt",e.toString());}catch(Exception ignored){}}});
   if(cfg==null)experiment.start(mode);else experiment.start(cfg);
   long start=SystemClock.elapsedRealtime();
   // Three sparse observations complement the moving-layer trace. Actual intervals
   // are recorded; dumps may add overhead and can miss intermediate animation.
   Thread samples=new Thread(()->{try{
    for(int at:new int[]{2,5,8}){
     while(!cancel&&SystemClock.elapsedRealtime()-start<at*1000)Thread.sleep(50);
     if(cancel||SystemClock.elapsedRealtime()-start>=10000)break;
     command(out,"surface-during-"+at+"s.txt",2,null,"dumpsys","SurfaceFlinger");
     if(!cancel&&SystemClock.elapsedRealtime()-start<seconds*1000L)
      command(out,"systemui-blur-"+at+"s.txt",2,null,"dumpsys","activity","service","com.android.systemui/.SystemUIService");
    }
   }catch(Exception e){try{write(out,"blur-samples-error.txt",e.toString());}catch(Exception ignored){}}});
   if(detail)samples.start();
   command(dir,"perfetto-status.txt",seconds+5,config,"perfetto","--txt","-c","-","-o","-");
   // Preserve a full observation window even when Samsung rejects the requested trace.
   while(!cancel&&SystemClock.elapsedRealtime()-start<seconds*1000L)Thread.sleep(100);
   log.join(4000);
   if(detail){samples.join(6000);if(samples.isAlive())throw new IOException("Blur sampler did not finish");}
   experiment.close();
   experiment.awaitSampler();
   status="Finishing report";
   write(dir,"recording-end.txt","elapsedRealtimeNanos="+SystemClock.elapsedRealtimeNanos()+" cancelled="+cancel+" traceBytes="+(trace.exists()?trace.length():0));
   command(dir,"power-after.txt",3,null,"dumpsys","power");
   command(dir,"display-after.txt",4,null,"dumpsys","display");
   command(dir,"device-state-after.txt",3,null,"dumpsys","device_state");
   command(dir,"windows-after.txt",4,null,"dumpsys","window","windows");
   command(dir,"surface-after.txt",4,null,"dumpsys","SurfaceFlinger");
   File zip=new File(dir,"report.zip");
   try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(zip))){for(File f:dir.listFiles()){if(f.equals(zip)||!f.isFile())continue;z.putNextEntry(new ZipEntry(f.getName()));try(InputStream in=new FileInputStream(f)){byte[] b=new byte[16384];int n;while((n=in.read(b))>=0)z.write(b,0,n);}z.closeEntry();}}
   result=zip;status=(new File(dir,"trace-limit.txt").exists()?"Trace limit reached — incomplete report":cancel?"Cancelled — partial report":"Complete")+(trace.length()>0?"; trace bytes saved (source coverage unverified)":"; WARNING: no Perfetto trace saved");
   // Original text/trace files are now in the ZIP; remove duplicate bytes.
   for(File f:dir.listFiles())if(!f.equals(zip))f.delete();
  }catch(Exception e){status="Failed: "+e.getClass().getSimpleName()+": "+e.getMessage();if(dir!=null)delete(dir);}
  finally{if(experiment!=null)experiment.close();experiment=null;busy=false;}
 }
 private static String blurApiInventory(){
  StringBuilder out=new StringBuilder("Read-only runtime API discovery; declaration visibility does not prove invocation permission or actual SystemUI use. No hidden API exemptions applied.\n");
  for(String name:new String[]{"android.view.SemBlurInfo","android.view.SemBlurInfo$Builder","com.samsung.android.graphics.SemGfxImageFilter","android.view.View","android.view.SurfaceControl$Transaction","android.view.Window","android.view.WindowManager"}){
   out.append("\nCLASS ").append(name).append("\n");
   try{Class<?> c=Class.forName(name);for(java.lang.reflect.Method m:c.getDeclaredMethods())if(m.getName().toLowerCase(Locale.ROOT).contains("blur")||name.contains("SemBlurInfo")||name.contains("SemGfxImageFilter"))out.append(m.toString()).append("\n");
    if(name.contains("SemBlurInfo"))for(java.lang.reflect.Field f:c.getDeclaredFields())out.append(f.toString()).append("\n");
   }catch(Throwable e){out.append("Unavailable: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");}
  }return out.toString();
 }
 private static int ProcessUid(){return android.os.Process.myUid();}
 private static void write(File d,String name,String value)throws IOException{try(FileOutputStream o=new FileOutputStream(new File(d,name))){o.write(value.getBytes(StandardCharsets.UTF_8));}}
 private void command(File dir,String name,int seconds,String input,String... argv)throws Exception{
  File output=new File(dir,name);long begin=SystemClock.elapsedRealtimeNanos();
  java.lang.Process p=new ProcessBuilder(argv).redirectErrorStream(!name.equals("perfetto-status.txt")).start();processes.add(p);if(name.equals("perfetto-status.txt"))traceProcess=p;
  Thread drain=new Thread(()->{try(InputStream in=name.equals("perfetto-status.txt")?p.getErrorStream():p.getInputStream();OutputStream o=new FileOutputStream(output)){if(name.startsWith("systemui-blur-")){
     BufferedReader lines=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));String line;int kept=0;
     o.write("Filtered blur/shade state only; missing fields do not establish absence of blur.\n".getBytes(StandardCharsets.UTF_8));
     while((line=lines.readLine())!=null){String l=line.toLowerCase(Locale.ROOT);
      if(l.matches(".*(blur|scrim|shadeexpansion|panelexpansion|qs_expansion|qsexpansion|mexpandedfraction|permission denial|error|exception|not found).*")){
       byte[] bytes=(line+"\n").getBytes(StandardCharsets.UTF_8);if(kept+bytes.length>1024*1024){o.write("[TRUNCATED]\n".getBytes(StandardCharsets.UTF_8));break;}o.write(bytes);kept+=bytes.length;
      }
     }return;
    }
    byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))>=0){int keep=Math.min(n,Math.max(0,4*1024*1024-total));if(keep>0)o.write(b,0,keep);total+=n;if(total>4*1024*1024){o.write("\n[TRUNCATED at 4 MiB]\n".getBytes(StandardCharsets.UTF_8));break;}}}catch(IOException ignored){}});
  Thread binary=null;
  if(name.equals("perfetto-status.txt")){binary=new Thread(()->{try(InputStream in=p.getInputStream();OutputStream out=new FileOutputStream(new File(dir,"capture.perfetto-trace"))){byte[] b=new byte[16384];int n;long total=0;while((n=in.read(b))>=0){total+=n;if(total>208L*1024*1024){write(dir,"trace-limit.txt","Trace stream exceeded 208 MiB; recording is incomplete.");p.destroy();break;}out.write(b,0,n);}}catch(IOException ignored){}});binary.start();}
  drain.start();
  try{try(OutputStream stdin=p.getOutputStream()){if(input!=null)stdin.write(input.getBytes(StandardCharsets.UTF_8));}
   boolean done=p.waitFor(seconds,TimeUnit.SECONDS);if(!done){p.destroy();if(!p.waitFor(1,TimeUnit.SECONDS))p.destroyForcibly();}drain.join(2000);if(binary!=null)binary.join(2000);
   synchronized(this){try(FileOutputStream o=new FileOutputStream(new File(dir,"commands.txt"),true)){o.write((Arrays.toString(argv)+" beginNs="+begin+" endNs="+SystemClock.elapsedRealtimeNanos()+" exit="+(p.isAlive()?"running":p.exitValue())+" timeout="+!done+"\n").getBytes(StandardCharsets.UTF_8));}}
  }finally{processes.remove(p);if(traceProcess==p)traceProcess=null;if(p.isAlive())p.destroyForcibly();}
 }
 private static void delete(File d){if(d.isDirectory())for(File f:d.listFiles())delete(f);d.delete();}
}
