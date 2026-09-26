package org.duofold.live.wallpaperlayer;
import android.app.*;
import android.graphics.*;
import android.os.*;
import android.content.*;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Android-managed process: window sessions have a real process record. */
public class PhotoInstrumentation extends Instrumentation {
 public static volatile boolean running;
 LayerHost host; UnlockTrace unlockTrace; UiAutomation automation; BroadcastReceiver screenEvents;
 final java.util.ArrayDeque<String> transitionHistory=new java.util.ArrayDeque<>();
 final java.util.ArrayDeque<String> windowSnapshots=new java.util.ArrayDeque<>();
 final java.util.concurrent.ExecutorService traceWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
 volatile boolean captureBusy;long lastCapture;long reportNumber;
 synchronized void transition(String text){
  String event=SystemClock.elapsedRealtime()+" "+text;
  if(unlockTrace!=null)unlockTrace.event(text);
  if(transitionHistory.size()>=80)transitionHistory.removeFirst();transitionHistory.addLast(event);
  if(text.contains("SCREEN_OFF")||UnlockTrace.capturing())return;
  if(traceWorker.isShutdown()||automation==null||captureBusy||SystemClock.elapsedRealtime()-lastCapture<150)return;
  captureBusy=true;lastCapture=SystemClock.elapsedRealtime();
  traceWorker.execute(()->{String snapshot;try{
   ParcelFileDescriptor fd=automation.executeShellCommand("dumpsys -t 2 window windows");
   try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){snapshot=WindowTraceFilter.read(new InputStreamReader(in,StandardCharsets.UTF_8));}
  }catch(Throwable e){snapshot="Window snapshot failed: "+e;}
  synchronized(PhotoInstrumentation.this){if(windowSnapshots.size()>=4)windowSnapshots.removeFirst();windowSnapshots.addLast("Triggered by "+event+"\n"+snapshot);captureBusy=false;}
  });
 }
 synchronized String traceReport(){return "\nTransition events (elapsed ms):\n"+String.join("\n",transitionHistory)+"\nWindow snapshots:\n"+(windowSnapshots.isEmpty()?"No completed snapshot yet":String.join("\n---\n",windowSnapshots));}
 volatile String lastReport="Starting registered wallpaper host";
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){
  File stop=new File(getTargetContext().getFilesDir(),"stop-layer");
  if(!WallpaperRestore.enabled(getTargetContext())||stop.exists()){finish(0,new Bundle());return;}
  running=true;
  try {
   // No-restart instrumentation preserves the app process. Restrict exemptions
   // to the same framework packages used by the existing wallpaper renderer.
   if(!org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/", "Landroid/window/", "Landroid/app/", "Landroid/os/", "Landroid/accessibilityservice/IAccessibilityServiceConnection;"))
    throw new IllegalStateException("Wallpaper framework access unavailable; fold animation remains running");
   // Do not suspend Duo Fold Live's accessibility service during this experiment.
   automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
   if(automation==null)throw new IllegalStateException("UiAutomation unavailable");
   automation.adoptShellPermissionIdentity("android.permission.INTERNAL_SYSTEM_WINDOW","android.permission.MANAGE_ACTIVITY_TASKS","android.permission.ACCESS_SURFACE_FLINGER");
   unlockTrace=new UnlockTrace(getTargetContext(),automation);
   publish("Registered photo host started; shell window permission adopted");
   screenEvents=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){if(host!=null)host.screenEvent(intent.getAction());else transition("Screen event "+intent.getAction());}};
   IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);
   // These three actions are protected system broadcasts. USER_PRESENT may be sent
   // by SystemUI, which a NOT_EXPORTED receiver cannot receive on some firmware.
   getTargetContext().registerReceiver(screenEvents,filter,Context.RECEIVER_EXPORTED);
   runOnMainSync(()->{
    host=new LayerHost(getTargetContext());host.transitionSink=this::transition;
    try {host.start(ParcelFileDescriptor.open(new File(getTargetContext().getFilesDir(),"photo.jpg"),ParcelFileDescriptor.MODE_READ_ONLY));}
    catch(Exception e){host.recordFailure(e);host.stop("Photo startup failed: "+e);}
   });
   if(!new File(getTargetContext().getFilesDir(),"legacy-renderer").exists()&&host.running){
    try{
     String hierarchy;
     try(InputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys -t 3 window displays"))){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] data=new byte[8192];int n;while((n=stream.read(data))!=-1){if(bytes.size()+n>4000000)throw new IOException("Display hierarchy exceeds bound");bytes.write(data,0,n);}hierarchy=bytes.toString("UTF-8");}
     String check=DisplayAreaGuard.check(hierarchy);
     runOnMainSync(()->{host.journal.phase(check);host.activateNative();});
    }catch(Throwable e){final String problem=e.toString();runOnMainSync(()->{host.nativeStatus="Legacy fallback: native preflight failed: "+problem;host.journal.phase(host.nativeStatus);});}
   }
   runOnMainSync(()->{
    if(!host.running)return;
    try{
     android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();
     info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
     automation.setServiceInfo(info);
     host.homePhoto=new HomePhotoLayer(getTargetContext(),automation,host.main,host::trace,host.bitmap,display->host.nativePhoto==null?null:host.nativePhoto.photoParent(display));
     automation.setOnAccessibilityEventListener(event->{
      if(event.getEventType()==android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||(event.getEventType()==android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED&&(event.getWindowChanges()&(android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ADDED|android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_REMOVED))!=0)){
       host.main.post(()->{if(host.homePhoto!=null)host.homePhoto.windowChanged();});
      }
     });
     host.homePhoto.requestRefresh();
    }catch(Throwable e){host.trace("Home transition photo unavailable; existing wallpaper retained: "+LayerHost.reason(e));}
   });
   File photo=new File(getTargetContext().getFilesDir(),"photo.jpg");
   long photoVersion=photo.lastModified(),nextReport=0;
   while(true){
    if(!host.running || SystemClock.elapsedRealtime()>=nextReport){
     runOnMainSync(()->lastReport=host.report());publish(lastReport);nextReport=SystemClock.elapsedRealtime()+3000;
    }
    if(!host.running)break;
    long changed=photo.lastModified();
    if(changed!=photoVersion){
     // Decode away from the UI thread; publish only a complete, valid replacement.
     Bitmap replacement=BitmapFactory.decodeFile(photo.getAbsolutePath());
     if(replacement!=null){runOnMainSync(()->{host.updatePhoto(replacement);host.journal.phase("Custom photo updated");});photoVersion=changed;}
    }
    if(stop.exists()||!WallpaperRestore.enabled(getTargetContext())){
     runOnMainSync(()->host.stop("Custom wallpaper disabled; original wallpaper unchanged"));
     continue;
    }
    HomePhotoLayer homeLayer=host.homePhoto;if(homeLayer!=null)homeLayer.requestRefresh();
    Thread.sleep(500);
   }
  }catch(Throwable e){StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));publish("Duo Wallpaper Layer 0.13\nRegistered host failed:\n"+trace);}
  finally{
   if(automation!=null)try{automation.setOnAccessibilityEventListener(null);}catch(Exception ignored){}
   if(unlockTrace!=null)unlockTrace.close();
   traceWorker.shutdown();
   if(screenEvents!=null)try{getTargetContext().unregisterReceiver(screenEvents);}catch(Exception ignored){}
   try {if(host!=null)runOnMainSync(()->host.stop("Registered host finished; layer removed"));}catch(Throwable ignored){}
   if(automation!=null)try{automation.dropShellPermissionIdentity();}catch(Throwable ignored){}
   running=false;finish(0,new Bundle());
  }
 }
 void publish(String report){
  report="Report sequence: "+(++reportNumber)+"; captured elapsed ms: "+SystemClock.elapsedRealtime()+"\n"+report+"\nUnlock capture: "+UnlockTrace.status()+traceReport();
  lastReport=report;
  // Persistent app-private report survives instrumentation finishing and process restarts.
  try {
   File dir=getTargetContext().getFilesDir(),tmp=new File(dir,"layer-report.tmp");
   Files.write(tmp.toPath(),report.getBytes(StandardCharsets.UTF_8));
   Files.move(tmp.toPath(),new File(dir,"layer-report.txt").toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
  }catch(Exception ignored){}
  Bundle out=new Bundle();out.putString("duo_report",Base64.encodeToString(report.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP));sendStatus(0,out);
 }
}
