package org.duofold.live;
import android.os.*;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.view.SurfaceControl;
import java.io.*;
/** Shell-side display capture with exclusions and selected-task guards. Never requests secure/protected capture. */
final class GlassCapture extends Binder {
 static final String TOKEN="org.duofold.live.capture.GlassCapture";
 private final int owner;private volatile boolean closed;private DisplayCaptureApi captureApi;
 private final Object settingsLock=new Object();
 private synchronized DisplayCaptureApi captureApi(Class<?> wa)throws Exception{
  if(captureApi==null)captureApi=DisplayCaptureApi.resolve(Class::forName,wa,Rect.class,SurfaceControl[].class);
  return captureApi;
 }
 GlassCapture(int uid){owner=uid;}
 synchronized void close(){closed=true;}
 private static boolean matchesCommittedPrimary(Object info,String physical,int width,int height,int rotation) throws ReflectiveOperationException {
  if (info==null) return false;
  Class<?> type=info.getClass();
  return java.util.Objects.equals(physical,String.valueOf(type.getField("uniqueId").get(info)))
   && type.getField("state").getInt(info)==2 && type.getField("committedState").getInt(info)==2
   && type.getField("logicalWidth").getInt(info)==width && type.getField("logicalHeight").getInt(info)==height
   && type.getField("rotation").getInt(info)==rotation;
 }
 private Object service(String name,String stub)throws Exception{
  IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
  return Class.forName(stub).getMethod("asInterface",IBinder.class).invoke(null,b);
 }
 protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  data.enforceInterface(TOKEN);if(Binder.getCallingUid()!=owner)throw new SecurityException("Wrong caller");
  if(closed)throw new IllegalStateException("Reader stopped");
  long identity=Binder.clearCallingIdentity();Bundle result=new Bundle();
  SurfaceControl[] excluded=new SurfaceControl[0];HardwareBuffer buffer=null;Bitmap ownedHomeBitmap=null;ParcelFileDescriptor evidenceArchive=null;
  boolean critical=code!=15&&code!=16&&code!=17&&code!=2;
  if(critical)WindowWorkPriority.INSTANCE.enterCritical();
  try{
   if(code==17){
    File archive=File.createTempFile("duo-shell-evidence-",".zip",new File("/data/local/tmp"));
    try{
     android.system.Os.chmod(archive.getPath(),0600);
     try(OutputStream output=new FileOutputStream(archive)){ShellDiagnosticArchive.write(new File("/data/local/tmp"),android.os.Process.myUid(),BuildConfig.VERSION_NAME,output);}
     evidenceArchive=ParcelFileDescriptor.open(archive,ParcelFileDescriptor.MODE_READ_ONLY);
     result.putParcelable("archive",evidenceArchive);result.putBoolean("ok",true);
    }finally{archive.delete();}
   }else if(code==16){result=MovingSceneCapture.captureDiagnosticPhysical(data.readString());
   }else if(code==15){result=SystemBarCapture.capture(data);
   }else if(code==13){result=WorkspaceCaptureGuard.inspect(data.createIntArray(),data.readString());
   }else if(code==14){int display=data.readInt();String physical=data.readString();int[] tasks=data.createIntArray();result=MovingSceneCapture.captureWorkspace(display,physical,tasks);
   }else if(code==10){result=HomeCaptureGuard.inspect(data.readInt(),data.readString());
   }else if(code==7){result=MovingSceneCapture.capture(data.readInt(),data.readInt(),data.readString());
   }else if(code==8){result=MovingSceneCapture.captureHomeInner(data.readString(),data.readInt()!=0);
   }else if(code==2){synchronized(settingsLock){
    String action=data.readString();
    String previous=command("settings","get","system","fold_lock_behavior").trim();
    if(!java.util.Arrays.asList("null","stay_awake_on_fold_key","selective_stay_awake_key","sleep_on_fold_key").contains(previous))throw new IllegalStateException("Unrecognized existing fold setting; use Samsung Display settings");
    String expected;
    if("always".equals(action)){expected="stay_awake_on_fold_key";if(!expected.equals(previous))command("settings","put","system","fold_lock_behavior",expected);}
    else if("restore".equals(action)){
     String original=data.readString();expected=original;
     if("null".equals(original))command("settings","delete","system","fold_lock_behavior");
     else if(java.util.Arrays.asList("stay_awake_on_fold_key","selective_stay_awake_key","sleep_on_fold_key").contains(original))command("settings","put","system","fold_lock_behavior",original);
     else throw new IllegalArgumentException("Unknown original fold setting");
    }else throw new IllegalArgumentException("Unknown action");
    String observed=command("settings","get","system","fold_lock_behavior").trim();
    if(!expected.equals(observed))throw new IllegalStateException("Fold setting readback mismatch: "+observed);
    result.putString("previous",previous);result.putString("value",observed);result.putBoolean("ok",true);}
   }else if(code==4||code==12){
    int task=data.readInt();String physical=data.readString();int expectedWidth=data.readInt(),expectedHeight=data.readInt();
    int[] workspace=code==12?data.createIntArray():null;
    MultiWindowTasks.Snapshot workspaceBefore=code==12?MultiWindowTasks.requireTasks(workspace):null;
    Object wm=service("window","android.view.IWindowManager$Stub");
    if((boolean)Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked");
    Object dm=service("display","android.hardware.display.IDisplayManager$Stub");
    Object info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,0);
    if(info==null||!java.util.Objects.equals(physical,String.valueOf(info.getClass().getField("uniqueId").get(info)))||info.getClass().getField("state").getInt(info)!=2)throw new IllegalStateException("Incoming panel not ON on D0");
    int width=info.getClass().getField("logicalWidth").getInt(info),height=info.getClass().getField("logicalHeight").getInt(info);
    if(code==12&&!workspaceBefore.fits(width,height))throw new IllegalStateException("Workspace bounds pending");
    if(width!=expectedWidth||height!=expectedHeight)throw new IllegalStateException("Waiting for destination dimensions");
    String dump=EndpointWindowTrace.dump();
    String state=code==12?EndpointWindowState.workspaceGuard(dump,workspace,0):EndpointWindowState.captureGuard(dump,task,0);
    if(code==12&&!workspaceBefore.signature.equals(MultiWindowTasks.requireTasks(workspace).signature))throw new IllegalStateException("Workspace changed during readiness check");
    Object after=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,0);
    if (!matchesCommittedPrimary(after,physical,width,height,info.getClass().getField("rotation").getInt(info)))
     throw new IllegalStateException("Primary changed during readiness check");
    result.putBoolean("ok",state.equals("READY; HAS_DRAWN"));result.putString("error",state);
   }else if(code==1 || code==3 || code==5 || code==6 || code==9 || code==11){
    long captureStarted=SystemClock.elapsedRealtime();String barBefore="",barAfter="";
    AppCaptureIdentity appIdentity=null;
    HomeWallpaperIdentity wallpaperIdentity=null;
    String homeIdentity=code==9?HomeCaptureIdentity.focused(false):null;
    int n=data.readInt();if(n<(code==6?0:1)||n>((code==5||code==6||code==9||code==11)?64:8))throw new IllegalArgumentException("No valid overlay exclusion surfaces");
    excluded=new SurfaceControl[n];for(int i=0;i<n;i++)excluded[i]=data.readTypedObject(SurfaceControl.CREATOR);
    int displayId=(code==1||code==5||code==6||code==9||code==11)&&data.dataAvail()>=4?data.readInt():0;
    int exactTask=(code==5||code==6||code==9||code==11)?data.readInt():-1;
    String expectedPhysical=(code==5||code==6||code==9||code==11)?data.readString():null;
    boolean expectOpaque=code==5&&data.dataAvail()>=4&&data.readInt()!=0;
    int appRasterPercent=code==5&&data.dataAvail()>=4?data.readInt():0;
    HomeContentPolicy.Sample homeReference=code==9&&data.readInt()!=0?new HomeContentPolicy.Sample(data.readInt(),data.readInt(),data.readInt()):null;
    int[] workspace=code==11?data.createIntArray():null;
    MultiWindowTasks.Snapshot workspaceBefore=null;
    if(code==11){if(displayId!=0)throw new IllegalStateException("Workspace capture stays on native primary");workspaceBefore=MultiWindowTasks.requireTasks(workspace);}
    if(displayId<0||displayId>1)throw new IllegalArgumentException("Unsupported capture display");
    for(SurfaceControl sc:excluded)if(sc==null||!sc.isValid())throw new IllegalStateException("Overlay surface changed");
    Object wm=service("window","android.view.IWindowManager$Stub");Class<?> wa=Class.forName("android.view.IWindowManager");
    if((boolean)wa.getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked — capture paused");
    Object dm=service("display","android.hardware.display.IDisplayManager$Stub");Object info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,displayId);
    if(code==5||code==6||code==9||code==11){
     if(exactTask<0||!java.util.Objects.equals(expectedPhysical,String.valueOf(info.getClass().getField("uniqueId").get(info))))throw new IllegalStateException("Incoming identity changed");
     String windowDump=EndpointWindowTrace.dump();barBefore=windowDump;
     String[] windows=EndpointWindowState.blocks(windowDump);
     String guard=code==11?EndpointWindowState.workspaceGuard(windows,workspace,displayId):code==9?HomeWindowEvidence.guard(windows,exactTask,displayId):EndpointWindowState.captureGuard(windows,exactTask,displayId);
     if(!guard.equals("READY; HAS_DRAWN"))throw new IllegalStateException("CAPTURE_GUARD_BEFORE "+guard);
     if(code==5||code==9)appIdentity=AppCaptureIdentity.readGuarded(windows,exactTask,displayId);
     if(code==9)wallpaperIdentity=HomeWallpaperIdentity.read(windows,displayId,appIdentity.uid);
    }
    int w=info.getClass().getField("logicalWidth").getInt(info),h=info.getClass().getField("logicalHeight").getInt(info);
    if(code==11&&!workspaceBefore.fits(w,h))throw new IllegalStateException("Workspace bounds pending");
    int rotation=info.getClass().getField("rotation").getInt(info);
    if(info.getClass().getField("state").getInt(info)!=2)throw new IllegalStateException("Screen off — capture paused");
    Bitmap bitmap;
    if(code==9){
     long pairStarted=SystemClock.elapsedRealtime();
     HomeNativeComposition.Result home=HomeNativeCapture.capture(wm,wa,displayId,w,h,excluded,appIdentity.uid,homeReference);
     bitmap=home.bitmap;ownedHomeBitmap=bitmap;
     result.putLong("homePairMs",SystemClock.elapsedRealtime()-pairStarted);
     result.putString("backend","Composed Home + launcher validation probe on display "+displayId);
     result.putString("homeAgreement",home.agreement);
     result.putIntArray("homeContent",new int[]{home.content.count,home.content.visible,home.content.solid});
    }else{
    DisplayCaptureApi captureApi=captureApi(wa);
    Class<?> builder=captureApi.builder;Object b=captureApi.constructor.newInstance();
    builder.getMethod("setSourceCrop",Rect.class).invoke(b,new Rect(0,0,w,h));
    int longSide=Math.max(w,h);
    int maxSide=code==5?AppCaptureRaster.side(longSide,appRasterPercent):code==6?320:code==1?640:1440;
    builder.getMethod("setFrameScale",float.class).invoke(b,Math.min(1f,(float)maxSide/longSide));
    if(code==5)result.putFloat("blurRadiusScale",AppCaptureRaster.blurScale(longSide,maxSide));
    builder.getMethod("setExcludeLayers",SurfaceControl[].class).invoke(b,(Object)excluded);
    if(appIdentity!=null)captureApi.restrictOwner(b,appIdentity.uid);
    Object args=builder.getMethod("build").invoke(b);
    Object shot;
    try(CaptureCompletion<Object> completion=new CaptureCompletion<>(GlassCapture::releaseShot)){
     // JNI keeps a weak reference to this callback; keep a strong reference until completion.
     Object callback=captureApi.statusCallback
       ? (java.util.function.ObjIntConsumer<Object>)completion::accept
       : (java.util.function.Consumer<Object>)(item->completion.accept(item,0));
     Object listener=captureApi.listenerConstructor.newInstance(callback);
     try{
      captureApi.capture.invoke(wm,displayId,args,listener);
      shot=completion.await(250);
     }finally{java.lang.ref.Reference.reachabilityFence(callback);java.lang.ref.Reference.reachabilityFence(listener);}
    }
    result.putString("backend","WindowManager display "+displayId+" / "+captureApi.name);
    buffer=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);
    if((boolean)shot.getClass().getMethod("containsSecureLayers").invoke(shot))throw new IllegalStateException("Protected content omitted");
    Bitmap hardware=(Bitmap)shot.getClass().getMethod("asBitmap").invoke(shot);
    if(hardware==null)throw new IllegalStateException("No readable frame");
    try{bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);}finally{hardware.recycle();}
    if(bitmap==null)throw new IllegalStateException("No readable copy");
    ownedHomeBitmap=bitmap; // Every rejected/exceptional capture owns its copy until delivery.
    }
    Object after=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,displayId);
    if(after==null || after.getClass().getField("state").getInt(after)!=2 || after.getClass().getField("logicalWidth").getInt(after)!=w || after.getClass().getField("logicalHeight").getInt(after)!=h || after.getClass().getField("rotation").getInt(after)!=rotation ||
       !java.util.Objects.equals(info.getClass().getField("uniqueId").get(info),after.getClass().getField("uniqueId").get(after))){bitmap.recycle();throw new IllegalStateException("Panel changed during capture; retry before handoff");}
    if(code==5||code==6||code==9||code==11){
     String windowDump=EndpointWindowTrace.dump();barAfter=windowDump;
     long windowObservedAt=SystemClock.elapsedRealtime();
     String[] windows=EndpointWindowState.blocks(windowDump);
     String guard=code==11?EndpointWindowState.workspaceGuard(windows,workspace,displayId):code==9?HomeWindowEvidence.guard(windows,exactTask,displayId):EndpointWindowState.captureGuard(windows,exactTask,displayId);
     if(!guard.equals("READY; HAS_DRAWN")){bitmap.recycle();throw new IllegalStateException("CAPTURE_GUARD_AFTER "+guard);}
     if(appIdentity!=null)appIdentity.requireSame(AppCaptureIdentity.readGuarded(windows,exactTask,displayId));
     if(wallpaperIdentity!=null)wallpaperIdentity.requireSame(HomeWallpaperIdentity.read(windows,displayId,appIdentity.uid));
     if(code==11&&!workspaceBefore.signature.equals(MultiWindowTasks.requireTasks(workspace).signature)){bitmap.recycle();throw new IllegalStateException("Workspace changed during capture");}
     if(code==9&&!homeIdentity.equals(HomeCaptureIdentity.focused(false))){bitmap.recycle();throw new IllegalStateException("Home changed during capture");}
     // On D0 the post-capture dump already passed the selected
     // task HAS_DRAWN check. Bracket it with the same D0 geometry/power checks
     // used by the standalone probe. Missing optional evidence uses that probe.
     if (displayId==0 && (code==5 || code==9 && homeReference!=null)) {
      try {
       Object current=Class.forName("android.hardware.display.IDisplayManager")
        .getMethod("getDisplayInfo",int.class).invoke(dm,0);
       if (matchesCommittedPrimary(current,expectedPhysical,w,h,rotation))
        result.putLong(code==9?"homeWindowObservedAt":"windowObservedAt",windowObservedAt);
      } catch (ReflectiveOperationException unavailable) {
       // The guarded image can still be used without supplying readiness.
      }
     }
     result.putString("provenance",code==11?"workspace="+java.util.Arrays.toString(workspace)+"; all selected panes HAS_DRAWN before/after; unchanged task identities and geometry; display capture, not optical proof":"selectedTask="+exactTask+" display="+displayId+" competingTaskWindows=none before/after; display capture, not isolated app pixels");
     if(code==5){
      AppCaptureBands bands=AppCaptureBands.sample(bitmap.getWidth(),bitmap.getHeight(),bitmap::getPixel);
      if(!bands.hasPixels)throw new IllegalStateException("APP_OWNER_PIXELS_EMPTY; retain previous app image");
      if(bands.fadedFromOpaque(expectOpaque))
       throw new IllegalStateException("APP_CAPTURE_FADED "+bands.report()+"; retaining previous app image");
      result.putBoolean("opaqueAppReference",bands.opaqueReference());
      result.putString("provenance","selectedTask="+exactTask+" display="+displayId+" ownerUid="+appIdentity.uid+" windows="+appIdentity.windows+" stable before/after; owner-filtered display; "+bands.report()+"; explicit Duo/photo exclusions; not task-isolated or optical proof");
     }
     if(code==9)result.putString("provenance","selectedTask="+exactTask+" display="+displayId+" launcherOwner="+appIdentity.uid+" registeredWallpaperOwner="+wallpaperIdentity.uid+"; stable before/after; composed Home retained once, wallpaper UID filter=off; launcher probe validation only; "+java.util.Arrays.toString(result.getIntArray("homeContent"))+" alpha counts; "+result.getString("homeAgreement")+"; pairMs="+result.getLong("homePairMs")+" guardedTotalMs="+(SystemClock.elapsedRealtime()-captureStarted)+"; separate captures, not task-isolated or optical proof");
    }
    java.util.ArrayList<Bundle> bars=new java.util.ArrayList<>();
    for(SystemBarRegions.Region region:appIdentity!=null?java.util.Collections.<SystemBarRegions.Region>emptyList():SystemBarRegions.stable(SystemBarRegions.parse(barBefore,displayId,w,h),SystemBarRegions.parse(barAfter,displayId,w,h))){
     Bundle row=new Bundle();row.putString("kind",region.kind);row.putIntArray("bounds",new int[]{region.left,region.top,region.right,region.bottom});bars.add(row);
    }
    result.putParcelableArrayList("systemBars",bars);result.putString("systemBarEvidence",appIdentity!=null?"not supplied by owner-filtered app image; independent destination capture":SystemBarRegions.evidence(barAfter,displayId,w,h));
    result.putString("physicalId",String.valueOf(info.getClass().getField("uniqueId").get(info)));result.putInt("displayId",displayId);result.putParcelable("bitmap",bitmap);result.putInt("width",w);result.putInt("height",h);result.putInt("rotation",rotation);result.putLong("stamp",captureStarted);result.putBoolean("ok",true);ownedHomeBitmap=null;
   }else throw new IllegalArgumentException("Unknown operation");
  }catch(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();result.putString("error",cause.getClass().getSimpleName()+": "+cause.getMessage());}
  finally{if(ownedHomeBitmap!=null&&!ownedHomeBitmap.isRecycled())ownedHomeBitmap.recycle();if(buffer!=null)buffer.close();for(SurfaceControl sc:excluded)if(sc!=null)sc.release();if(critical)WindowWorkPriority.INSTANCE.leaveCritical();Binder.restoreCallingIdentity(identity);}
  try{reply.writeNoException();reply.writeBundle(result);return true;}
  finally{if(evidenceArchive!=null)try{evidenceArchive.close();}catch(IOException ignored){}}
 }
 static Bitmap captureBitmap(Object wm,Class<?> wa,int display,int w,int h,SurfaceControl[] excluded)throws Exception{
  return captureBitmap(wm,wa,display,w,h,excluded,1440);
 }
 static Bitmap captureBitmap(Object wm,Class<?> wa,int display,int w,int h,SurfaceControl[] excluded,int maxSide)throws Exception{
  DisplayCaptureApi api=DisplayCaptureApi.resolve(Class::forName,wa,Rect.class,SurfaceControl[].class);
  Object builder=api.constructor.newInstance();
  api.builder.getMethod("setSourceCrop",Rect.class).invoke(builder,new Rect(0,0,w,h));
  api.builder.getMethod("setFrameScale",float.class).invoke(builder,Math.min(1f,maxSide/(float)Math.max(w,h)));
  api.builder.getMethod("setExcludeLayers",SurfaceControl[].class).invoke(builder,(Object)excluded);
  Object shot;
  try(CaptureCompletion<Object> completion=new CaptureCompletion<>(GlassCapture::releaseShot)){
   Object callback=api.statusCallback?(java.util.function.ObjIntConsumer<Object>)completion::accept:(java.util.function.Consumer<Object>)(item->completion.accept(item,0));
   Object listener=api.listenerConstructor.newInstance(callback);
   try{api.capture.invoke(wm,display,api.builder.getMethod("build").invoke(builder),listener);shot=completion.await(250);}
   finally{java.lang.ref.Reference.reachabilityFence(callback);java.lang.ref.Reference.reachabilityFence(listener);}
  }
  HardwareBuffer buffer=null;Bitmap hardware=null;
  try{
   buffer=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);
   if((boolean)shot.getClass().getMethod("containsSecureLayers").invoke(shot))throw new IllegalStateException("Protected content omitted");
   hardware=(Bitmap)shot.getClass().getMethod("asBitmap").invoke(shot);if(hardware==null)throw new IllegalStateException("No readable bar pixels");
   return hardware.copy(Bitmap.Config.ARGB_8888,false);
  }finally{if(hardware!=null)hardware.recycle();if(buffer!=null)buffer.close();}
 }
 private static void releaseShot(Object shot){
  try{HardwareBuffer b=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);if(b!=null)b.close();}catch(Exception ignored){}
 }
 private String command(String...args)throws Exception{
  java.lang.Process process=new ProcessBuilder(args).redirectErrorStream(true).start();
  String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
  if(process.waitFor()!=0)throw new IOException(output);return output;
 }
}
