package org.duofold.live;
import android.os.*;
import java.io.*;
import java.util.*;
public class AngleReader extends Binder {
 public static final String DESCRIPTOR="org.duofold.live.wallpaperprobe.AngleReader";
 private GlassCapture capture;
 private PreviewExpansion expansion;
 private volatile HandoffFade fade;
 private FoldRotationHold rotation,retiringRotation;private long retiringSince;
 private String recoveryApk;
 private final ClosedHingeGate closedHingeGate=new ClosedHingeGate();
 private final AnimationModePolicy animationMode=new AnimationModePolicy();
 private volatile boolean previewAllowed=false;
 private final InnerLiveMirror mirror=new InnerLiveMirror();
 private final MainGlassSurfaces mainGlass=new MainGlassSurfaces();
 private final ConcurrentController concurrent=new ConcurrentController();
 private final ContinuityProbeDiagnostics continuity=new ContinuityProbeDiagnostics();
 private final CoverHandoff handoff=new CoverHandoff();
 private long screenshotTraceSentAt;
 private final ClosureDeviceState closureState=new ClosureDeviceState();
 private long heartbeat=SystemClock.elapsedRealtime();private long diagAt;
 private int logLines,matchingResponses,staleResponses;
 private int owner=-1,generation=0,count=0; private float angle=Float.NaN,min=180,max=0; private long last=0; private String state="Idle",raw=""; private java.lang.Process process; private final Set<Float> unique=new HashSet<>();
 public AngleReader(){attachInterface(null,DESCRIPTOR);}
 // Mirror attach waits up to 250 ms for its compositor commit. Run it outside this monitor so the 4 ms
 // angle poll (code 2), which drives the fade and the cover hold, never stalls behind it mid-transition.
 protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
  // Shizuku destroy: wait for the rotation restore (up to 8 s) OUTSIDE the monitor (plan D7), so a code-2 poll
  // in flight is not held for that long before the process exits.
  if(code==16777115){FoldRotationHold pending;synchronized(this){pending=rotation!=null?rotation:retiringRotation;stop();}if(pending!=null)pending.awaitRelease();System.exit(0);return true;}
  if(code==4||code==5){
   data.enforceInterface(DESCRIPTOR);
   synchronized(this){int caller=Binder.getCallingUid();if(owner<0)owner=caller;if(caller!=owner)throw new SecurityException("Wrong caller");}
   if(code==5){mirror.detach(data.readInt());reply.writeNoException();return true;}
   int id=data.readInt();android.view.SurfaceControl parent=data.readTypedObject(android.view.SurfaceControl.CREATOR);int w=data.readInt(),h=data.readInt();boolean halfPane=data.dataAvail()>=4&&data.readInt()!=0;
   int ticket=mirror.ticket();  // before reading previewAllowed (plan D1)
   Bundle result=mirror.attach(id,parent,w,h,previewAllowed,halfPane,ticket);HandoffFade f=fade;if(result.getBoolean("ok")&&f!=null)f.mirrorSubmitted();
   reply.writeNoException();reply.writeBundle(result);return true;
  }
  synchronized(this){return locked(code,data,reply,flags);}
 }
 private boolean locked(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
  if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
  data.enforceInterface(DESCRIPTOR);
  int caller=Binder.getCallingUid();if(owner<0)owner=caller;if(caller!=owner)throw new SecurityException("Wrong caller");
  if(code==7){
   android.view.SurfaceControl sc=data.readTypedObject(android.view.SurfaceControl.CREATOR);
   long identity=Binder.clearCallingIdentity();
   try{
    if(sc==null || !sc.isValid())throw new IllegalStateException("Animation surface unavailable");
    // Owned by mainGlass from here on (released on replace/stop), so its skip flag can follow the mirror (B1).
    mainGlass.add(sc,MainGlassSurfaces.skipFor(RecordVisible.enabled(),previewAllowed||mirror.attached()));sc=null;
    reply.writeNoException();reply.writeString("Cover animation excluded from mirrors");
   }catch(Exception e){throw new IllegalStateException("Could not exclude animation",e);}
   finally{if(sc!=null)sc.release();Binder.restoreCallingIdentity(identity);}
   return true;
  }
  if(code==9){boolean inner=data.readInt()!=0;long when=data.readLong();int kind=data.dataAvail()>=4?data.readInt():0;long captured=data.dataAvail()>=8?data.readLong():-1;if(fade!=null)fade.drawn(inner,when,kind,captured);reply.writeNoException();return true;}
  if(code==8){if(expansion==null)expansion=new PreviewExpansion(caller);reply.writeNoException();reply.writeStrongBinder(expansion);return true;}
  if(code==6){if(capture==null)capture=new GlassCapture(caller);reply.writeNoException();reply.writeStrongBinder(capture);return true;}
  if(code==1){String action=data.readString();if(action==null||!action.matches("org\\.duofold\\.live\\.wallpaperprobe\\.READ_[0-9]+"))throw new IllegalArgumentException("Invalid action");recoveryApk=data.readString();start(action);reply.writeNoException();return true;}
  if(code==2){heartbeat=SystemClock.elapsedRealtime();boolean unlocked=data.readInt()!=0,dual=data.readInt()!=0,primaryInner=data.readInt()!=0,secondaryReady=data.readInt()!=0;int frozenSource=data.readInt();float openThreshold=data.readFloat();boolean live=data.readInt()!=0;boolean appEnabled=data.readInt()!=0;float closedThreshold=FoldThreshold.sanitizeClosed(data.readFloat());long probeRequest=data.dataAvail()>=8?data.readLong():0;
   float fadeSmoothing=data.dataAvail()>=4?data.readFloat():FadeSettings.DEFAULT_SMOOTHING;
   float fadeGradualness=data.dataAvail()>=4?data.readFloat():FadeSettings.DEFAULT_GRADUALNESS;
   String mode=data.dataAvail()>0?AnimationModePolicy.sanitize(data.readString()):AnimationModePolicy.DEFAULT;
   boolean debug=data.dataAvail()>=4&&data.readInt()!=0;
   float handoffAngle=HandoffSettings.angle(data.dataAvail()>=4?data.readFloat():HandoffSettings.DEFAULT);
   boolean screenshotStartup=data.dataAvail()>=4&&data.readInt()!=0;boolean screenshotPrepared=data.dataAvail()>=4&&data.readInt()!=0;
   concurrent.powerOverrideEnabled(data.dataAvail()>=4&&data.readInt()!=0);
   if(data.dataAvail()>=4)data.readInt(); // Reserved former closing-power slot.
   concurrent.closingAppReturnEnabled(data.dataAvail()>=4&&data.readInt()!=0);
   boolean jitterProtection=data.dataAvail()<4||data.readInt()!=0;
   String behavior=AnimationModePolicy.initialBehavior(data.dataAvail()>0?data.readString():null,mode);
   float effectiveAngle=closedHingeGate.filter(angle,closedThreshold,jitterProtection,last>0&&heartbeat-last<750);
   closureState.sample(angle,effectiveAngle,last>0&&heartbeat-last<750,dual,unlocked);
   // Display power only, for CloseReleaseGate (plan A2); a trailing field. Older clients: fall back to the unlocked flag.
   boolean screenOn=data.dataAvail()>=4?data.readInt()!=0:unlocked;
   handoff.screen(screenOn);concurrent.screen(screenOn);concurrent.handoff(handoffAngle);mirror.handoff(handoffAngle);
   boolean effectAllowed=animationMode.update(behavior,effectiveAngle,last>0&&heartbeat-last<750);
   boolean mirrorMode=AnimationModePolicy.mirrors(mode);
   appEnabled=appEnabled&&effectAllowed;
   // Plan D6: a new hold only after the previous one restored rotation and dropped its file lock; creating it
   // earlier failed with "Another orientation hold is active" and left ~2 s with no hold after a restart.
   // Bounded like awaitRelease(): after 8 s a stuck old hold no longer blocks a new one.
   if(rotation==null&&(retiringRotation==null||retiringRotation.released()||heartbeat-retiringSince>8000)){retiringRotation=null;rotation=new FoldRotationHold(caller/100000,recoveryApk);}
   if(rotation!=null){rotation.screenshotRelease(dual&&screenshotStartup);rotation.update(appEnabled&&unlocked,last>0&&heartbeat-last<750,effectiveAngle,openThreshold);}
   if(fade==null)fade=new HandoffFade();
   fade.settings(fadeSmoothing,fadeGradualness,mirrorMode&&!debug,openThreshold,handoffAngle);
   fade.update(live&&!dual&&appEnabled&&!handoff.probeHolding(),effectiveAngle,last>0&&heartbeat-last<750);
   if(expansion!=null){expansion.motion(effectiveAngle,openThreshold);expansion.enabled(mirrorMode&&live&&!dual&&appEnabled&&!handoff.probeHolding());}
   boolean bridgeReady=true;
   boolean needsBridge=mirrorMode && live && !dual && unlocked && appEnabled && handoff.active() && !handoff.probeHolding() && angle>=handoffAngle && angle<FoldThreshold.sanitize(openThreshold) && last>0 && heartbeat-last<750 && expansion!=null;
   if(needsBridge)bridgeReady=expansion.holdBeforeRelease();else if(expansion!=null)expansion.cancelReleaseWait();
   concurrent.observeMotion(effectiveAngle,last>0&&heartbeat-last<2000,appEnabled&&unlocked,openThreshold,dual,screenshotStartup);
   boolean wasCoverHeld=handoff.active();
   boolean freshAngle=last>0&&heartbeat-last<750;
   // Mode switches: the incoming controller starts only once the outgoing one has actually released. A gated release
   // can still be deferring, and starting the other override meanwhile is rejected as an existing override.
   if(!effectAllowed){handoff.releaseGated(effectiveAngle,freshAngle);concurrent.releaseGated(effectiveAngle,freshAngle);}else if(dual){handoff.releaseGated(effectiveAngle,freshAngle);if(!handoff.active())concurrent.update(effectiveAngle,last>0&&heartbeat-last<2000,unlocked,primaryInner,secondaryReady,frozenSource,openThreshold,screenshotStartup,screenshotPrepared);}else{concurrent.releaseGated(effectiveAngle,freshAngle);if(!concurrent.active()&&bridgeReady)handoff.update(effectiveAngle,freshAngle,unlocked,live&&appEnabled,openThreshold,probeRequest,handoffAngle);}
   boolean timedCoverRelease=handoff.takeTimedCoverRelease();
   if(((wasCoverHeld && !handoff.active()) || timedCoverRelease) && expansion!=null)expansion.releaseReturned();
   if(handoff.probeHolding()&&fade!=null)fade.update(false,effectiveAngle,false);
   if(expansion!=null&&handoff.probeHolding())expansion.enabled(false);
   previewAllowed=mirrorMode&&effectAllowed&&!handoff.probeNative()&&CoverPreviewPolicy.allowed(live,dual,primaryInner,unlocked,handoff.active());
   if(!previewAllowed)mirror.revoke();
   mainGlass.update(MainGlassSurfaces.skipFor(RecordVisible.enabled(),previewAllowed||mirror.attached()));
   continuity.sample(handoff.probeHolding(),handoff.probeStatus());
   // Plan F5: functional fields every poll; status strings at most every 250 ms ("diag" tells the client).
   long replyNow=SystemClock.elapsedRealtime();boolean diag=DiagnosticsCadence.due(replyNow,diagAt);if(diag)diagAt=replyNow+DiagnosticsCadence.INTERVAL_MS;
   Bundle b=new Bundle();b.putBoolean("diag",diag);b.putBundle("incomingHold",concurrent.incomingHoldInfo());if(SystemClock.elapsedRealtime()-screenshotTraceSentAt>=500){b.putString("screenshotTrace",concurrent.trace()+"\n"+handoff.trace()+"\n"+closureState.report());screenshotTraceSentAt=SystemClock.elapsedRealtime();}
   if(diag){b.putString("rotationHold",rotation==null?"Rotation hold idle":rotation.status);b.putString("handoffFade",fade==null?"Handoff fade idle":fade.status);b.putString("continuityProbe",handoff.probeStatus());b.putString("continuityTrace",continuity.report());b.putString("bridgeTrace",expansion==null?"No bridge":expansion.trace);b.putString("expansion",expansion==null?"Expansion idle":expansion.status);b.putString("readerDiagnostics",state+"; wallpaper log lines="+logLines+"; parsed responses="+matchingResponses+"; rejected timestamps="+staleResponses);b.putString("mirror",mirror.status);b.putString("handoff",dual?concurrent.status:handoff.status);b.putString("raw",raw);b.putString("closedHingeGate",closedHingeGate.report());}
   b.putBoolean("effectAllowed",effectAllowed);b.putBoolean("coverPreview",previewAllowed);b.putString("state",state);b.putBoolean("continuityNative",handoff.probeNative());b.putBoolean("nativeInner",concurrent.secondaryHasNativeContent()||handoff.probeNative());b.putBoolean("dualActive",dual&&concurrent.active());b.putInt("uid",android.os.Process.myUid());b.putInt("count",count);b.putInt("unique",unique.size());b.putFloat("rawAngle",angle);b.putFloat("angle",effectiveAngle);b.putFloat("min",min);b.putFloat("max",max);b.putLong("last",last);reply.writeNoException();reply.writeBundle(b);return true;}
  if(code==3){stop();reply.writeNoException();return true;}return super.onTransact(code,data,reply,flags);
 }
 private synchronized void stop(){closedHingeGate.reset();previewAllowed=false;if(rotation!=null){rotation.close();retiringRotation=rotation;retiringSince=SystemClock.elapsedRealtime();rotation=null;}if(fade!=null){fade.close();fade=null;}if(expansion!=null){expansion.close();expansion=null;}if(capture!=null){capture.close();capture=null;}mirror.shutdown();mainGlass.close();concurrent.releaseForTeardown();handoff.releaseForTeardown();generation++;if(process!=null){process.destroy();process=null;}state="Stopped";}
 private synchronized void start(String action){
  stop();int reaped=OrphanReaders.reap();if(reaped>0)android.util.Log.w("DuoReady","reaped "+reaped+" orphaned angle reader(s)");logLines=matchingResponses=staleResponses=0;count=0;unique.clear();angle=Float.NaN;min=180;max=0;last=0;raw="";state="Starting log reader";final int gen=generation;
  Thread reader=new Thread(()->{
   java.lang.Process child=null;
   try{
    child=new ProcessBuilder("logcat","-v","epoch","-T","1","-s","SprWallpaper|FoldInteractive:V","*:S").redirectErrorStream(true).start();
    synchronized(this){if(gen!=generation){child.destroy();return;}process=child;state="Listening";}
    try(BufferedReader input=new BufferedReader(new InputStreamReader(child.getInputStream()))){String line;while((line=input.readLine())!=null){
     Float value=AngleParser.parse(line,action);
     synchronized(this){if(gen!=generation)return;logLines++;if(value==null)continue;matchingResponses++;
      // Timestamp freshness: never label buffered responses as current.
      String[] fields=line.trim().split("\\s+",2);long age;
      try{age=System.currentTimeMillis()-(long)(Double.parseDouble(fields[0])*1000);}catch(Exception ex){staleResponses++;continue;}
      if(age < -100||age>1500){staleResponses++;continue;}
      angle=value;mirror.blur(value);last=SystemClock.elapsedRealtime()-Math.max(0,age);count++;unique.add(value);min=Math.min(min,value);max=Math.max(max,value);raw=line;state="Receiving";
     }
    }}
    synchronized(this){if(gen==generation)state="Log reader ended";}
   }catch(Exception ex){synchronized(this){if(gen==generation)state="Reader error: "+ex;}}
   finally{if(child!=null)child.destroy();}
  },"wallpaper-angle-reader");reader.setDaemon(true);reader.start();
  Thread timeout=new Thread(()->{while(true){try{Thread.sleep(1000);}catch(InterruptedException ignored){return;}synchronized(this){if(gen!=generation)return;if(SystemClock.elapsedRealtime()-heartbeat>2500){stop();return;}}}},"reader-lease");timeout.setDaemon(true);timeout.start();
 }
}
