package org.duofold.live.experimental.b8;

import android.os.SystemClock;
import org.duofold.live.HingePausePolicy;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Hinge-driven B8, serialized independently of the angle-reader Binder loop. */
public final class LiveB8Controller {
 private static final class Input {
  final boolean enabled;final float angle,open,handoff;final long sample,heartbeat;
  Input(boolean e,float a,long s,float o,float h){enabled=e;angle=a;sample=s;open=o;handoff=h;heartbeat=SystemClock.elapsedRealtime();}
 }
 private volatile Input input=new Input(false,Float.NaN,0,172,85);
 private String lastOperation="idle";
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"duo-live-b8");t.setDaemon(true);return t;});
 private volatile boolean active,closing;
 private volatile boolean pauseRequested;
 private volatile int pauseSource;
 private volatile long pauseReturnAt,pauseFadeAt;
 private boolean pauseCanceled,softwareNativeHold;
 private static final class PauseFrame {
  final long session,request,at;final String physical;
  PauseFrame(long session,long request,String physical,long at){this.session=session;this.request=request;this.physical=physical;this.at=at;}
 }
 private volatile PauseFrame pauseFrame=new PauseFrame(0,0,"",0);
 public void pausePresented(long session,long request,String physical,long at){pauseFrame=new PauseFrame(session,request,physical,at);}
 private boolean pauseUnwindPresented(long now){
  PauseFrame a=pauseFrame;
  return a.session==sceneSession&&a.request==pauseReturnAt&&expected.equals(a.physical)&&a.at>=pauseReturnAt&&a.at<=now&&now-a.at<=250;
 }
 private long nativeSwitchAt;

 public void pause(boolean held,int source){pauseSource=source;pauseRequested=held;}
 public long pauseReturnAt(){return pauseReturnAt;}
 public long pauseFadeAt(){return pauseFadeAt;}
 public boolean paused(){return phase==12;}
 private volatile long sceneSession;
 private volatile boolean capturePauseRequested;
 private static final class SceneAck {
  final long session,commitAt,receivedAt,observedAt,heldUntil;final int revision;final String physical;
  final boolean prepared,paused,animated,nativePrimary;
  SceneAck(long s,boolean p,String id,long c,boolean drained,boolean a,boolean n,int revision,long observedAt){
   this(s,p,id,c,drained,a,n,revision,observedAt,0);
  }
  SceneAck(long s,boolean p,String id,long c,boolean drained,boolean a,boolean n,int revision,long observedAt,long heldUntil){
   this.heldUntil=heldUntil;
   this.revision=revision;this.observedAt=observedAt;
   session=s;prepared=p;physical=id==null?"":id;commitAt=c;paused=drained;animated=a;nativePrimary=n;receivedAt=SystemClock.elapsedRealtime();
  }
 }
 private volatile SceneAck sceneAck=new SceneAck(0,false,"",0,false,false,false,-1,0);
 public boolean capturePauseRequested(){return capturePauseRequested;}
 public boolean compositionProbeAllowed(){return !cleaning&&(phase==2||phase==3||phase==9);}
 private volatile String sceneSource="";
 private volatile int sceneTask=-1,sceneRevision;
 private volatile boolean sceneHome,sceneWorkspace;
 private volatile int[] sceneTasks=new int[0];
 public boolean sceneWorkspace(){return sceneWorkspace;}
 public int[] sceneTasks(){return sceneTasks.clone();}
 private boolean nativeManaged(){return sceneHome||sceneWorkspace;}
 private final LiveB8Policy.MotionLeg motionLeg = new LiveB8Policy.MotionLeg();
 private boolean primaryRequestedThisSession;
 private float pausedAngle;
 public boolean sceneHome(){return sceneHome;}
 private volatile CaptureAck captureAck=new CaptureAck(0,"",-1);
 private volatile PrimaryAck primaryAck=new PrimaryAck(0,0,"",0);
 private volatile long primaryCheck;
    public int captureDisplay() {
        if (nativeReadinessOnly()) return -1;
        if (direct && (phase==5 || phase==10)) return verified>=0 ? 0 : -1;
        if (nativeManaged()) return -1;
        // On the direct path outgoing coverage authorizes promotion. Capture an
        // app only after its new primary is verified, as we already do for Home.
        if (this.phase == 3) {
            return 1;
        }
        return this.phase == 2 ? 0 : -1;
    }

    private static final class CaptureAck {
        final String physical;
        final int revision;
        final long session;

        CaptureAck(long s, String p, int r) {
            this.session = s;
            this.physical = p;
            this.revision = r;
        }
    }

    public String capturePhysical() {
        if (nativeReadinessOnly()) return "";
        return (this.phase == 3 || !direct && this.phase == 9 || direct&&(phase==5||phase==10)&&verified>=0) ? this.expected : "";
    }

    public void captured(long session, String physical, int revision) {
        this.captureAck = new CaptureAck(session, physical == null ? "" : physical, revision);
    }

    private boolean destinationCaptured() {
        CaptureAck a = this.captureAck;
        return a.session == this.sceneSession && a.revision == this.sceneRevision && this.expected.equals(a.physical);
    }

    private static final class PrimaryAck {
        final long at;
        final long startedAt;
        final long check;
        final String physical;
        final long session;

        PrimaryAck(long s, long c, String p, long a) { this(s,c,p,a,a); }
        PrimaryAck(long s, long c, String p, long a, long began) {
            this.session = s;
            this.check = c;
            this.physical = p == null ? "" : p;
            this.at = a;
            this.startedAt = began;
        }
    }

    public long primaryCheck() {
        if ((this.phase != 5 && this.phase != 10 && this.phase != 11) || this.verified < 0) {
            return 0L;
        }
        return this.primaryCheck;
    }

    public String primaryCheckPhysical() {
        return primaryCheck() > 0 ? this.expected : "";
    }

    public void primaryReady(long session, long check, String physical, long at) {
        primaryReady(session,check,physical,at,at);
    }
    public void primaryReady(long session, long check, String physical, long at, long began) {
        this.primaryAck = new PrimaryAck(session, check, physical, at, began);
    }

    private long readySince=-1, readyStreakCheck=-1, lastReadyAt=-1;
    private boolean nativeAppReady(long now) {
        PrimaryAck a = this.primaryAck;
        boolean valid=a.check == this.primaryCheck && a.session == this.sceneSession && this.expected.equals(a.physical)
                && a.startedAt >= this.verified && a.at >= a.startedAt && a.at > 0 && a.at <= now && a.at-a.startedAt <= 750;
        if(!valid){readySince=lastReadyAt=-1;return false;}
        // A pending query is not a failed query. Its previous successful result can
        // expire for release without erasing the streak between bounded observations.
        if(now-a.at>250){if(now-a.at>750)readySince=lastReadyAt=-1;return false;}
        if(readySince<0||readyStreakCheck!=primaryCheck||a.at-lastReadyAt>750){readySince=a.at;readyStreakCheck=primaryCheck;}
        if(a.at<lastReadyAt){readySince=lastReadyAt=-1;return false;}
        lastReadyAt=a.at;
        // Two distinct successful observations, not one HAS_DRAWN result reused by ticks.
        return a.at-readySince>=100;
    }

 public int sceneRevision(){return sceneRevision;}
 public boolean livePresentation(){return phase==2&&!cleaning;}
 public long sceneSession(){return sceneSession;}
 public int sceneTask(){return sceneTask;}
 public String sceneSource(){return sceneSource;}
 public void presentation(long session,boolean prepared,String physical,long commitAt,boolean paused){presentation(session,prepared,physical,commitAt,paused,false,false);}
 public void presentation(long session,boolean prepared,String physical,long commitAt,boolean paused,boolean animated,boolean nativePrimary){presentation(session,prepared,physical,commitAt,paused,animated,nativePrimary,-1,0);}
 public void presentation(long session,boolean prepared,String physical,long commitAt,boolean paused,
                          boolean animated,boolean nativePrimary,int revision,long observedAt){
  sceneAck=new SceneAck(session,prepared,physical,commitAt,paused,animated,nativePrimary,revision,observedAt);}
 public void heldPresentation(long session,long heldUntil){
  // Invalidate all readiness fields before publishing liveness for a frozen frame.
  captured(session,"",-1);
  primaryReady(session,0,"",0);
  sceneAck=new SceneAck(session,false,"",0,false,false,false,-1,0,heldUntil);
 }
 private boolean presentationReady(long now){return presentationReady(sceneAck,now);}
 private boolean presentationReady(SceneAck a,long now){return sceneSession>0&&a.session==sceneSession&&a.prepared&&(!direct||sceneMapped())&&now-a.receivedAt<=250;}
 private boolean presentationAlive(long now){return presentationAlive(sceneAck,now);}
 private boolean presentationAlive(SceneAck a,long now){return sceneSession>0&&a.session==sceneSession&&now-a.receivedAt<=750&&(a.heldUntil==0||(direct&&now<a.heldUntil&&a.heldUntil-a.receivedAt<=LiveB8Policy.STALE_MS));}
 private boolean primaryPresented(long now){return primaryPresented(sceneAck,now);}
 private boolean primaryPresented(SceneAck a,long now){return presentationAlive(a,now)&&(!direct||sceneMapped())&&expected.equals(a.physical)&&a.commitAt>=holdStarted&&a.commitAt<=now&&now-a.commitAt<=250;}

 private volatile String status="Live B8 idle";
 private final ArrayDeque<String> history=new ArrayDeque<>();
 private final boolean direct;private volatile LiveB8Scene scene;
 public boolean directScene(){return direct;}
 // Removing an effect needs fresh native window/layout evidence, not another
 // effect frame. A renderer can be stuck on the pre-restoration rotation.
 public boolean nativeReadinessOnly(){return direct&&(phase==10||phase==5&&nativeReturn);}
 public String directStage(){return !direct?"":phase==9?"BEFORE_PRIMARY":phase==5?"AFTER_PRIMARY":phase==10?"CANCEL_RECOVERY":phase==11?"PAUSE_RETURN":phase==12?"PAUSE_SETTLED":"";}
 public boolean sceneMapped(){LiveB8Scene s=scene;return s!=null&&s.mapped();}
 public Object sceneHandles(long token){LiveB8Scene s=scene;return token==sceneSession&&s!=null?s.handles():null;}
 private LiveB8EndpointCheck endpointCheck;
 private LiveB8Power completionPower;
 private LiveB8Task completionTask;
 private LiveB8Power power;private LiveB8Brightness brightness;private LiveB8Bridge shield;private LiveB8Task task;
 // Preserve a native group across a cover collapse; discard when another app/Home starts.
 private LiveB8Task rememberedWorkspace;
 private File dir;private final LiveB8Evidence evidence;private volatile int thermal;private long shieldStarted;private volatile String lastResources="No shield allocated";
 private final File diagnosticRoot;
 private volatile int phase;private int current,target;
 private long started,renewed,deadline,stableSince=-1,verified=-1,preparedAt,holdStarted,unreadySince=-1,brightnessWaitSince=-1;
 private String candidate="",expected="",inner="",cover="";
 private boolean closedStartBlocked;
 private volatile boolean nativeReturn;
 private boolean failed,primaryFocusRequested,brightnessObserved,presentationUnmasked,recoveryEndpointSeen,brightnessBlocked;private volatile boolean cleaning;
 private long endpointObservedAt;
 private long lastUnmaskWait;private String unmaskWait="";
 public interface RotationGate {boolean verified(long session,String primary,int first,String secondary,int second);}
 private final RotationGate rotationGate;
 private long rotationWaitSince=-1;
 public long rotationSession(){return cleaning||closing||phase==10||phase==12||phase==13?0:sceneSession;}
 public LiveB8Controller(RotationGate gate){this(new File("/data/local/tmp"),true,LiveB8Scene.supported(),gate);}
 LiveB8Controller(File root,boolean scheduled){this(root,scheduled,false,null);}
 LiveB8Controller(File root,boolean scheduled,boolean independent,RotationGate gate){rotationGate=gate;direct=independent;diagnosticRoot=root;evidence=new LiveB8Evidence(root,scheduled);if(scheduled)worker.scheduleWithFixedDelay(this::tick,0,25,TimeUnit.MILLISECONDS);}
 public void update(boolean enabled,float angle,long sample,float open,float handoff){input=new Input(enabled,angle,sample,open,handoff);}
 public void thermal(int value){thermal=value;}
 public boolean ownsRoute(){return active||cleaning;}
 public boolean finished(){return worker.isShutdown();}
 public String status(){return status;}
 public synchronized String report(){return status+"\nHinge-driven; commits and mapping checks are not visible-pixel proof. "+(direct?"The primary switches after the outgoing snapshot commits and both rotation and brightness pass.":"The legacy route uses the saved handoff band.")+"\n"+String.join("\n",history)+"\nResources: "+lastResources+"\n"+evidence.report();}
 private synchronized void log(String text){status="Live B8: "+text;history.addLast(SystemClock.elapsedRealtime()+" "+text);while(history.size()>80)history.removeFirst();evidence.record(dir,text);}
 private void unlocked()throws Exception{
  Object wm=PowerExperiment.service("window","android.view.IWindowManager"),pm=PowerExperiment.service("power","android.os.IPowerManager");
  if((boolean)Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm)||!(boolean)Class.forName("android.os.IPowerManager").getMethod("isInteractive").invoke(pm))throw new IllegalStateException("Locked or noninteractive");
 }
 private void createFolder()throws Exception{
  File base=diagnosticRoot;String prefix="duo-live-b8-"+android.os.Process.myUid()+"-";
  File[] old=base.listFiles(f->f.isDirectory()&&f.getName().startsWith(prefix));
  if(old!=null){Arrays.sort(old,Comparator.comparingLong(File::lastModified));for(int n=0;n<old.length-5;n++)delete(old[n]);}
  dir=new File(base,prefix+SystemClock.elapsedRealtime());if(!dir.mkdir())throw new IOException("B8 diagnostic folder unavailable");android.system.Os.chmod(dir.getPath(),0700);
 }
 private static void delete(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)if(c.isFile())c.delete();f.delete();}
 private void begin()throws Exception{
  begin(false);
 }
 private void selectSource(LiveB8Task selected)throws Exception{
  if(direct)selected.selectNativePrimary();else selected.selectSourceOnly();
 }
 private void selectTransfer(LiveB8Task selected)throws Exception{
  if(direct)selected.selectNativePrimary();else selected.selectOnly();
 }
 private void begin(boolean resume)throws Exception{
  lastOperation="source preparation";
  if(LiveB8Power.baseState()==0){
   if(!closedStartBlocked){log("START_BLOCKED base=CLOSED; awaiting physical reopening; no power/state request");closedStartBlocked=true;}
   if(resume)cleanup();return;
  }
  closedStartBlocked=false;endpointCheck=null;
  if(!resume)power=new LiveB8Power();
  completionPower=power;
  pauseReturnAt=pauseFadeAt=nativeSwitchAt=0;pauseCanceled=false;softwareNativeHold=false;brightnessBlocked=false;
  active=true;primaryRequestedThisSession=false;endpointObservedAt=0;recoveryEndpointSeen=false;unreadySince=-1;brightnessWaitSince=-1;createFolder();current=power.primary();
  float angle=input.angle;
  int initialTarget=resume && angle!=pausedAngle ? (angle>pausedAngle ? 1 : 2) : 3-current;
  motionLeg.start(initialTarget,angle);
  started=SystemClock.elapsedRealtime();
  if(!resume&&power.state(false)!=power.state(true))throw new IllegalStateException("Another device-state owner is active");
  String pkg=LiveB8Task.focusedPackage();if(pkg==null)throw new IllegalStateException("No source app or workspace");
  task=new LiveB8Task(dir,pkg,rememberedWorkspace);selectSource(task);completionTask=task;rememberedWorkspace=task.isWorkspace()?task:null;sceneHome=task.isHome();sceneWorkspace=task.isWorkspace();sceneTasks=task.workspaceTasks();if(nativeManaged()&&!direct)throw new IllegalStateException("Native layout requires independent physical layers");sceneTask=task.taskId();sceneSource=LiveB8Power.physical(power.info(0));sceneRevision=0;sceneSession=started;
  log("SOURCE_PREPARE task="+sceneTask+" package="+pkg+" home="+sceneHome+" workspace="+Arrays.toString(sceneTasks)+"; exact capture overlaps secondary wake");
  power.request(current);power.on();renewed=SystemClock.elapsedRealtime();phase=1;deadline=renewed+6000;
  if(direct&&!nativeManaged()){
   closeBrightness();brightness=new LiveB8Brightness(dir);preparedAt=SystemClock.elapsedRealtime();brightnessObserved=false;
   brightness.prepareEarlyAsync();
   log("BRIGHTNESS_READ_STARTED; overlaps panel wake and source capture; refresh waits for both ON");
  }
  log("PREWARM primary="+current+"; hinge starts session; both ON requested");
 }
 private void identities()throws Exception{
  Object a=power.info(0),b=power.info(1);String x=LiveB8Power.physical(a),y=LiveB8Power.physical(b);
  long aa=(long)LiveB8Power.number(a,"logicalWidth")*LiveB8Power.number(a,"logicalHeight"),bb=(long)LiveB8Power.number(b,"logicalWidth")*LiveB8Power.number(b,"logicalHeight");
  if(aa==bb||x.equals(y))throw new IllegalStateException("Ambiguous physical panels");inner=aa>bb?x:y;cover=aa>bb?y:x;
 }
 private boolean homeBrightnessPreparation(){return direct&&sceneHome;}
 private void closeBrightness(){
  if(brightness==null)return;
  brightness.close();
  String result=brightness.flashReport();
  if(!result.isEmpty())log("OUTGOING_FLASH_HOLD session="+sceneSession+" "+result);
  brightness=null;
 }
 private void prepareBrightness()throws Exception{if(brightnessBlocked)return;closeBrightness();brightness=new LiveB8Brightness(dir);preparedAt=SystemClock.elapsedRealtime();brightnessObserved=false;if(homeBrightnessPreparation())brightness.prepareHomeAsync();else brightness.prepareAsync();}
 private String primaryGeometry()throws Exception{
  Object d=power.info(0);if(!expected.equals(LiveB8Power.physical(d))||LiveB8Power.number(d,"state")!=2||LiveB8Power.number(d,"committedState")!=2)throw new IllegalStateException("Destination primary not ON/committed");
  if(task!=null){String geometry=task.verifyPrimary(expected);observeNativeTasks();return geometry;}
  return expected+":"+LiveB8Power.number(d,"logicalWidth")+"x"+LiveB8Power.number(d,"logicalHeight")+":"+LiveB8Power.number(d,"rotation");
 }
 private void observeNativeTasks(){
  if(nativeManaged()&&task!=null&&(sceneTask!=task.taskId()||!Arrays.equals(sceneTasks,task.workspaceTasks()))){
   sceneTask=task.taskId();sceneTasks=task.workspaceTasks();sceneRevision++;resetGeometry();
   log((sceneWorkspace?"WORKSPACE_TASKS_OBSERVED":"HOME_TASK_OBSERVED")+" task="+sceneTask+" members="+Arrays.toString(sceneTasks)+"; native placement, no task move");
  }
 }
 private void releaseVerifiedScene(){
  // Readiness has passed after native policy settled. Revoke root stacks now;
  // power/brightness teardown may finish later, while the real screen is usable.
  if(direct&&scene!=null){scene.close();log("NATIVE_REVEAL_SUBMITTED endpointToSubmitMs="+(endpointObservedAt>0?SystemClock.elapsedRealtime()-endpointObservedAt:-1)+"; native coverage removal submitted before resource cleanup; not optical timing");}
 }
 private boolean settle(String value,long now){if(!value.equals(candidate)){candidate=value;stableSince=now;verified=-1;}return LiveB8Policy.stable(now,stableSince);}
 private void resetGeometry(){candidate="";stableSince=-1;verified=-1;readySince=lastReadyAt=-1;readyStreakCheck=-1;}
 /** Remove only the frozen shield. The opaque app presentation still covers both
  * panels; native layout/HAS_DRAWN remain separate gates for handoff completion. */
 private void unmaskPresentation(long now,Input i)throws Exception{
  if(presentationUnmasked||shield==null)return;
  Object d=power.info(0);String physical=LiveB8Power.physical(d);
  // Binder/display work may take longer than a frame. Check against a clock
  // sampled AFTER it, or a valid newer animation commit looks future-dated.
  now=SystemClock.elapsedRealtime();
  // All evidence in this decision must describe the same acknowledged frame.
  SceneAck a=sceneAck;
  String waiting=!a.nativePrimary?"native-panel image":!primaryPresented(a,now)?"fresh primary presentation commit":
   !expected.equals(physical)||LiveB8Power.number(d,"state")!=2||LiveB8Power.number(d,"committedState")!=2?"primary ON mapping":
   !LiveB8Policy.nativePanelMatches(i.angle,i.open,physical,inner,cover)?"endpoint physical panel":
   !presentationReady(a,now)&&!LiveB8Policy.endpoint(i.angle,i.open)?"both panel presentations":"";
  if(!waiting.isEmpty()){
   if(!waiting.equals(unmaskWait)||now-lastUnmaskWait>=500){unmaskWait=waiting;lastUnmaskWait=now;log("UNMASK_WAIT reason="+waiting+" shieldAgeMs="+(now-shieldStarted)+" commitAgeMs="+(a.commitAt>0?now-a.commitAt:-1));}
   return;
  }
  // Both composites must be coherent in mid-fold. At a native endpoint Android
  // may turn the outgoing display OFF; only the visible primary needs coverage.
  shield.reveal();presentationUnmasked=true;
  log("PRESENTATION_UNMASK_SUBMITTED target="+target+" shieldAgeMs="+(now-shieldStarted)+" primaryFrame="+(a.animated?"animated":"plain")+"; opaque native-panel app coverage retained; native readiness pending; software evidence");
 }
 private int wanted(Input i){
  if (!direct || LiveB8Policy.endpoint(i.angle,i.open))
   return LiveB8Policy.target(i.angle,current,i.handoff,i.open);
  return motionLeg.observe(i.angle);
 }
 private boolean outgoingReady(SceneAck ack,long now) {
  String outgoing=current==1 ? inner : cover;
  return presentationReady(ack,now) && ack.nativePrimary && outgoing.equals(ack.physical)
    && ack.revision==sceneRevision && ack.commitAt>=sceneSession && ack.commitAt<=ack.observedAt
    && ack.observedAt<=now && now-ack.observedAt<=250;
 }
 private boolean rotationReady()throws Exception{
  Object a=power.info(0),b=power.info(1);
  return a!=null&&b!=null&&rotationGate!=null&&rotationGate.verified(sceneSession,
    LiveB8Power.physical(a),LiveB8Power.number(a,"rotation"),LiveB8Power.physical(b),LiveB8Power.number(b,"rotation"));
 }
 private boolean awaitRotation(long now)throws Exception{
  if(rotationReady()){
   if(rotationWaitSince>=0)log("ROTATION_PAIR_READY elapsedMs="+(now-rotationWaitSince));
   rotationWaitSince=-1;return true;
  }
  if(rotationWaitSince<0){rotationWaitSince=now;log("ROTATION_PAIR_WAIT session="+sceneSession);}
  if(LiveB8Policy.endpoint(input.angle,input.open)||now-rotationWaitSince>=2000)
   nativeFallback("rotation hold unverified on physical panel pair");
  return false;
 }
 private void startTransfer(int wanted,boolean release)throws Exception{
  brightnessWaitSince=-1;rotationWaitSince=-1;
  // Each subsequent leg gets its own sample validation and first pair of writes.
  if (direct && !release && primaryRequestedThisSession) prepareBrightness();
  primaryFocusRequested=false;if(!direct||!release||wanted!=current)sceneRevision++;target=wanted;nativeReturn=release;expected=target==1?inner:cover;resetGeometry();deadline=SystemClock.elapsedRealtime()+(direct&&release?1800:LiveB8Policy.TRANSITION_MS);
  if(nativeManaged()){
   task.verifyPrimary(LiveB8Power.physical(power.info(0)));observeNativeTasks();capturePauseRequested=true;
   if(release){holdStarted=SystemClock.elapsedRealtime();presentationUnmasked=true;power.nativeRelease();phase=5;log("HOME_ENDPOINT_RELEASE; native Home readiness required");}
   else{phase=9;log((sceneWorkspace?"WORKSPACE_PREPARED":"HOME_PREPARED")+" target="+target+"; source projection retained; native layout stays under Android control");}
   return;
  }
  task=null;
  if(SystemClock.elapsedRealtime()-preparedAt>30000)prepareBrightness();
  if(!release){String pkg=LiveB8Task.focusedPackage();
   if(pkg!=null){
    task=new LiveB8Task(dir,pkg);selectTransfer(task);if(task.taskId()!=sceneTask)throw new IllegalStateException("Focused app changed during presentation");completionTask=task;
    capturePauseRequested=true;
    if(direct){
     phase=9;log("NATIVE_APP_PREPARED target="+target+" task="+sceneTask+" package="+pkg+"; app retained on logical D0; Android owns placement; no task move or focus writes");
    }else{phase=8;log("TRANSFER_PREPARE target="+target+" package="+pkg+"; presentation precedes task move");}
    return;
   }
   throw new IllegalStateException("No routable fullscreen app; refusing to freeze destination wallpaper");
  }else{
   String pkg=LiveB8Task.focusedPackage();if(pkg==null)throw new IllegalStateException("No fullscreen app for endpoint shield");task=new LiveB8Task(dir,pkg);selectTransfer(task);if(task.taskId()!=sceneTask)throw new IllegalStateException("Focused app changed before endpoint");completionTask=task;
   // A completed handoff already covers this physical primary, including a
   // plain opaque native-panel frame when late animation was skipped. Ending
   // the concurrent-state request needs fresh native verification, not another
   // capture/frozen shield over the presentation that is already committed.
   Object d=power.info(0);long now=SystemClock.elapsedRealtime();SceneAck a=sceneAck;
   if(target==current&&shield==null&&a.nativePrimary&&presentationReady(a,now)&&primaryPresented(a,now)
       &&expected.equals(LiveB8Power.physical(d))&&LiveB8Power.number(d,"state")==2&&LiveB8Power.number(d,"committedState")==2){
    Input latest=input;
    if(!LiveB8Policy.usable(latest.enabled,now,latest.heartbeat,latest.sample,latest.angle)||closing)throw new IllegalStateException("Input expired before native endpoint release");
    // Task layout can lag the already coherent presentation. Phase 5 verifies
    // that layout under coverage instead of aborting on its first stale sample.
    holdStarted=now;capturePauseRequested=true;presentationUnmasked=true;
    power.nativeRelease();phase=5;
    log("ENDPOINT_RELEASE_PRESENTATION target="+target+" primaryFrame="+(a.animated?"animated":"plain")+"; retained native-panel coverage; no new shield or captures; fresh native readiness required");return;
   }
   log("ENDPOINT_RELEASE target="+target+"; native mapping will be verified");
  }
  capturePauseRequested=true;phase=8;
 }
 private void nativeFallback(String reason)throws Exception{
  long observed=SystemClock.elapsedRealtime();Input latest=input;
  recoveryEndpointSeen=LiveB8Policy.usable(latest.enabled,observed,latest.heartbeat,latest.sample,latest.angle)&&LiveB8Policy.endpoint(latest.angle,latest.open);
  log("NATIVE_FALLBACK "+reason+"; retaining presentation for at most 1800ms");
  // An app-side preparation failure is not an Android cancellation. Native
  // Samsung policy stays OPENED deep into closing, so releasing here strands
  // the cover OFF at 35 degrees. Preserve the angle-selected interactive panel
  // through this leg, with the same bounded unwind/readiness gates as a pause.
  if(direct&&!nativeManaged()&&!recoveryEndpointSeen&&!power.canceled&&scene!=null&&presentationAlive(observed)
      &&LiveB8Policy.usable(latest.enabled,observed,latest.heartbeat,latest.sample,latest.angle)){
   unlocked();if(thermal>=3||closing)throw new IllegalStateException("Recovery no longer eligible");
   softwareNativeHold=true;
   log("SOFTWARE_NATIVE_RETURN angle="+latest.angle+"; 90-degree panel rule retained after "+reason);
   returnFromPause(observed);return;
  }
  closeBrightness();
  power.nativeRelease();
  if(task!=null)task.restore();
  long now=SystemClock.elapsedRealtime();
  phase=10;deadline=now+1800;holdStarted=now;resetGeometry();
  // A software recovery at a partial fold used to reveal the plain native
  // screen abruptly. Reuse retained pixels for the same reverse animation;
  // never reacquire power, a capture, or a canceled concurrent request here.
  if(direct&&!recoveryEndpointSeen&&scene!=null&&presentationAlive(now)){
   pauseReturnAt=now;pauseFadeAt=0;capturePauseRequested=true;
   log("RECOVERY_UNWIND; retained effect returns before native reveal; no power reassertion");
  }
 }
 private boolean waitForPresentation(long now,String reason)throws Exception{
  boolean ready=direct && phase==9 && !nativeReturn ? outgoingReady(sceneAck,now) : presentationReady(now);
  if(ready){unreadySince=-1;return false;}
  if(unreadySince<0)unreadySince=now;
  // Recover under existing coverage, with no new shield or repeated power request.
  // Phase 10 restores native policy/task and requires fresh native readiness,
  // or releases at its fixed deadline if that evidence never arrives.
  if(now-unreadySince>=750)nativeFallback(reason);
  return true;
 }
 private void returnFromPause(long now)throws Exception{
  // Direct Duo observes Android's task placement; legacy routing can restore
  // its own move. Retain the selected panel until the native screen is ready.
  pausedAngle=input.angle;pauseReturnAt=now;pauseFadeAt=0;primaryFocusRequested=false;capturePauseRequested=true;sceneRevision++;
  closeBrightness();
  if(task==null){String pkg=LiveB8Task.focusedPackage();if(pkg==null)throw new IllegalStateException("Pause source unavailable");task=new LiveB8Task(dir,pkg);selectSource(task);if(task.taskId()!=sceneTask)throw new IllegalStateException("Pause task changed");}
  task.restore();
  // Restoring brightness/tasks can cross Binder calls. Recheck cancellation,
  // endpoint, freshness and lock AFTER them, immediately before panel control.
  Input latest=input;long at=SystemClock.elapsedRealtime();
  if(power.canceled||closing||thermal>=3||!LiveB8Policy.usable(latest.enabled,at,latest.heartbeat,latest.sample,latest.angle)
      ||LiveB8Policy.endpoint(latest.angle,latest.open))throw new IllegalStateException("Pause return canceled during restoration");
  unlocked();
  int source=softwareNativeHold?anglePrimary(input.angle,power.primary()):pauseSource==1||pauseSource==2?pauseSource:power.primary();
  if(power.primary()!=source)power.request(source);
  current=target=source;expected=source==1?inner:cover;power.on();renewed=now;
  resetGeometry();phase=11;deadline=now+1800;
  if(shield!=null)shield.reveal();
  log("PAUSE_RETURN source="+source+"; restoring interactive task; easing retained presentation");
 }
 private static int anglePrimary(float angle,int active){return angle<90f?2:angle>90f?1:active;}
 private void tickPause(long now)throws Exception{
  if(power.canceled){log("PAUSE_ANDROID_CANCELED; no reassertion");failed=true;pauseCanceled=true;cleanup();return;}
  if(phase==12){
   // No screenshot or drawing resource survives the settled hold. Only the
   // leased panel choice remains until movement, endpoint, lock, or disconnect.
   if(scene!=null){scene.close();if(!scene.closed())return;scene=null;}
   if(!softwareNativeHold&&!pauseRequested){log("PAUSE_RESUME; fresh source capture at current hinge pose");begin(true);return;}
   int wanted=softwareNativeHold?anglePrimary(input.angle,current):pauseSource==1||pauseSource==2?pauseSource:current;
   if(wanted!=current){
    current=target=wanted;expected=current==1?inner:cover;
    power.on();power.request(current);nativeSwitchAt=now;renewed=now;resetGeometry();
    log("PAUSE_NATIVE_CONTINUE target="+current+"; no new capture, renderer or animation session");return;
   }
   if(nativeSwitchAt>0){
    if(now-nativeSwitchAt>=1800){log("PAUSE_NATIVE_CONTINUE_TIMEOUT; release to Android");failed=true;cleanup();return;}
    try{if(!settle(primaryGeometry(),now))return;}catch(Exception pending){resetGeometry();return;}
    power.park();renewed=now;nativeSwitchAt=0;log("PAUSE_NATIVE_CONTINUE_READY target="+current);return;
   }
   if(now-renewed>=500){if(power.primary()!=current)throw new IllegalStateException("Paused source mapping changed");power.park();renewed=now;}
   return;
  }
  if(now>=deadline||!presentationAlive(now)){log("PAUSE_RETURN_TIMEOUT; native fallback");if(softwareNativeHold)failed=true;cleanup();return;}
  if(softwareNativeHold){
   int selected=anglePrimary(input.angle,current);
   if(selected!=current){
    current=target=selected;expected=current==1?inner:cover;
    power.on();power.request(current);renewed=now;resetGeometry();pauseFadeAt=0;
    if(scene!=null)scene.opacity(1f);
    log("SOFTWARE_NATIVE_RETARGET angle="+input.angle+" target="+current+"; original recovery deadline retained");
   }
  }
  if(now-renewed>=500){power.on();renewed=now;}
  try{
   String geometry=primaryGeometry();now=SystemClock.elapsedRealtime();
   if(settle(geometry,now)&&verified<0){verified=now;primaryCheck++;if(task!=null&&!primaryFocusRequested){primaryFocusRequested=true;task.focus();}log("PAUSE_PRIMARY_VERIFIED check="+primaryCheck+" physical="+expected);}
   if(verified<0||!nativeAppReady(now)||now-pauseReturnAt<HingePausePolicy.EASE_MS||direct&&!pauseUnwindPresented(now))return;
  }catch(Exception pending){resetGeometry();pauseFadeAt=0;if(scene!=null)scene.opacity(1f);return;}
  if(pauseFadeAt==0){pauseFadeAt=now;log("PAUSE_DISSOLVE; fresh native task ready; software evidence only");}
  if(scene!=null)scene.opacity(1f-HingePausePolicy.fraction(pauseFadeAt,now,HingePausePolicy.FADE_MS));
  if(now-pauseFadeAt<HingePausePolicy.FADE_MS)return;
  // Revoke the scene before releasing renderer resources. A late capture or
  // renderer callback cannot resurrect coverage over the usable screen.
  sceneSession=0;capturePauseRequested=false;
  if(scene!=null)scene.close();
  if(shield!=null){shield.close();if(!shield.isClosed())return;shield=null;}
  if(task!=null){task.restore();task=null;}
  power.park();renewed=now;phase=12;
  log("PAUSE_SETTLED source="+current+"; native screen interactive; animation layers removed");
 }
 private void brightnessUnavailable(String reason)throws Exception{
  if(!direct||nativeReturn){nativeFallback(reason);return;}
  // A failed preflight is not hinge stillness or an endpoint. Leave the current
  // phase, task placement, physical layers, power ownership and rotation hold
  // alone, for Home/workspaces as well as apps. Stop only the failed brightness
  // worker; never retry it or authorize an unvalidated handoff in this session.
  // The existing pause, endpoint, cancellation and session bounds still apply.
  if(!brightnessBlocked){
   brightnessBlocked=true;closeBrightness();
   log("BRIGHTNESS_BLOCKED "+reason+"; handoff withheld; animation retained until hinge pause/endpoint; no recovery requested");
  }
 }
 private boolean brightnessPrepared(long now)throws Exception{
  if(brightnessBlocked)return false;
  if(brightnessWaitSince<0){brightnessWaitSince=now;log("BRIGHTNESS_GATE elapsedMs="+(now-started)+" preparing="+brightness.preparing());}
  if(brightness.preparing()){
   if(now-brightnessWaitSince>=2500)brightnessUnavailable("brightness preparation timed out");
   return false;
  }
  try{brightness.requirePrepared();}catch(Exception unavailable){brightnessUnavailable("brightness unavailable: "+unavailable);return false;}
  return true;
 }
 private void arm()throws Exception{
  long now=SystemClock.elapsedRealtime();
  if(!brightnessPrepared(now))return;
  if(direct){
   if(!nativeReturn&&!awaitRotation(now))return;
   if(!nativeReturn && !outgoingReady(sceneAck,now))return;
   if(!nativeReturn&&!nativeManaged()&&!sceneAck.paused)return;
   brightnessWaitSince=-1;presentationUnmasked=true;shieldStarted=now;
   lastResources="Independent moving app layers; no handoff display captures or static shield";
   log("MOVING_COVERAGE_HANDOFF target="+target+"; retained physical app renderers; no screenshot shield");promote();return;
  }
  brightnessWaitSince=-1;presentationUnmasked=false;shieldStarted=SystemClock.elapsedRealtime();shield=new LiveB8Bridge(dir,true);shield.arm();lastResources=shield.resources();phase=4;log("SHIELD_SUBMITTED target="+target+" nativeRelease="+nativeReturn);}
 private void tick(){
  try{
   long now=SystemClock.elapsedRealtime();Input i=input;
   if(!LiveB8Policy.usable(i.enabled,now,i.heartbeat,i.sample,i.angle)||closing){
    endpointCheck=null;
    if(active||power!=null){if(!cleaning)log("STOP enabled="+i.enabled+" thermal="+thermal+" sampleAge="+(now-i.sample));cleanup();}
    if(!i.enabled)failed=false;
    if(closing&&power==null)worker.shutdown();return;
   }
   // Remember a fresh endpoint even while asynchronous cleanup still owns the
   // route. A new request remains blocked until every owned handle is released.
   if(failed&&LiveB8Policy.endpoint(i.angle,i.open))failed=false;
   if(pauseCanceled&&!softwareNativeHold&&!pauseRequested){failed=false;pauseCanceled=false;}
   if(cleaning){cleanup();return;}
   if(!active)checkEndpoint();
   if(failed)return;
   if(!active){if(pauseRequested||LiveB8Policy.endpoint(i.angle,i.open))return;unlocked();if(thermal>=3){log("THERMAL_PAUSE status="+thermal);failed=true;return;}begin();return;}
   unlocked();
   if(phase==13){
    // Home pixels were already released. Let Android cancel the concurrent
    // request at CLOSED before dropping the existing ON leases. An already
    // running writer may protect ONLY the cover for this same bounded tail.
    // No capture, power renewal or new state request runs here.
    if(!LiveB8Policy.endpoint(i.angle,i.open)||i.angle>0||now>=deadline||thermal>=3){
     log("HOME_CLOSE_POWER_RELEASE reason="+(thermal>=3?"thermal":now>=deadline?"400ms bound":"new motion")+"; no image hold");cleanup();return;
    }
    boolean nativeCover=power.canceled&&power.state(true)==0&&power.state(false)==0&&cover.equals(LiveB8Power.physical(power.info(0)))&&LiveB8Power.number(power.info(0),"state")==2&&LiveB8Power.number(power.info(0),"committedState")==2;
    if(!nativeCover)stableSince=-1;
    else if(stableSince<0)stableSince=now;
    else if(now-stableSince>=100){log("HOME_CLOSE_POWER_RELEASE Android canceled; native cover stable 100ms; no image hold");cleanup();}
    return;
   }
   if(pauseReturnAt>0&&phase!=10&&LiveB8Policy.endpoint(i.angle,i.open)){log("PAUSE_ENDPOINT_RELEASE");cleanup();return;}
   if(endpointObservedAt==0&&LiveB8Policy.endpoint(i.angle,i.open)){endpointObservedAt=now;log("ENDPOINT_OBSERVED angle="+i.angle+"; software callback time");}
   if(nativeManaged()&&LiveB8Policy.endpoint(i.angle,i.open)){
    // User-requested Home fail-open: never wait for screenshots, drawing,
    // brightness, or a second native-readiness cycle at the endpoint.
    // Revoke the session before cleanup so late capture deliveries cannot attach.
    sceneSession=0;capturePauseRequested=false;
    if(scene!=null)scene.close();
    log((sceneWorkspace?"WORKSPACE_ENDPOINT_BYPASS":"HOME_ENDPOINT_BYPASS")+" endpointToSubmitMs="+(SystemClock.elapsedRealtime()-endpointObservedAt)+"; coverage removal submitted; no screenshot wait; not optical timing");
    if(sceneHome&&i.angle<=0&&cover.equals(LiveB8Power.physical(power.info(0)))){
     boolean coverBrightness=brightness!=null&&brightness.retainCoverTail(cover);
     if(!coverBrightness)closeBrightness();
     phase=13;deadline=now+400;stableSince=-1;
     log("HOME_CLOSE_POWER_TAIL boundMs=400; existing leases only; coverBrightness="+coverBrightness+"; waiting for Android cancellation; coverage removal submitted; no image hold");return;
    }
    if(!power.canceled)power.nativeRelease();
    cleanup();return;
   }
   if (direct) motionLeg.observe(i.angle);
   if(scene!=null&&phase!=12)scene.refresh(power);
   if(thermal>=3)throw new IllegalStateException("Thermal pause status="+thermal);
   if(phase==11||phase==12){tickPause(now);return;}
   if(pauseRequested&&!power.canceled&&phase!=10){
    if(direct&&scene==null||!presentationAlive(now)){log("PAUSE_BEFORE_PRESENTATION; native fallback");cleanup();return;}
    returnFromPause(now);return;
   }
   // An Android cancellation at full close is expected. Do not reassert it.
   if(power.canceled||phase==10){
    if(phase!=10){
     log("ANDROID_CANCELED phase="+phase+"; no power reassertion");
     if(!presentationAlive(now)){cleanup();failed=!LiveB8Policy.endpoint(i.angle,i.open);return;}
     recoveryEndpointSeen=false;phase=10;deadline=now+1800;holdStarted=now;resetGeometry();
     closeBrightness();
     if(task!=null)task.restore();
     log("CANCEL_COVERAGE_WAIT budgetMs=1800; existing presentation/shield retained while native app settles");
    }
    recoveryEndpointSeen|=LiveB8Policy.endpoint(i.angle,i.open);
    // Endpoint/cancellation keeps native-readiness release rules and the
    // original fixed recovery budget, without an animation power request.
    if(pauseReturnAt>0&&(power.canceled||LiveB8Policy.endpoint(i.angle,i.open))){pauseReturnAt=pauseFadeAt=0;if(scene!=null)scene.opacity(1f);}
    if(now>=deadline||!presentationAlive(now)){log("CANCEL_COVERAGE_TIMEOUT; native readiness unconfirmed");cleanup();failed=true;return;}
    if(shield!=null&&!shield.isClosed())shield.followPhysicalStacks();
    unmaskPresentation(now,i);
    try{
     if(power.state(false)!=power.state(true))throw new IllegalStateException("Native policy pending");
     Object d=power.info(0);String physical=LiveB8Power.physical(d);
     if(!LiveB8Policy.nativePanelMatches(i.angle,i.open,physical,inner,cover))throw new IllegalStateException("Endpoint physical remap pending");
     if(!physical.equals(expected)){expected=physical;resetGeometry();}
     if(LiveB8Power.number(d,"state")!=2||LiveB8Power.number(d,"committedState")!=2)throw new IllegalStateException("Native primary OFF");
     String geometry=task!=null?task.verifyNativePrimary(expected):primaryGeometry();observeNativeTasks();
     now=SystemClock.elapsedRealtime();
     if(settle(geometry,now)&&verified<0){verified=now;primaryCheck++;log("CANCEL_PRIMARY_VERIFIED check="+primaryCheck+" physical="+expected);}
     if(verified>=0&&nativeAppReady(now)&&(direct||primaryPresented(now))){
      if(pauseReturnAt>0){
       if(!pauseUnwindPresented(now))return;
       if(pauseFadeAt==0){pauseFadeAt=now;log("RECOVERY_DISSOLVE; unwind committed and native window ready");}
       if(scene!=null)scene.opacity(1f-HingePausePolicy.fraction(pauseFadeAt,now,HingePausePolicy.FADE_MS));
       if(now-pauseFadeAt<HingePausePolicy.FADE_MS)return;
      }
      // Fast reversal can leave the endpoint before recovery finishes. Only a
      // verified recovery may reuse that observation to admit the next leg.
      failed=!recoveryEndpointSeen;
      log("CANCEL_NATIVE_READY; releasing retained coverage; endpointSeen="+recoveryEndpointSeen+"; next motion eligible after cleanup="+!failed);releaseVerifiedScene();cleanup();
     }
    }catch(Exception pending){resetGeometry();if(pauseFadeAt>0){pauseFadeAt=0;if(scene!=null)scene.opacity(1f);}}
    return;
   }
   if(brightness!=null&&!brightnessObserved&&!brightness.preparing()){brightnessObserved=true;log("BRIGHTNESS_PREPARED elapsedMs="+(now-started)+" preparationMs="+(now-preparedAt)+" status="+brightness.preparationStatus());}
   if(!nativeReadinessOnly()&&now-renewed>=500){power.on();renewed=now;}
   if(phase!=2&&phase!=9&&!(phase==8&&brightnessBlocked&&!nativeReturn)&&now>=deadline)throw new IllegalStateException("Transition readiness timeout phase="+phase);
   if(now-started>=30000)throw new IllegalStateException("Presentation session reached 30 second bound");
   // A held frame is only coverage. Wait for a fresh acknowledgement before
   // moving a task, switching a panel or evaluating destination readiness.
   SceneAck coverage=sceneAck;
   if(coverage.heldUntil>0){
    if(!presentationAlive(coverage,now))throw new IllegalStateException("Held presentation expired");
    return;
   }
   if(phase==1){if(!power.ready())return;identities();
    if(!sceneSource.equals(current==1?inner:cover))throw new IllegalStateException("Source changed during preparation");
    if(direct)scene=new LiveB8Scene(dir,power);
    if(brightness==null)prepareBrightness();else brightness.allowRefresh();
    phase=7;deadline=now+6000;log("PRESENTATION_PREPARE task="+sceneTask+" source="+sceneSource+"; waiting for both committed app composites");return;}
   if(phase==7){
    if(!(direct ? outgoingReady(sceneAck,now) : presentationReady(now))){if(LiveB8Policy.endpoint(i.angle,i.open)){log("PREPARATION_INCOMPLETE_AT_ENDPOINT; releasing to native display policy");cleanup();}return;}
    phase=2;log("PRESENTATION_READY both panels; elapsedMs="+(now-started)+"; brightness independent; task has not moved");
    if (!LiveB8Policy.endpoint(i.angle,i.open)) {
     int next=direct ? wanted(i) : 3-current;
     if (next!=current) startTransfer(next,false);
    }
    return;
   }
   if(!presentationAlive(now))throw new IllegalStateException("Presentation lease lost");
   if (direct && !nativeReturn && (phase==8 || phase==9) && !LiveB8Policy.endpoint(i.angle,i.open) && wanted(i)!=target) {
    if (task!=null && !nativeManaged()) task.restore();
    capturePauseRequested=false;sceneRevision++;phase=2;resetGeometry();unreadySince=-1;
    log("LEG_REVERSED_BEFORE_REQUEST; outgoing primary retained");return;
   }
   if(direct&&!nativeReturn&&(phase==8||phase==9||phase==2&&brightnessBlocked)&&LiveB8Policy.endpoint(i.angle,i.open)){
    nativeFallback("endpoint reached before primary request");return;
   }
   if((phase==8||phase==3)&&!nativeReturn&&LiveB8Policy.endpoint(i.angle,i.open)&&wanted(i)!=target){nativeFallback("PREPARED_RETURN_TO_SOURCE; restoring task under coverage");return;}
   if (phase==8) {
    SceneAck ack=sceneAck;
    boolean covered=direct && !nativeReturn ? outgoingReady(ack,now) : presentationReady(ack,now);
    if (covered && ack.paused) {
     if (nativeReturn) arm();
     else {
      if(direct){
       throw new IllegalStateException("Direct Duo cannot enter legacy task-move phase");
      }
      lastOperation="task.moveSelected";
      log("TASK_MOVE_BEGIN task="+sceneTask+" target="+target+" current="+current+" session="+sceneSession);
      task.moveSelected();
      phase=direct ? 9 : 3;
      resetGeometry();
      log("HINGE_MOVE target="+target+" elapsedMs="+(now-started)+"; task moved under committed coverage");
     }
    }
    return;
   }
   if(phase==2){
    if(!presentationReady(now)){
     if(LiveB8Policy.endpoint(i.angle,i.open)){nativeFallback("presentation stale at endpoint");return;}
     waitForPresentation(now,"Presentation commits stalled");
     return;
    }
    unreadySince=-1;
    int wanted=wanted(i);
    if(LiveB8Policy.endpoint(i.angle,i.open)){
     int base=power.state(true),nativePanel=base==3?1:base==0?2:0;
     if(sceneRevision==0&&nativePanel==current&&wanted==current&&sceneSource.equals(LiveB8Power.physical(power.info(0)))){log("SOURCE_ENDPOINT_RELEASE; no task move or primary remap; no shield required");cleanup();return;}
     if(nativePanel==wanted){startTransfer(wanted,true);return;}
    }
    if(wanted!=current)startTransfer(wanted,false);else if(!direct&&sceneRevision==0&&!LiveB8Policy.endpoint(i.angle,i.open))startTransfer(current==1?2:1,false);
    return;
   }
   if(phase==3){
    if(!presentationReady(now)){
     if(LiveB8Policy.endpoint(i.angle,i.open)){nativeFallback("presentation stale at endpoint");return;}
     waitForPresentation(now,"Presentation commits stalled");
     return;
    }
    unreadySince=-1;
    try{String geometry=task.destinationGeometry();SceneAck a=sceneAck;if(settle(geometry,now)&&presentationReady(a,now)&&a.paused&&destinationCaptured()){phase=9;log("DESTINATION_DENSITY "+task.configurationReport());log("DESTINATION_PREPARED target="+target+" elapsedMs="+(now-started)+"; native app capture committed; waiting for hinge");}}catch(Exception pending){resetGeometry();}return;
   }
   if(phase==9){
    if(LiveB8Policy.endpoint(i.angle,i.open)&&wanted(i)!=target){nativeFallback("PREPARED_RETURN_TO_SOURCE; restoring task under coverage");return;}
    if(waitForPresentation(now,"Prepared destination commits stalled"))return;
    if(wanted(i)==target){if(direct){arm();return;}String g;try{g=task.destinationGeometry();}catch(Exception pending){g="";}deadline=now+6000;
     if(candidate.equals(g)&&destinationCaptured()){arm();return;}sceneRevision++;resetGeometry();phase=3;log("DESTINATION_CHANGED; recapturing before shield");}
    return;
   }
   if(phase==6)return;
   if((shield==null||shield.isClosed())&&!(phase==5&&presentationUnmasked))throw new IllegalStateException("Shield no longer active");
   if(shield!=null){shield.followPhysicalStacks();lastResources=shield.resources();}
   if(phase==4){
    if(!shield.ready()||!presentationReady(now))return;
    // Keep independent screenshot buffers visible until the destination presentation
    // is committed. A live display mirror can create a cyclic compositor graph.
    // ARM_COMMIT includes visibility and capture exclusion for every shield
    // buffer. Promote on that evidence without another identical transaction.
    promote();return;
   }
   if(phase==5){
    int base=power.state(true);
    if(!nativeReturn&&LiveB8Policy.endpoint(i.angle,i.open)&&((target==1&&i.angle>=i.open&&base==3)||(target==2&&i.angle<=0&&base==0))&&expected.equals(LiveB8Power.physical(power.info(0)))&&(direct||now-shieldStarted<3000)){
     brightness.begin("live-endpoint-reuse");power.nativeRelease();nativeReturn=true;resetGeometry();if(direct)deadline=Math.min(deadline,now+1800);log(presentationUnmasked?"ENDPOINT_RELEASE retained app coverage; no new captures":"ENDPOINT_REUSE screenshot shield; no new captures");return;
    }
    unmaskPresentation(now,i);
    try{
     if(nativeReturn&&power.state(false)!=power.state(true))throw new IllegalStateException("Native state pending");
     String geometry=primaryGeometry();now=SystemClock.elapsedRealtime();
     if(settle(geometry,now)&&verified<0){verified=now;primaryCheck++;log("PRIMARY_VERIFIED target="+target+"; geometry stable 75ms");if(task!=null&&!primaryFocusRequested){primaryFocusRequested=true;task.focus();log("PRIMARY_DENSITY "+task.configurationReport());}}
     now=SystemClock.elapsedRealtime();
     if(verified>=0&&nativeAppReady(now)&&(nativeReadinessOnly()||primaryPresented(now)&&(!direct||destinationCaptured()))){if(task!=null)log("REVEAL_DENSITY "+task.configurationReport());log("PRIMARY_APP_READY target="+target+" elapsedMs="+(now-started)+" check="+primaryCheck+"; post-remap HAS_DRAWN; not optical proof");if(shield!=null)shield.reveal();phase=6;log("REVEAL target="+target+(nativeReturn?"; verified native window/layout; effect commit not required":"; matching primary presentation commit; outgoing presentation retained"));}
    }catch(Exception pending){resetGeometry();}
    return;
   }
  }catch(Throwable e){
   while(e.getCause()!=null)e=e.getCause();
   StringWriter stack=new StringWriter();e.printStackTrace(new PrintWriter(stack));
   log("ABORT "+e+" phase="+phase+" lastHandoffOperation="+lastOperation+" session="+sceneSession+" task="+sceneTask+" current="+current+" target="+target);
   evidence.record(dir,"ABORT_STACK "+stack.toString());
   failed=true;if(pauseReturnAt>0)pauseCanceled=true;cleanup();
  }
  finally{
   if(presentationUnmasked&&shield!=null&&shield.isClosed()){
    lastResources=shield.resources();log("SHIELD_REMOVED "+lastResources+"; app presentation continues");shield=null;
   }
   if(phase==6&&(shield==null||shield.isClosed())){
    if(shield!=null){lastResources=shield.resources();log("SHIELD_REMOVED "+lastResources);}current=target;shield=null;if(!nativeManaged())task=null;capturePauseRequested=false;
    if(nativeReturn){log("NATIVE_COMPLETE primary="+current);releaseVerifiedScene();cleanup();}
    else{phase=2;log("HANDOFF_COMPLETE primary="+current+"; outgoing presentation retained until endpoint");}
   }
  }
 }
 private void promote()throws Exception{
    power.on();
    try {
     if(direct&&!nativeManaged()&&!nativeReturn)brightness.beginAppHandoff(current==1?inner:cover);
     else brightness.begin(nativeReturn ? "live-endpoint" : "live-handoff");
    } catch(Exception failure) {
     if (!direct || nativeReturn) throw failure;
     brightnessUnavailable("brightness hold rejected: "+failure);return;
    }
    holdStarted=SystemClock.elapsedRealtime();
    Input latest;long at;
    if (direct && !nativeReturn) {
     unlocked();
     if(task!=null)task.verifyHandoffSource(current==1?inner:cover);
     Object display=power.info(0);
     SceneAck ack=sceneAck;
     if(!rotationReady()){nativeFallback("rotation hold changed before primary request");return;}
     latest=input;at=SystemClock.elapsedRealtime();
     if (closing || pauseRequested || power.canceled || thermal>=3
         || !LiveB8Policy.usable(latest.enabled,at,latest.heartbeat,latest.sample,latest.angle)
         || wanted(latest)!=target || !outgoingReady(ack,at)
         || display==null || !ack.physical.equals(LiveB8Power.physical(display))
         || LiveB8Power.number(display,"state")!=2 || LiveB8Power.number(display,"committedState")!=2
         || LiveB8Policy.endpoint(latest.angle,latest.open)) {
      nativeFallback("outgoing readiness or motion changed before primary request");return;
     }
     lastOperation="power.request(primary)";
     log("PRIMARY_REQUEST_BEGIN target="+target+" task="+sceneTask+" current="+current+" outgoing="+ack.physical+" session="+sceneSession);
     power.request(target);
     primaryRequestedThisSession=true;
     deadline=SystemClock.elapsedRealtime()+LiveB8Policy.TRANSITION_MS;
     log("READINESS_HANDOFF target="+target+" outgoing="+ack.physical+" frameCommit="+ack.commitAt+" rotation=verified-pair brightness=accepted angle="+latest.angle);
    } else {
     latest=input;at=SystemClock.elapsedRealtime();
     if (!LiveB8Policy.usable(latest.enabled,at,latest.heartbeat,latest.sample,latest.angle) || closing || !presentationReady(at))
      throw new IllegalStateException("Input/presentation expired before primary request");
     lastOperation=nativeReturn?"power.nativeRelease":"power.request(primary)";
     log("PRIMARY_REQUEST_BEGIN target="+target+" nativeRelease="+nativeReturn+" task="+sceneTask+" session="+sceneSession);
     if(nativeReturn)power.nativeRelease();else power.request(target);
    }
    phase=5;resetGeometry();log("PRIMARY_REQUEST target="+target+" nativeRelease="+nativeReturn);
 }
 private boolean endpointEligible(int base){
  Input latest=input;long now=SystemClock.elapsedRealtime();
  return !closing&&!active&&!cleaning&&latest.enabled&&Float.isFinite(latest.angle)
    &&latest.sample>0&&now>=latest.sample&&now-latest.sample<=750&&now-latest.heartbeat<=750
    &&(base==0?latest.angle<=0:latest.angle>=latest.open);
 }
 private void checkEndpoint(){
  LiveB8EndpointCheck check=endpointCheck;if(check==null)return;
  try{
   unlocked();
   String result=check.tick(()->endpointEligible(check.base));
   if(!result.isEmpty())log("ENDPOINT_CHECK "+result);
  }catch(Exception unavailable){check.finished=true;log("ENDPOINT_CHECK UNVERIFIED "+unavailable);}
  if(check.finished)endpointCheck=null;
 }
 private void cleanup(){
  // Keep app coverage through restoration/release calls and pending layer
  // removal. The app's existing lock, disable and lease guards still apply.
  cleaning=true;
  if(shield!=null){shield.close();lastResources=shield.resources();if(!shield.isClosed())return;log("SHIELD_REMOVED "+lastResources);shield=null;}
  capturePauseRequested=false;
  if(task!=null){task.restore();task=null;}
  closeBrightness();
  if(power!=null){if(!power.close()){status="Live B8 cleanup pending; legacy handoff remains suspended";return;}power=null;}
  if(scene!=null){scene.close();if(!scene.closed())return;scene=null;}
  sceneSession=0;sceneTask=-1;sceneHome=false;sceneWorkspace=false;sceneTasks=new int[0];pauseReturnAt=pauseFadeAt=0;sceneAck=new SceneAck(0,false,"",0,false,false,false,-1,0);
  boolean completed=active;
  active=false;phase=0;cleaning=false;log("CLEANUP_COMPLETE; owned power and layer handles released");
  int base=input.angle<=0?0:3;
  String physical=base==0?cover:inner;
  if(completed&&direct&&completionPower!=null&&completionTask!=null&&!physical.isEmpty()&&endpointEligible(base)){
   endpointCheck=new LiveB8EndpointCheck(completionPower,completionTask,base,physical,SystemClock.elapsedRealtime());
   checkEndpoint();
  }
  completionPower=null;completionTask=null;
 }
 public void close(){closing=true;input=new Input(false,Float.NaN,0,172,85);}
}
