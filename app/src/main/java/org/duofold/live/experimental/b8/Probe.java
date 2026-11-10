package org.duofold.live.experimental.b8;

import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;

/** Shizuku shell worker. Predefined observations and explicit bounded token-owned experiments. */
public class Probe extends Binder {
 private boolean brightnessHoldSelected;
 static final String TOKEN="org.duofold.live.experimental.b8.Probe";
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
  if(code==16777115){abort();System.exit(0);return true;}
  if(code==INTERFACE_TRANSACTION){reply.writeString(TOKEN);return true;}
  data.enforceInterface(TOKEN);
  synchronized(this){int uid=Binder.getCallingUid();if(owner<0)owner=uid;if(owner!=uid)throw new SecurityException("Wrong caller");}
  if(code==1||code==7||code==8||code==9||(code==10||code==11||code==12)){final String recipe=code!=1?data.readString():null;if(code!=1)ExperimentConfig.parse(recipe);final String taskPkg=(code==9||(code==10||code==11||code==12))?data.readString():null;final int options=(code==10||code==11||code==12)?data.readInt():0;RoutePlan.validate(options);if(code==12&&options!=14)throw new IllegalArgumentException("B8 requires protected B7 route");if(code==9||(code==10||code==11||code==12))TaskGuard.validate(taskPkg,ExperimentConfig.parse(recipe));int mode=(code==9||(code==10||code==11||code==12))?17:code==8?15:code==7?14:data.readInt(); if(mode<0||mode>19||(code==1&&mode>13&&mode!=16&&mode!=18&&mode!=19))throw new IllegalArgumentException("Unknown test"); synchronized(this){if(busy)throw new IllegalStateException("Recording already active");busy=true;cancel=false;result=null;status="Preparing";}ShieldBridge.screenshotOnly=code==11;brightnessHoldSelected=code==12;new Thread(()->{if(mode==19)recoverEvidence();else record(mode,recipe,taskPkg,options);},"observer-capture").start();reply.writeNoException();return true;}
  if(code==2){reply.writeNoException();reply.writeString(status);reply.writeLong(result==null?0:result.length());reply.writeInt(busy?1:0);return true;}
  if(code==3){long offset=data.readLong();byte[] bytes=new byte[48*1024];try{if(result==null)throw new IOException("No completed recording");try(RandomAccessFile f=new RandomAccessFile(result,"r")){if(offset<0||offset>f.length())throw new IOException("Invalid offset");f.seek(offset);int n=f.read(bytes);reply.writeNoException();reply.writeByteArray(n<0?new byte[0]:Arrays.copyOf(bytes,n));}}catch(IOException e){reply.writeException(new IllegalStateException(e));}return true;}
  if(code==4){abort();reply.writeNoException();return true;}
  if(code==6){reply.writeNoException();return true;} // Preserve recovery evidence on device.
  return super.onTransact(code,data,reply,flags);
 }
 private void abort(){cancel=true;PowerExperiment e=experiment;if(e!=null)e.close();synchronized(processes){for(java.lang.Process p:processes)if(p!=traceProcess)p.destroy();}}
 static String modeName(int mode){switch(mode){case 19:return "recovery";case 18:return "navigation-implementation";case 17:return "task-route";case 16:return "colorfade-implementation";case 15:return "colorfade-sequence";case 14:return "custom-sequence";case 10:return "concurrent-inner";case 11:return "concurrent-cover";case 12:return "inner-plus-on";case 13:return "cover-plus-on";case 5:return "power-baseline";case 6:return "on-D0";case 7:return "on-D1";case 8:return "on-both";case 9:return "bright-wakelock";case 1:return "recents";case 2:return "notification-shade";case 3:return "quick-settings";case 4:return "duo-compositor";default:return "handoff";}}
 private void recoverEvidence(){
  File dir=null;
  try{
   dir=new File("/data/local/tmp/fold-power-lab-recovery-"+UUID.randomUUID());if(!dir.mkdir())throw new IOException("Cannot create recovery folder");android.system.Os.chmod(dir.getPath(),0700);
   write(dir,"README.txt","Fold Power Lab 0.5.11-private RECOVERY ONLY\nNo display switch, power override, screenshot, sensor subscription, Perfetto session or settings restoration is initiated. Existing evidence is not deleted. Missing/denied reboot records are unknown, not proof of no crash. Archives may contain app names and system crash logs.\n");
   write(dir,"recovery-clock.txt","wallMs="+System.currentTimeMillis()+" elapsedMs="+SystemClock.elapsedRealtime()+"\n");
   status="Preserving unfinished shell recordings";
   RecoveryArchive.pack(new File("/data/local/tmp"),new File(dir,"previous-recordings.zip"),"fold-power-lab-","fold-power-lab-recovery-",400L*1024*1024);
   String[][] commands={
    {"boot-reason.txt","getprop","sys.boot.reason"},{"boot-reason-last.txt","getprop","persist.sys.boot.reason"},{"bootloader-reason.txt","getprop","ro.boot.bootreason"},
    {"boot-id.txt","cat","/proc/sys/kernel/random/boot_id"},{"uptime.txt","cat","/proc/uptime"},
    {"crash-logcat.txt","logcat","-b","crash","-d","-v","epoch"},{"previous-boot-logcat.txt","logcat","-L","-b","all","-d","-v","epoch"},
    {"dropbox-index.txt","dumpsys","dropbox"},{"system-server-watchdog.txt","dumpsys","dropbox","--print","system_server_watchdog"},
    {"system-server-crash.txt","dumpsys","dropbox","--print","system_server_crash"},{"system-server-native-crash.txt","dumpsys","dropbox","--print","system_server_native_crash"},
    {"last-kmsg-dropbox.txt","dumpsys","dropbox","--print","SYSTEM_LAST_KMSG"},{"system-restart.txt","dumpsys","dropbox","--print","SYSTEM_RESTART"},
    {"pstore-list.txt","ls","-la","/sys/fs/pstore"},{"last-kmsg.txt","cat","/proc/last_kmsg"},
    {"lab-exit-info.txt","dumpsys","activity","exit-info","org.duofold.live"}
   };
   for(String[] c:commands){if(cancel)break;status="Reading "+c[0];try{command(dir,c[0],4,null,Arrays.copyOfRange(c,1,c.length));}catch(Exception e){write(dir,c[0],"Unavailable: "+e);}}
   if(!cancel)RecoveryArchive.packFlat(new File("/sys/fs/pstore"),new File(dir,"pstore.zip"),8L*1024*1024);
   status="Packaging recovered evidence";
   RecoveryArchive.packFlat(dir,new File(dir,"report.zip"),500L*1024*1024);
   result=new File(dir,"report.zip");status=(cancel?"Partial recovery":"Recovery complete")+"; originals preserved; no overrides run";
  }catch(Exception e){status="Recovery failed; originals preserved: "+e;}finally{busy=false;}
 }
 private void record(int mode,String recipe,String taskPkg,int options){
  try{AnimatorLease.recover();DesktopLease.recover();}catch(Exception e){status="Animator recovery failed: "+e;busy=false;return;}
  if(mode==16||mode==18){exportImplementation(mode==18);return;}
  final ExperimentConfig cfg=recipe==null?null:ExperimentConfig.parse(recipe);
  final int seconds=cfg==null?10:cfg.duration;
  boolean blurDetail=mode>0&&mode<5; boolean colorfade=mode==15||mode==17; boolean detail=blurDetail||colorfade;
  File dir=null;
  try{
   dir=new File("/data/local/tmp/fold-power-lab-"+UUID.randomUUID());if(!dir.mkdir())throw new IOException("Cannot create diagnostic folder");
   android.system.Os.chmod(dir.getPath(),0700);
   write(dir,"README.txt","Fold Power Lab 0.5.11-private\nMode="+(modeName(mode)+(detail?" DETAIL (higher overhead)":" LIGHT"))+"\nShell UID="+ProcessUid()+"\nSoftware event timestamps are not photon visibility. No screen pixels recorded. Logs may contain app/window names. OEM/private shaders and binder commands are not guaranteed observable. Missing trace sources do not mean absent animation.\nAll capture is opt-in, local, bounded; no internet permission.\nCompare several identical folds with Duo disabled and enabled, same foreground app, orientation, wallpaper and motion. Do not compare detailed tracing timing to light timing.\nApp callback times are elapsedRealtimeNanos; sensor event timestamps use elapsed realtime; logcat uses monotonic (may diverge across sleep); Perfetto includes clock snapshots.\n");
   write(dir,"power-api-inventory.txt",PowerExperiment.inventory());
   if(taskPkg!=null){command(dir,"desktop-policy-setting.txt",2,null,"settings","get","global","force_desktop_mode_on_external_displays");command(dir,"animator-setting-before.txt",2,null,"settings","get","global","animator_duration_scale");command(dir,"window-policy-before.txt",3,null,"dumpsys","window","policy");}
   if(cfg!=null)write(dir,"recipe.txt",cfg.describe());
   if(taskPkg!=null){
    command(dir,"content-mirror-setting.txt",2,null,"settings","get","secure","mirror_built_in_display");
    command(dir,"overlay-inventory.txt",3,null,"cmd","overlay","list","--user","current");
    command(dir,"statusbar-command-help.txt",2,null,"cmd","statusbar","help");
    write(dir,"AUDIT-README.txt","0.5.11: Test D is routeOptions6 (unchanged A2), NOT a new override. navigation-policy.txt records raw DisplayInfo fields and public flag masks. Sampled display dumps retain ColorFade state, task-host eligibility and power policy; SystemUI dumps retain navigation caches. Commands include actual begin/end times; nominal sample labels are targets, not simultaneous observations. Missing fields or permission denials mean unknown. No ColorFade suppression is implemented. Native-primary suppression remains an open research alternative; disabling animation duration did not remove the observed flash.\n");
   }
   if(taskPkg!=null)write(dir,"shield-backend.txt",(ShieldBridge.screenshotOnly?"SCREENSHOT_ONLY comparison":"MIRROR_BASELINE original 0.5.3 LiveWindowBridge SHA256 ac904fb4909aa3493567cd809d8ca1b43e8b597d05bab807b5d1212c3c8188d4")+"\n");
   if(taskPkg!=null)write(dir,"selected-app.txt","routeOptions="+options+" (1=focus reassertion, 2=outgoing panel OFF, 4=primary promotion baseline, 5=primary promotion with animator scale zero, 6=direct power OFF, 7=temporary desktop policy, 8=desktop policy plus direct OFF, 9=live window bridge baseline, 10=live window bridge plus native promotion, 11=B4 staged mirror removal, 12=B5 staged removal plus delayed focus, 13=B6 frozen shield + geometry gate, 14=B7 frozen shield + verified native release at24s)\n"+taskPkg+"\nExact fullscreen app root only. "+(RoutePlan.promotes(options)?"TEST B: move at +6s, promote destination primary at +10s, retarget exact task if needed; no +16s return. Native cleanup at +30s.":"TEST A/baseline: fixed mapping, move at +6s, return at +16s.")+" No task close or force-stop. B6/B7 in0.5.11 use static captures of window mirrors during handoff; moving content intentionally pauses. Geometry/readiness are software evidence, not fresh-frame or pixel proof. Native return only verifies known Samsung base0=CLOSED or3=OPENED against physical identity; other base states time out. Independent bridge watchdog still removes protection.\n");
   command(dir,"sensorservice-before.txt",3,null,"dumpsys","sensorservice");
   command(dir,"capabilities.txt",6,null,"perfetto","--query");
   if(blurDetail){
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
   String config=TracePlan.config(seconds,detail,colorfade);
   write(dir,"trace-config.txt",config);
   write(dir,"capture-policy.txt",detail?"Streaming detailed layers every 250 ms; separate 32 MiB layer and 8 MiB timing buffers; ColorFade mode omits HWC/buffer detail and the unavailable transaction source. Perfetto limit 192 MiB. Source coverage and buffer-loss counters must still be checked after recording.":"Light capture: 8 MiB ring buffer, no detailed layer tracing. Includes 25 ms target shell power sampling; account for this overhead.");
   final File out=dir;
   Thread log=new Thread(()->{try{command(out,"events-logcat.txt",seconds+2,null,"logcat","-v","monotonic","-T","1","WindowManager:I","DisplayManagerService:V","DeviceStateManagerService:V",detail?"SurfaceFlinger:D":"SurfaceFlinger:I","ShellTransitions:V","NotificationShadeDepthController:D","BlurUtils:D","SecQpBlurController:D","CapturedBlurContainerController:D","SecPanelBackgroundController:D","DisplayPowerController:V","DisplayPowerState:V","LogicalDisplayMapper:V","LocalDisplayAdapter:V","PowerManagerService:V","ActivityTaskManager:I","*:S");}catch(Exception e){try{write(out,"logcat-error.txt",e.toString());}catch(Exception ignored){}}});
   write(dir,"recording-start.txt","elapsedRealtimeNanos="+SystemClock.elapsedRealtimeNanos()+" wallMs="+System.currentTimeMillis());
   status="RECORDING: "+modeName(mode)+" — "+seconds+" seconds. "+(cfg!=null&&cfg.alternate?"Hold hinge near 90 degrees; switching is automatic.":"Perform the gestures now.");
   log.start();
   experiment=new PowerExperiment(out,label->{try{command(out,label+"-device-state.txt",2,null,"dumpsys","device_state");if(!cancel)command(out,label+"-display.txt",2,null,"dumpsys","display");}catch(Exception e){try{write(out,label+"-snapshot-error.txt",e.toString());}catch(Exception ignored){}}});
   experiment.routeOptions=options;experiment.brightnessHoldSelected=brightnessHoldSelected;
   write(dir,"brightness-mode.txt",brightnessHoldSelected?"B8: B7 mirror plus 2s physical brightness reassertion at forward and return; see physical-brightness-hold.txt":"Original test: no physical brightness writes");
   if(taskPkg!=null)experiment.taskMover=new TaskMover(out,taskPkg,options,experiment);
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
   Thread navigation=new Thread(()->{try{
    for(int at:(BridgePlan.stagedReveal(options)?new int[]{4,12,15,20,26}:new int[]{4,10,20,26})){
     while(!cancel&&SystemClock.elapsedRealtime()-start<at*1000L)Thread.sleep(50);
     if(cancel)break;
     if(BridgePlan.stagedReveal(options)&&(at==12||at==15)){
      command(out,"content-windows-"+at+"s.txt",2,null,"dumpsys","window","windows");
      if(!cancel)command(out,"content-surfaces-"+at+"s.txt",2,null,"dumpsys","SurfaceFlinger");
      continue;
     }
     NavigationProbe.sample(out,"scheduled audit target="+at+"s actualAgeMs="+(SystemClock.elapsedRealtime()-start));
     command(out,"navigation-window-"+at+"s.txt",2,null,"dumpsys","window","displays");
     if(!cancel)command(out,"navigation-systemui-"+at+"s.txt",2,null,"dumpsys","activity","service","com.android.systemui/.SystemUIService");
     if(!cancel)command(out,"navigation-input-"+at+"s.txt",2,null,"dumpsys","input");
     if(!cancel)command(out,"display-colorfade-"+at+"s.txt",2,null,"dumpsys","display");
    }
   }catch(Exception e){try{write(out,"navigation-error.txt",e.toString());}catch(Exception ignored){}}});
   if(taskPkg!=null)navigation.start();
   if(blurDetail)samples.start();
   command(dir,"perfetto-status.txt",seconds+5,config,"perfetto","--txt","-c","-","-o","-");
   // Preserve a full observation window even when Samsung rejects the requested trace.
   while(!cancel&&SystemClock.elapsedRealtime()-start<seconds*1000L)Thread.sleep(100);
   log.join(4000);
   if(blurDetail){samples.join(6000);if(samples.isAlive())throw new IOException("Blur sampler did not finish");}
   experiment.close();
   experiment.awaitSampler();
   if(taskPkg!=null){navigation.join(12000);if(navigation.isAlive())throw new IOException("Navigation sampler still running");}
   status="Finishing report";
   write(dir,"recording-end.txt","elapsedRealtimeNanos="+SystemClock.elapsedRealtimeNanos()+" cancelled="+cancel+" traceBytes="+(trace.exists()?trace.length():0));
   write(dir,"trace-coverage.txt",TraceCoverage.inspect(trace));
   File brightnessLog=new File(dir,"events-logcat.txt");if(brightnessLog.isFile())write(dir,"brightness-command-summary.txt",BrightnessSamples.summary(new String(java.nio.file.Files.readAllBytes(brightnessLog.toPath()),java.nio.charset.StandardCharsets.UTF_8)));
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
 private void exportImplementation(boolean navigationOnly){
  File dir=null;
  try{
   dir=new File("/data/local/tmp/fold-power-lab-"+UUID.randomUUID());if(!dir.mkdir())throw new IOException("Cannot create export folder");android.system.Os.chmod(dir.getPath(),0700);
   status="Reading Samsung implementation — no display changes";
   write(dir,"README.txt","Fold Power Lab Private 0.5.11 implementation export. Read-only fixed system files and command help. No device-state request, settings mutation or display switch. Firmware binaries can reveal Samsung's exact ColorFade path; presence does not imply an accessible override. Missing/stripped dex may require corresponding compiled artifacts. SystemUI-only export is limited to resolved system-partition APKs. No user app data collected.\n");
   write(dir,"device.txt","model="+Build.MODEL+"\nfingerprint="+Build.FINGERPRINT+"\nsdk="+Build.VERSION.SDK_INT+"\n");
   write(dir,"power-api-inventory.txt",PowerExperiment.inventory());
   {command(dir,"desktop-policy-setting.txt",2,null,"settings","get","global","force_desktop_mode_on_external_displays");command(dir,"animator-setting-before.txt",2,null,"settings","get","global","animator_duration_scale");command(dir,"window-policy-before.txt",3,null,"dumpsys","window","policy");}
   command(dir,"display-help.txt",4,null,"cmd","display","help");
   command(dir,"power-help.txt",4,null,"cmd","power","help");
   command(dir,"window-help.txt",4,null,"cmd","window","help");
   command(dir,"overlay-list.txt",4,null,"cmd","overlay","list","--user","0");
   command(dir,"colorfade-enabled-resource.txt",4,null,"cmd","overlay","lookup","--verbose","android","android:bool/config_colorFadeEnabled");
   command(dir,"colorfade-fades-resource.txt",4,null,"cmd","overlay","lookup","--verbose","android","android:bool/config_colorFadeFades");
   command(dir,"display-state.txt",4,null,"dumpsys","display");
   command(dir,"framework-list.txt",4,null,"ls","-l","/system/framework","/system/framework/oat/arm64","/system_ext/framework");
   final String[] corePaths={"/system/framework/services.jar","/system/framework/framework.jar","/system/framework/framework-res.apk","/system/framework/oat/arm64/services.odex","/system/framework/oat/arm64/services.vdex"};
   java.util.ArrayList<String> paths=new java.util.ArrayList<>();
   if(navigationOnly){
    command(dir,"systemui-package.txt",4,null,"pm","path","com.android.systemui");
    command(dir,"systemui-service.txt",5,null,"dumpsys","activity","service","com.android.systemui/.SystemUIService");
    command(dir,"window-displays.txt",4,null,"dumpsys","window","displays");
    for(String line:new String(java.nio.file.Files.readAllBytes(new File(dir,"systemui-package.txt").toPath()),StandardCharsets.UTF_8).split("\\n")){
     if(!line.startsWith("package:"))continue;String path=line.substring(8).trim();
     if((path.startsWith("/system/")||path.startsWith("/system_ext/")||path.startsWith("/product/"))&&path.endsWith(".apk")&&!path.contains(".."))paths.add(path);
    }
   }else java.util.Collections.addAll(paths,corePaths);
   StringBuilder manifest=new StringBuilder();long total=0;
   for(String path:paths){
    if(cancel)break;
    File source=new File(path);String dest=path.substring(1).replace('/','_');
    if(!source.isFile()||!source.canRead()){manifest.append(path).append(" UNAVAILABLE\n");continue;}
    long length=source.length();if(length>(navigationOnly?384L:128L)*1024*1024||total+length>(navigationOnly?512L:192L)*1024*1024){manifest.append(path).append(" SKIPPED size budget bytes=").append(length).append("\n");continue;}
    status="Copying "+source.getName();File target=new File(dir,dest);long copied=0;java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
    try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(target)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>=0){if(cancel)throw new IOException("Cancelled");copied+=n;if(copied>length||total+copied>(navigationOnly?512L:192L)*1024*1024)throw new IOException("File exceeded export bound");out.write(buffer,0,n);hash.update(buffer,0,n);}}
    catch(IOException e){target.delete();manifest.append(path).append(" FAILED ").append(e).append("\n");continue;}
    total+=copied;StringBuilder hex=new StringBuilder();for(byte v:hash.digest())hex.append(String.format(Locale.ROOT,"%02x",v&255));
    manifest.append(path).append(" -> ").append(dest).append(" bytes=").append(copied).append(" sha256=").append(hex).append("\n");
   }
   write(dir,"export-manifest.txt",manifest.toString());
   status="Packaging implementation export";File zip=new File(dir,"report.zip");
   try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){for(File f:dir.listFiles()){if(!f.isFile()||f.equals(zip))continue;out.putNextEntry(new ZipEntry(f.getName()));try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))>=0)out.write(b,0,n);}out.closeEntry();}}
   result=zip;status=(cancel?"Cancelled — partial":"Complete")+"; implementation export, copied "+total+" bytes; see manifest for omissions";
   for(File f:dir.listFiles())if(!f.equals(zip))f.delete();
  }catch(Exception e){status="Export failed: "+e;if(dir!=null)delete(dir);}finally{busy=false;}
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
