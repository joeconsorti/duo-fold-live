package org.duofold.live;
import android.os.*;
import java.io.*;
import java.util.*;
public class AngleReader extends Binder {
 public static final String DESCRIPTOR="org.duofold.live.wallpaperprobe.AngleReader";
 private GlassCapture capture;
 private final ShellPanelIdentity panelIdentity=new ShellPanelIdentity();
 private org.duofold.live.experimental.b8.LiveB8Controller b8;
 private PreviewExpansion expansion;
 private HandoffFade fade;
 private FoldRotationHold rotation;
 private String recoveryApk;
 private final ClosedHingeGate closedHingeGate=new ClosedHingeGate();
 private final AnimationModePolicy animationMode=new AnimationModePolicy();
 private final HingePausePolicy hingePause=new HingePausePolicy();
 private boolean previewAllowed=false;
 private final InnerLiveMirror mirror=new InnerLiveMirror();
 private final ConcurrentController concurrent=new ConcurrentController();
 private final ContinuityProbeDiagnostics continuity=new ContinuityProbeDiagnostics();
 private final CoverHandoff handoff=new CoverHandoff();
 private final DiagnosticCadence diagnostics=new DiagnosticCadence(500);
 private final ClosureDeviceState closureState=new ClosureDeviceState();
 private long heartbeat=SystemClock.elapsedRealtime();
 private int logLines,matchingResponses,staleResponses;
 private int owner=-1,generation=0,count=0; private float angle=Float.NaN,min=180,max=0; private long last=0; private String state="Idle",raw=""; private java.lang.Process process; private final Set<Float> unique=new HashSet<>();
 public AngleReader(){attachInterface(null,DESCRIPTOR);}
 protected synchronized boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
  if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
  if(code==16777115){FoldRotationHold pending=rotation;stop();if(pending!=null)pending.awaitRelease();System.exit(0);return true;}
  data.enforceInterface(DESCRIPTOR);
  int caller=Binder.getCallingUid();if(owner<0)owner=caller;if(caller!=owner)throw new SecurityException("Wrong caller");
  if(code==7){
   android.view.SurfaceControl sc=data.readTypedObject(android.view.SurfaceControl.CREATOR);
   long identity=Binder.clearCallingIdentity();
   try{
    if(sc==null || !sc.isValid())throw new IllegalStateException("Animation surface unavailable");
    try(android.view.SurfaceControl.Transaction t=new android.view.SurfaceControl.Transaction()){
     android.view.SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",android.view.SurfaceControl.class,boolean.class).invoke(t,sc,true);t.apply();
    }
    reply.writeNoException();reply.writeString("Cover animation excluded from mirrors");
   }catch(Exception e){throw new IllegalStateException("Could not exclude animation",e);}
   finally{if(sc!=null)sc.release();Binder.restoreCallingIdentity(identity);}
   return true;
  }
  if(code==10){
   long token=data.readLong();Bundle layers=b8==null?null:(Bundle)b8.sceneHandles(token);
   try{reply.writeNoException();reply.writeBundle(layers);}finally{
    if(layers!=null)for(int n=0;n<2;n++){Bundle row=layers.getBundle("p"+n);android.view.SurfaceControl sc=row==null?null:row.getParcelable("root",android.view.SurfaceControl.class);if(sc!=null)sc.release();}
   }return true;
  }
  if(code==9){boolean inner=data.readInt()!=0;long when=data.readLong();int kind=data.dataAvail()>=4?data.readInt():0;long captured=data.dataAvail()>=8?data.readLong():-1;if(fade!=null)fade.drawn(inner,when,kind,captured);reply.writeNoException();return true;}
  if(code==8){if(expansion==null)expansion=new PreviewExpansion(caller);reply.writeNoException();reply.writeStrongBinder(expansion);return true;}
  if(code==6){if(capture==null)capture=new GlassCapture(caller);reply.writeNoException();reply.writeStrongBinder(capture);return true;}
  if(code==4){int id=data.readInt();android.view.SurfaceControl parent=data.readTypedObject(android.view.SurfaceControl.CREATOR);int w=data.readInt(),h=data.readInt();Bundle result=mirror.attach(id,parent,w,h,previewAllowed);if(result.getBoolean("ok")&&fade!=null)fade.mirrorSubmitted();reply.writeNoException();reply.writeBundle(result);return true;}
  if(code==5){mirror.detach(data.readInt());reply.writeNoException();return true;}
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
   boolean b8Selected=data.dataAvail()>=4&&data.readInt()!=0&&!dual;
   int b8Thermal=data.dataAvail()>=4?data.readInt():0;
   Bundle b8Presentation=data.dataAvail()>0?data.readBundle(getClass().getClassLoader()):null;
   if(b8Selected&&(b8==null||b8.finished()))b8=new org.duofold.live.experimental.b8.LiveB8Controller();
   if(b8!=null&&b8Presentation!=null){b8.presentation(b8Presentation.getLong("session"),b8Presentation.getBoolean("prepared"),b8Presentation.getString("primary"),b8Presentation.getLong("commitAt"),b8Presentation.getBoolean("capturePaused"),b8Presentation.getBoolean("animated"),b8Presentation.getBoolean("nativePrimary"));b8.captured(b8Presentation.getLong("session"),b8Presentation.getString("capturedPhysical"),b8Presentation.getInt("capturedRevision",-1));b8.primaryReady(b8Presentation.getLong("session"),b8Presentation.getLong("primaryCheck"),b8Presentation.getString("readyPhysical"),b8Presentation.getLong("readyAt"),b8Presentation.getLong("readyStartedAt"));}
   boolean b8Route=b8Selected||(b8!=null&&b8.ownsRoute());
   float effectiveAngle=closedHingeGate.filter(angle,closedThreshold,jitterProtection,last>0&&heartbeat-last<750);
   closureState.sample(angle,effectiveAngle,last>0&&heartbeat-last<750,dual,unlocked);
   boolean effectAllowed=animationMode.update(behavior,effectiveAngle,last>0&&heartbeat-last<750)&&b8Thermal<3;
   hingePause.update(appEnabled&&unlocked&&effectAllowed&&probeRequest==0,heartbeat,last,effectiveAngle,openThreshold,primaryInner?1:2,b8Selected?2:dual?1:0);
   boolean stationary=hingePause.held();
   boolean mirrorMode=AnimationModePolicy.mirrors(mode);
   appEnabled=appEnabled&&effectAllowed;
   if(rotation==null)rotation=new FoldRotationHold(caller/100000,recoveryApk);
   rotation.screenshotRelease(b8Route||(dual&&screenshotStartup));
   if(fade==null)fade=new HandoffFade();
   fade.settings(fadeSmoothing,fadeGradualness,mirrorMode&&!debug,openThreshold,handoffAngle);
   fade.update(!stationary&&!b8Route&&!HandoffBaseline.ENABLED&&live&&!dual&&appEnabled&&!handoff.probeHolding(),effectiveAngle,last>0&&heartbeat-last<750);
   if(expansion!=null){expansion.motion(effectiveAngle,openThreshold);expansion.enabled(!stationary&&!b8Route&&!HandoffBaseline.ENABLED&&mirrorMode&&live&&!dual&&appEnabled&&!handoff.probeHolding());}
   boolean bridgeReady=true;
   boolean needsBridge=!stationary&&!b8Route&&!HandoffBaseline.ENABLED && mirrorMode && live && !dual && unlocked && appEnabled && handoff.active() && !handoff.probeHolding() && angle>=handoffAngle && angle<FoldThreshold.sanitize(openThreshold) && last>0 && heartbeat-last<750 && expansion!=null;
   if(needsBridge)bridgeReady=expansion.holdBeforeRelease();else if(expansion!=null)expansion.cancelReleaseWait();
   if(expansion!=null){
    boolean coverEnabled=!stationary&&!b8Route&&!HandoffBaseline.ENABLED&&mirrorMode&&live&&!dual&&unlocked&&appEnabled&&!handoff.probeHolding()&&last>0&&heartbeat-last<750;
    expansion.coverControl(coverEnabled,effectiveAngle,handoffAngle);
    if(coverEnabled && handoff.innerActive() && DirectHandoffPolicy.next(true,effectiveAngle,true,true,true,openThreshold,handoffAngle)==DirectHandoffPolicy.COVER)bridgeReady=expansion.coverBeforeSwitch() && bridgeReady;
   }
   concurrent.observeMotion(effectiveAngle,last>0&&heartbeat-last<2000,appEnabled&&unlocked&&!b8Route,openThreshold,dual,screenshotStartup);
   boolean wasCoverHeld=handoff.active();
   if(b8Route){handoff.release();concurrent.release();if(b8!=null){b8.thermal(b8Thermal);b8.pause(stationary,hingePause.sourcePrimary());b8.update(b8Selected&&appEnabled&&unlocked,effectiveAngle,last,openThreshold,handoffAngle);}}
   else if(stationary){concurrent.release();handoff.holdPanel(hingePause.sourcePrimary(),hingePause.startedAt());}
   else if(!effectAllowed){handoff.release();concurrent.release();}else if(dual){handoff.release();concurrent.update(effectiveAngle,last>0&&heartbeat-last<2000,unlocked,primaryInner,secondaryReady,frozenSource,openThreshold,screenshotStartup,screenshotPrepared,handoffAngle);}else{concurrent.release();if(bridgeReady)handoff.update(effectiveAngle,last,last>0&&heartbeat-last<750,unlocked,live&&appEnabled,openThreshold,probeRequest,handoffAngle);}
   boolean timedCoverRelease=handoff.takeTimedCoverRelease();
   if(((wasCoverHeld && !handoff.active()) || timedCoverRelease) && expansion!=null)expansion.releaseReturned();
   if(handoff.probeHolding()&&fade!=null)fade.update(false,effectiveAngle,false);
   if(expansion!=null&&handoff.probeHolding())expansion.enabled(false);
   previewAllowed=!stationary&&!b8Route&&mirrorMode&&effectAllowed&&!handoff.probeNative()&&CoverPreviewPolicy.allowed(live,dual,primaryInner,unlocked,handoff.active());
   if(!previewAllowed)mirror.close();
   continuity.sample(handoff.probeHolding(),handoff.probeStatus());
   Bundle b=new Bundle();b.putBoolean("b8Eligible",b8Selected&&appEnabled&&unlocked);b.putBundle("panelIdentity",panelIdentity.snapshot());
   b.putLong("hingePauseAt",hingePause.startedAt());b.putString("hingePauseReport",hingePause.report());
   // The coordinated scene stays alive through its verified dissolve; parked native UI stays clear.
   effectAllowed=effectAllowed&&(!hingePause.settled(heartbeat)||(b8Route&&b8!=null&&b8.sceneSession()>0));
   rotation.update(FoldRotationPolicy.animationEnabled(appEnabled,unlocked,effectAllowed,
     hingePause.settled(heartbeat),b8Route&&b8!=null&&b8.sceneSession()>0,handoff.probeHolding()),
     last>0&&heartbeat-last<750,effectiveAngle,openThreshold);
   if(b8!=null){Bundle scene=new Bundle();scene.putLong("pauseReturnAt",b8.pauseReturnAt());scene.putLong("pauseFadeAt",b8.pauseFadeAt());scene.putLong("session",b8.sceneSession());scene.putBoolean("direct",b8.directScene());scene.putString("directStage",b8.directStage());scene.putBoolean("sceneMapped",b8.sceneMapped());scene.putInt("revision",b8.sceneRevision());scene.putInt("captureDisplay",b8.captureDisplay());scene.putString("capturePhysical",b8.capturePhysical());scene.putLong("primaryCheck",b8.primaryCheck());scene.putString("primaryCheckPhysical",b8.primaryCheckPhysical());scene.putBoolean("live",b8.livePresentation());scene.putBoolean("pauseCapture",b8.capturePauseRequested());scene.putBoolean("compositionProbeAllowed",b8.compositionProbeAllowed());scene.putString("source",b8.sceneSource());scene.putInt("task",b8.sceneTask());scene.putBoolean("home",b8.sceneHome());scene.putBoolean("workspace",b8.sceneWorkspace());scene.putIntArray("workspaceTasks",b8.sceneTasks());b.putBundle("b8Presentation",scene);}
   b.putBundle("incomingHold",concurrent.incomingHoldInfo());b.putBoolean("effectAllowed",effectAllowed);b.putBoolean("coverPreview",previewAllowed);b.putString("state",state);b.putBoolean("continuityNative",handoff.probeNative());b.putBoolean("nativeInner",concurrent.secondaryHasNativeContent()||handoff.probeNative());b.putBoolean("dualActive",dual&&concurrent.active());b.putString("handoff",b8Route&&b8!=null?b8.status():dual?concurrent.status:handoff.status);b.putInt("uid",android.os.Process.myUid());b.putInt("count",count);b.putInt("unique",unique.size());b.putFloat("rawAngle",angle);b.putFloat("angle",effectiveAngle);b.putFloat("min",min);b.putFloat("max",max);b.putLong("last",last);
   if(diagnostics.due(SystemClock.elapsedRealtime())){
    b.putString("closedHingeGate",closedHingeGate.report());b.putString("screenshotTrace",concurrent.trace()+"\n"+handoff.trace()+"\n"+closureState.report());b.putString("rotationHold",rotation==null?"Rotation hold idle":rotation.report());b.putString("handoffFade",fade==null?"Handoff fade idle":fade.status);b.putString("continuityProbe",handoff.probeStatus());b.putString("continuityTrace",continuity.report());b.putString("bridgeTrace",b8!=null?(b8Route?"":"Retained B8 evidence; mode currently inactive.\n")+b8.report():expansion==null?"No bridge":expansion.report());b.putString("expansion",expansion==null?"Expansion idle":expansion.status);b.putString("readerDiagnostics",state+"; wallpaper log lines="+logLines+"; parsed responses="+matchingResponses+"; rejected timestamps="+staleResponses);b.putString("mirror",mirror.status);b.putString("raw",raw);
   }
   reply.writeNoException();reply.writeBundle(b);return true;}
  if(code==3){stop();reply.writeNoException();return true;}return super.onTransact(code,data,reply,flags);
 }
 public synchronized void closeRoot(){FoldRotationHold pending=rotation;stop();if(pending!=null)pending.awaitRelease();}
 private synchronized void stop(){if(b8!=null)b8.close();closedHingeGate.reset();hingePause.reset();previewAllowed=false;if(rotation!=null){rotation.close();rotation=null;}if(fade!=null){fade.close();fade=null;}if(expansion!=null){expansion.close();expansion=null;}if(capture!=null){capture.close();capture=null;}mirror.close();concurrent.release();handoff.release();generation++;if(process!=null){process.destroy();process=null;}state="Stopped";}
 private synchronized void start(String action){
  stop();diagnostics.reset();logLines=matchingResponses=staleResponses=0;count=0;unique.clear();angle=Float.NaN;min=180;max=0;last=0;raw="";state="Starting log reader";final int gen=generation;
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
      angle=value;last=SystemClock.elapsedRealtime()-Math.max(0,age);count++;unique.add(value);min=Math.min(min,value);max=Math.max(max,value);raw=line;state="Receiving";
     }
    }}
    synchronized(this){if(gen==generation)state="Log reader ended";}
   }catch(Exception ex){synchronized(this){if(gen==generation)state="Reader error: "+ex;}}
   finally{if(child!=null)child.destroy();}
  },"wallpaper-angle-reader");reader.setDaemon(true);reader.start();
  Thread timeout=new Thread(()->{while(true){try{Thread.sleep(1000);}catch(InterruptedException ignored){return;}synchronized(this){if(gen!=generation)return;if(SystemClock.elapsedRealtime()-heartbeat>2500){stop();return;}}}},"reader-lease");timeout.setDaemon(true);timeout.start();
 }
}
