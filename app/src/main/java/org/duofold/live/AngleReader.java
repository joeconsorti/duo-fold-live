package org.duofold.live;
import android.os.*;
import java.io.*;
import java.util.*;
public class AngleReader extends Binder {
 public static final String DESCRIPTOR="org.duofold.live.wallpaperprobe.AngleReader";
 private GlassCapture capture;
 private PreviewExpansion expansion;
 private HandoffFade fade;
 private FoldRotationHold rotation;
 private final AnimationModePolicy animationMode=new AnimationModePolicy();
 private boolean previewAllowed=false;
 private final InnerLiveMirror mirror=new InnerLiveMirror();
 private final ConcurrentController concurrent=new ConcurrentController();
 private final ContinuityProbeDiagnostics continuity=new ContinuityProbeDiagnostics();
 private final CoverHandoff handoff=new CoverHandoff();
 private long heartbeat=SystemClock.elapsedRealtime();
 private int logLines,matchingResponses,staleResponses;
 private int owner=-1,generation=0,count=0; private float angle=Float.NaN,min=180,max=0; private long last=0; private String state="Idle",raw=""; private java.lang.Process process; private final Set<Float> unique=new HashSet<>();
 public AngleReader(){attachInterface(null,DESCRIPTOR);}
 protected synchronized boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
  if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
  if(code==16777115){stop();System.exit(0);return true;}
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
  if(code==9){boolean inner=data.readInt()!=0;long when=data.readLong();int kind=data.dataAvail()>=4?data.readInt():0;long captured=data.dataAvail()>=8?data.readLong():-1;if(fade!=null)fade.drawn(inner,when,kind,captured);reply.writeNoException();return true;}
  if(code==8){if(expansion==null)expansion=new PreviewExpansion(caller);reply.writeNoException();reply.writeStrongBinder(expansion);return true;}
  if(code==6){if(capture==null)capture=new GlassCapture(caller);reply.writeNoException();reply.writeStrongBinder(capture);return true;}
  if(code==4){int id=data.readInt();android.view.SurfaceControl parent=data.readTypedObject(android.view.SurfaceControl.CREATOR);int w=data.readInt(),h=data.readInt();Bundle result=mirror.attach(id,parent,w,h,previewAllowed);if(result.getBoolean("ok")&&fade!=null)fade.mirrorSubmitted();reply.writeNoException();reply.writeBundle(result);return true;}
  if(code==5){mirror.detach(data.readInt());reply.writeNoException();return true;}
  if(code==1){String action=data.readString();if(action==null||!action.matches("org\\.duofold\\.live\\.wallpaperprobe\\.READ_[0-9]+"))throw new IllegalArgumentException("Invalid action");start(action);reply.writeNoException();return true;}
  if(code==2){heartbeat=SystemClock.elapsedRealtime();boolean unlocked=data.readInt()!=0,dual=data.readInt()!=0,primaryInner=data.readInt()!=0,secondaryReady=data.readInt()!=0;int frozenSource=data.readInt();float openThreshold=data.readFloat();boolean live=data.readInt()!=0;boolean appEnabled=data.readInt()!=0;float closedThreshold=FoldThreshold.sanitizeClosed(data.readFloat());float effectiveAngle=FoldThreshold.effectiveAngle(angle,closedThreshold);long probeRequest=data.dataAvail()>=8?data.readLong():0;
   float fadeSmoothing=data.dataAvail()>=4?data.readFloat():FadeSettings.DEFAULT_SMOOTHING;
   float fadeGradualness=data.dataAvail()>=4?data.readFloat():FadeSettings.DEFAULT_GRADUALNESS;
   String mode=data.dataAvail()>0?AnimationModePolicy.sanitize(data.readString()):AnimationModePolicy.DEFAULT;
   boolean debug=data.dataAvail()>=4&&data.readInt()!=0;
   boolean effectAllowed=animationMode.update(mode,effectiveAngle,last>0&&heartbeat-last<750);
   boolean mirrorMode=AnimationModePolicy.mirrors(mode);
   appEnabled=appEnabled&&effectAllowed;
   if(rotation==null)rotation=new FoldRotationHold(caller/100000);
   rotation.update(appEnabled&&unlocked,last>0&&heartbeat-last<750,effectiveAngle,openThreshold);
   if(fade==null)fade=new HandoffFade();
   fade.settings(fadeSmoothing,fadeGradualness,mirrorMode&&!debug,openThreshold);
   fade.update(live&&!dual&&appEnabled&&!handoff.probeHolding(),effectiveAngle,last>0&&heartbeat-last<750);
   if(expansion!=null){expansion.motion(effectiveAngle,openThreshold);expansion.enabled(mirrorMode&&live&&!dual&&appEnabled&&!handoff.probeHolding());}
   if(mirrorMode && live && !dual && unlocked && appEnabled && handoff.active() && !handoff.probeHolding() && angle>=98f && last>0 && heartbeat-last<750 && expansion!=null)expansion.holdBeforeRelease();
   boolean wasCoverHeld=handoff.active();
   if(!effectAllowed){handoff.release();concurrent.release();}else if(dual){handoff.release();concurrent.update(effectiveAngle,last>0&&heartbeat-last<2000,unlocked,primaryInner,secondaryReady,frozenSource,openThreshold);}else{concurrent.release();handoff.update(effectiveAngle,last>0&&heartbeat-last<750,unlocked,live&&appEnabled,openThreshold,probeRequest);}
   if(wasCoverHeld && !handoff.active() && expansion!=null)expansion.releaseReturned();
   if(handoff.probeHolding()&&fade!=null)fade.update(false,effectiveAngle,false);
   if(expansion!=null&&handoff.probeHolding())expansion.enabled(false);
   previewAllowed=mirrorMode&&effectAllowed&&!handoff.probeNative()&&CoverPreviewPolicy.allowed(live,dual,primaryInner,unlocked,handoff.active());
   if(!previewAllowed)mirror.close();
   continuity.sample(handoff.probeHolding(),handoff.probeStatus());
   Bundle b=new Bundle();b.putString("rotationHold",rotation==null?"Rotation hold idle":rotation.status);b.putBoolean("effectAllowed",effectAllowed);b.putString("handoffFade",fade==null?"Handoff fade idle":fade.status);b.putString("continuityProbe",handoff.probeStatus());b.putString("continuityTrace",continuity.report());b.putString("bridgeTrace",expansion==null?"No bridge":expansion.trace);b.putString("expansion",expansion==null?"Expansion idle":expansion.status);b.putBoolean("coverPreview",previewAllowed);b.putString("state",state);b.putString("readerDiagnostics",state+"; wallpaper log lines="+logLines+"; parsed responses="+matchingResponses+"; rejected timestamps="+staleResponses);b.putString("mirror",mirror.status);b.putBoolean("continuityNative",handoff.probeNative());b.putBoolean("nativeInner",concurrent.secondaryHasNativeContent()||handoff.probeNative());b.putBoolean("dualActive",dual&&concurrent.active());b.putString("handoff",dual?concurrent.status:handoff.status);b.putInt("uid",android.os.Process.myUid());b.putInt("count",count);b.putInt("unique",unique.size());b.putFloat("rawAngle",angle);b.putFloat("angle",effectiveAngle);b.putFloat("min",min);b.putFloat("max",max);b.putLong("last",last);b.putString("raw",raw);reply.writeNoException();reply.writeBundle(b);return true;}
  if(code==3){stop();reply.writeNoException();return true;}return super.onTransact(code,data,reply,flags);
 }
 private synchronized void stop(){previewAllowed=false;if(rotation!=null){rotation.close();rotation=null;}if(fade!=null){fade.close();fade=null;}if(expansion!=null){expansion.close();expansion=null;}if(capture!=null){capture.close();capture=null;}mirror.close();concurrent.release();handoff.release();generation++;if(process!=null){process.destroy();process=null;}state="Stopped";}
 private synchronized void start(String action){
  stop();logLines=matchingResponses=staleResponses=0;count=0;unique.clear();angle=Float.NaN;min=180;max=0;last=0;raw="";state="Starting log reader";final int gen=generation;
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
