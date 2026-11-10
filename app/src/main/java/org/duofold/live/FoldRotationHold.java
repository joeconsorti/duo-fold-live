package org.duofold.live;

import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.channels.FileLock;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Separate from rendering. A durable journal plus cross-process lease protects user preferences. */
final class FoldRotationHold {
 private final HandlerThread thread=new HandlerThread("duo-fold-rotation");
 private final Handler handler;
 private final FoldRotationPolicy policy=new FoldRotationPolicy();
 private volatile boolean enabled,fresh,closed,screenshotRelease,diagnostics;
 void diagnostics(boolean enabled){diagnostics=enabled;}
 void screenshotRelease(boolean enabled){screenshotRelease=enabled;}
 private volatile float angle,open;
 private volatile long heartbeat,requestedSession;
 private long activeSession;
 private volatile FoldRotationProof pair;
 boolean verifiedFor(long session,String primary,int first,String secondary,int second){
  FoldRotationProof proof=pair;
  return !closed&&enabled&&fresh&&Float.isFinite(angle)&&angle>1f&&angle<FoldThreshold.sanitize(open)
    &&requestedSession==session&&proof!=null
    &&proof.matches(session,SystemClock.elapsedRealtime(),primary,first,secondary,second);
 }
 volatile String status="Rotation hold idle";
 private volatile Backend backend;
 String report(){Backend b=backend;return status+(b==null?"":"\n"+b.releaseTrace+"\n"+b.probe.report());}
 private boolean active,restorationPending;
 private long retryAfter;
 private long lastApply;
 private String mapping="";
 private int heldRotation;
 private final int user;
 private final String apk;
 private RotationRecoveryProcess recovery;
 private final java.util.concurrent.CountDownLatch released=new java.util.concurrent.CountDownLatch(1);
 FoldRotationHold(int user,String apk){this.user=user;this.apk=apk;thread.start();handler=new Handler(thread.getLooper());handler.post(tick);}
 void update(boolean enabled,boolean fresh,float angle,float open,long session){
  boolean changed=this.enabled!=enabled||this.fresh!=fresh||requestedSession!=session;
  this.enabled=enabled;this.fresh=fresh;this.angle=angle;this.open=open;
  heartbeat=SystemClock.elapsedRealtime();requestedSession=session;
  if(changed)handler.post(tick);
 }
 private boolean wanted(long now,long session){
  return policy.update(now,!closed&&enabled&&session!=0&&session==requestedSession,
    fresh&&now-heartbeat<1000,angle,open);
 }
 void close(){closed=true;handler.post(tick);}
 void awaitRelease(){try{released.await(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
 private final Runnable tick=new Runnable(){public void run(){
  handler.removeCallbacks(this);
  long identity=Binder.clearCallingIdentity();
  try {
   long now=SystemClock.elapsedRealtime();
   if(now<retryAfter){handler.postDelayed(this,Math.max(32,retryAfter-now));return;}
   if(backend==null){backend=new Backend(user);recovery=RotationRecoveryProcess.start(apk,user);}
   backend.reconcileFixed=screenshotRelease;backend.detailed=diagnostics;
   if(recovery!=null&&!recovery.alive())throw new IOException("Rotation recovery helper exited; releasing hold");
   long session=requestedSession;
   boolean wanted=!restorationPending&&wanted(now,session)&&(!active||activeSession==session);
   if(!active){boolean hadRecovery=backend.journal.exists();backend.recover();if(hadRecovery&&screenshotRelease&&!backend.journal.exists())status="Rotation settings restored after recovery; orientation response not verified";restorationPending=false;if(wanted){
    if(recovery==null)recovery=RotationRecoveryProcess.start(apk,user);
    backend.begin();
    // Saving the preferences can span a short fold. Discard an unused journal before the first write.
    long afterSave=SystemClock.elapsedRealtime();
    wanted=wanted(afterSave,session)&&fresh&&afterSave-heartbeat<1000;
    if(wanted){
     active=true;activeSession=session;
     Object info=backend.display();if(info==null)throw new IOException("Primary disappeared before rotation hold");
     heldRotation=backend.heldRotation(String.valueOf(info.getClass().getField("uniqueId").get(info)),info.getClass().getField("rotation").getInt(info));
     backend.hold(heldRotation);mapping="";lastApply=0;
    }else{
     backend.abandonUnapplied();status="Rotation preparation ended before hold";
    }
   }}
   if(active&&wanted){
    Object info=backend.display();
    if(info!=null){String current=String.valueOf(info.getClass().getField("uniqueId").get(info));
     // Posture changes can reload a different auto-rotate preference. Reassert only when needed.
     if(!mapping.equals(current)||now-lastApply>=100){
      if(!mapping.equals(current)){pair=null;heldRotation=backend.heldRotation(current,heldRotation);backend.enforce(current,heldRotation);}
      else {
       if(!(boolean)backend.frozen.invoke(backend.wm,0)||info.getClass().getField("rotation").getInt(info)!=heldRotation)backend.freeze.invoke(backend.wm,0,heldRotation,"Duo temporary fold hold");
       // Samsung can reset fixed-to-user policy without changing panel identity.
       backend.fixed.invoke(backend.wm,0,2);
      }
      backend.holdSecondary();
      mapping=current;lastApply=now;
      if(session>0&&wanted(SystemClock.elapsedRealtime(),session))
       pair=backend.readFrozenPair(session);
     }
    }
    Object actual=backend.display();boolean verified=actual!=null&&actual.getClass().getField("rotation").getInt(actual)==heldRotation&&(boolean)backend.frozen.invoke(backend.wm,0);
    status=(session>0?(pair!=null?"Rotation hold verified on both panels":"Rotation hold waiting for both panels"):
      verified?"Rotation hold verified":"Rotation hold requested; awaiting readback")+" · "+(heldRotation*90)+" degrees";
   } else if(active){pair=null;backend.restore();active=false;restorationPending=false;status="Rotation settings restored on both saved panels; orientation response not verified";}
   if(!active)backend.observeTail();
  } catch(Exception error){pair=null;restorationPending=true;retryAfter=SystemClock.elapsedRealtime()+2000;status="Rotation hold: "+root(error);enabled=false;
   try{boolean hadRecovery=backend!=null&&backend.journal.exists();if(backend!=null)backend.restore();active=false;restorationPending=false;if(hadRecovery&&screenshotRelease&&!backend.journal.exists())status="Rotation settings restored after recovery; orientation response not verified";if(recovery!=null&&!recovery.alive()){recovery.close();recovery=null;}}catch(Exception restore){status="Rotation restoration pending: "+root(restore);}
  } finally {Binder.restoreCallingIdentity(identity);}
  if(!closed||active||restorationPending)handler.postDelayed(this,32);else {if(backend!=null)backend.close();if(recovery!=null)recovery.close();released.countDown();thread.quitSafely();}
 }};
 static void checkRecoveryBackend(int user)throws Exception{try(Backend ignored=new Backend(user)){} }
 static boolean recoverAfterOwnerExit(int user)throws Exception{try(Backend b=new Backend(user)){b.recover();return !b.journal.exists();}}
 private static String root(Throwable e){while(e instanceof java.lang.reflect.InvocationTargetException&&e.getCause()!=null)e=e.getCause();return e.getClass().getSimpleName()+": "+e.getMessage();}
 /** Called by the independent keep-awake service. A live owner prevents recovery via the file lock. */
 private static final java.util.concurrent.atomic.AtomicBoolean recovering=new java.util.concurrent.atomic.AtomicBoolean();
 static void recoverAbandoned(int user){if(!new File("/data/local/tmp/duofold-rotation-"+user+".json").exists()||!recovering.compareAndSet(false,true))return;Thread worker=new Thread(()->{try(Backend b=new Backend(user)){b.recover();}catch(Exception ignored){/* Journal retained for next retry. */}finally{recovering.set(false);}},"duo-rotation-recovery");worker.setDaemon(true);worker.start();}
 static String repairAutoRotate(int user)throws Exception{
  try(Backend b=new Backend(user)){
   if(!b.acquire())throw new IOException("Rotation hold is still releasing. Keep animation disabled and retry.");
   if(!b.journal.exists())b.begin();
   JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(b.journal.toPath()),java.nio.charset.StandardCharsets.UTF_8));
   j.put("locked",false);j.put("auto","1");
   JSONObject secondary=j.optJSONObject("secondary");if(secondary!=null)secondary.put("locked",false);
   java.util.Map<Integer,Integer> preferences=preferencesForRepair(j);
   StringBuilder states=new StringBuilder();
   for(java.util.Map.Entry<Integer,Integer> pref:preferences.entrySet()){
    if(states.length()>0)states.append(':');states.append(pref.getKey()).append(':').append(pref.getValue()==0?0:2);
   }
   if(states.length()>0)j.put("states",states.toString());
   JSONObject modes=j.getJSONObject("fixed");
   for(java.util.Iterator<String> it=modes.keys();it.hasNext();)modes.put(it.next(),0);
   for(int id:new int[]{0,1}){
    Object d=b.info.invoke(b.dm,id);if(d==null)continue;
    String physical=String.valueOf(d.getClass().getField("uniqueId").get(d));
    if(!physical.startsWith("local:"))continue;
    modes.put(physical,0);
    if(id==0)j.put("primaryPhysical",physical);
    else{JSONObject target=new JSONObject();target.put("physical",physical);target.put("locked",false);target.put("rotation",b.userRotation.invoke(b.wm,id));j.put("secondary",target);}
   }
   // Explicit user repair: restore stock fixed-rotation policy, enable auto-rotate.
   // Save the repair target before writes so interrupted repair is recoverable.
   b.save(j);b.restore();return "Auto-rotate repair verified. Animation remains off; test rotation before re-enabling.";
  }
 }
 private static java.util.Map<Integer,Integer> preferencesForRepair(JSONObject j)throws Exception{return Backend.preferences(j);}
 private static final class Backend implements AutoCloseable {
  final Object wm,dm;final Method freeze,thaw,frozen,userRotation,info,postureSetting,fixed;
  boolean reconcileFixed,detailed;
  final RotationDiagnosticProbe probe=new RotationDiagnosticProbe();
  private int probes;
  private JSONObject ownedJournal;
  private long tailUntil,nextTail;
  private String lastTail="";
  volatile String releaseTrace="";
  private final java.util.ArrayDeque<String> releaseEvents=new java.util.ArrayDeque<>();
  void observeRelease(String phase){
   StringBuilder line=new StringBuilder().append(SystemClock.elapsedRealtime()).append(" ROTATION_RELEASE ").append(phase);
   for(int id:new int[]{0,1})try{
    Object d=info.invoke(dm,id);if(d==null){line.append(" D").append(id).append("=missing");continue;}
    line.append(" D").append(id).append("=").append(d.getClass().getField("uniqueId").get(d))
     .append(" rotation=").append(d.getClass().getField("rotation").getInt(d))
     .append(" frozen=").append(frozen.invoke(wm,id))
     .append(" userRotation=").append(userRotation.invoke(wm,id));
   }catch(Exception unavailable){line.append(" D").append(id).append("=unavailable");}
   releaseEvents.addLast(line.toString());while(releaseEvents.size()>32)releaseEvents.removeFirst();
   releaseTrace=String.join("\n",releaseEvents);
  }
  void releaseStep(String name,RotationRelease.Step step)throws Exception{
   long began=SystemClock.elapsedRealtime();
   try{step.run();}finally{observeRelease(name+" elapsedMs="+(SystemClock.elapsedRealtime()-began));}
  }
  void observeTail(){
   long now=SystemClock.elapsedRealtime();if(now>=tailUntil||now<nextTail)return;nextTail=now+250;
   StringBuilder state=new StringBuilder();
   for(int id:new int[]{0,1})try{Object d=info.invoke(dm,id);if(d!=null)state.append(id).append(':').append(d.getClass().getField("uniqueId").get(d)).append(':').append(d.getClass().getField("rotation").getInt(d)).append(':').append(frozen.invoke(wm,id));}catch(Exception ignored){}
   if(!state.toString().equals(lastTail)){boolean changed=!lastTail.isEmpty();lastTail=state.toString();observeRelease("TAIL_CHANGE read-only; no re-lock");if(changed&&detailed&&probes<2){probes++;probe.request("TAIL_CHANGE");}}
  }
  JSONObject saved()throws Exception{
   if(lock==null)throw new IOException("Rotation journal requires owner lease");
   if(ownedJournal==null)ownedJournal=new JSONObject(new String(java.nio.file.Files.readAllBytes(journal.toPath()),java.nio.charset.StandardCharsets.UTF_8));
   return ownedJournal;
  }
  final int user;
  final File journal;final RandomAccessFile lease;FileLock lock;
  Backend(int user)throws Exception {
   this.user=user;
   ShellFrameworkBootstrap.initialize();
   IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
   Class<?> api=Class.forName("android.view.IWindowManager");
   wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
   fixed=api.getMethod("setFixedToUserRotation",int.class,int.class);
   freeze=api.getMethod("freezeDisplayRotation",int.class,int.class,String.class);
   thaw=api.getMethod("thawDisplayRotation",int.class,String.class);
   postureSetting=api.getMethod("setDeviceStateAutoRotateSetting",int.class,boolean.class);
   frozen=api.getMethod("isDisplayRotationFrozen",int.class);userRotation=api.getMethod("getDisplayUserRotation",int.class);
   dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);info=dm.getClass().getMethod("getDisplayInfo",int.class);
   journal=new File("/data/local/tmp/duofold-rotation-"+user+".json");
   lease=new RandomAccessFile(journal.getPath()+".lock","rw");
  }
  Object display()throws Exception{return info.invoke(dm,0);}
  String get(Class<?> table,String key)throws Exception{
   // A shell user-service is not an AMS-registered app process. Its synthetic
   // ContentResolver can be rejected by getContentProvider even with UID 2000.
   String text=setting(table,"get",key,null);
   return "null".equals(text)?null:text;
  }
  void put(Class<?> table,String key,String value)throws Exception{
   if(java.util.Objects.equals(value,get(table,key)))return; // Avoid duplicate async settings callbacks.
   setting(table,value==null?"delete":"put",key,value);
   if(!java.util.Objects.equals(value,get(table,key)))throw new IOException("Rotation setting readback mismatch: "+key);
  }
  String setting(Class<?> table,String verb,String key,String value)throws Exception{
   String namespace=table==Settings.System.class?"system":table==Settings.Secure.class?"secure":null;
   if(namespace==null)throw new IllegalArgumentException("Unsupported settings table");
   java.util.List<String> args=new java.util.ArrayList<>(java.util.Arrays.asList("settings","--user",Integer.toString(user),verb,namespace,key));
   if("put".equals(verb))args.add(value);
   java.lang.Process process=new ProcessBuilder(args).redirectErrorStream(true).start();
   try{
    if(!process.waitFor(2,TimeUnit.SECONDS))throw new IOException("Rotation settings command timed out: "+key);
    String text=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
    if(process.exitValue()!=0||("get".equals(verb)&&!text.matches("null|[0-9:]*")))throw new IOException("Rotation settings command failed ("+key+"): "+text);
    return text;
   }finally{process.destroyForcibly();}
  }
  boolean acquire()throws Exception{if(lock!=null)return true;try{lock=lease.getChannel().tryLock();}catch(java.nio.channels.OverlappingFileLockException busy){return false;}return lock!=null;}
  void recover()throws Exception{if(!journal.exists()||!acquire())return;try{if(journal.exists())restore();}finally{unlock();}}
  void begin()throws Exception{
   if(!acquire())throw new IOException("Another orientation hold is active");
   if(journal.exists())throw new IOException("Prior rotation restoration pending");
   tailUntil=0;ownedJournal=null;probe.cancel();probes=0;
   JSONObject j=new JSONObject();j.put("reconcileFixedOnRelease",reconcileFixed);
   j.put("auto",nullable(get(Settings.System.class,"accelerometer_rotation")));
   j.put("userRotation",nullable(get(Settings.System.class,"user_rotation")));
   String states=get(Settings.Secure.class,"device_state_rotation_lock");
   java.util.Map<Integer,Integer> preferences=states==null||states.isEmpty()?java.util.Collections.emptyMap():RotationPreferences.parse(states);
   JSONObject routes=new JSONObject();
   Class<?> managerType=Class.forName("android.hardware.devicestate.DeviceStateManager");
   Object manager=managerType.getConstructor().newInstance();
   for(Object state:(java.util.List<?>)managerType.getMethod("getSupportedDeviceStates").invoke(manager)){
    Method property=state.getClass().getMethod("hasProperty",int.class);
    // Samsung Android 17 PostureDeviceStateConverter: rear=16, outer-primary=11,
    // inner-primary=12, half-open=2. Match the system's own posture routing.
    int posture=(boolean)property.invoke(state,16)?3:(boolean)property.invoke(state,11)?0:(boolean)property.invoke(state,12)?((boolean)property.invoke(state,2)?1:2):-1;
    if(posture>=0)routes.put(Integer.toString(posture),state.getClass().getMethod("getIdentifier").invoke(state));
   }
   for(java.util.Map.Entry<Integer,Integer> pref:preferences.entrySet())if(pref.getValue()!=0&&!routes.has(pref.getKey().toString()))throw new IllegalStateException("No restoration route for posture "+pref.getKey());
   j.put("states",nullable(states));j.put("routes",routes);j.put("fixed",new JSONObject());
   Object primary=display();
   if(primary==null)throw new IOException("Primary display unavailable before hold");
   j.put("locked",frozen.invoke(wm,0));j.put("rotation",userRotation.invoke(wm,0));
   j.put("primaryPhysical",String.valueOf(primary.getClass().getField("uniqueId").get(primary)));
   j.put("holdRotation",primary.getClass().getField("rotation").getInt(primary));
   j.getJSONObject("fixed").put(j.getString("primaryPhysical"),fixedMode(0));
   // Capture the secondary panel before freezing the primary can change shared settings.
   Object secondary=info.invoke(dm,1);
   if(secondary!=null){
    JSONObject saved=new JSONObject();
    saved.put("physical",String.valueOf(secondary.getClass().getField("uniqueId").get(secondary)));
    saved.put("locked",frozen.invoke(wm,1));saved.put("rotation",userRotation.invoke(wm,1));
    saved.put("holdRotation",secondary.getClass().getField("rotation").getInt(secondary));
    j.put("secondary",saved);
    j.getJSONObject("fixed").put(saved.getString("physical"),fixedMode(1));
   }
   // Fixed-mode shell queries are slow enough for D0/D1 to exchange physical panels.
   // Reject a mixed journal before any setting write, rather than restore another panel's policy.
   if(findPanel(j.getString("primaryPhysical"))!=0||j.has("secondary")&&findPanel(j.getJSONObject("secondary").getString("physical"))!=1)
    throw new IOException("Panels remapped while saving rotation preferences; no hold applied");
   save(j);
   observeRelease("SAVED auto="+value(j,"auto")+" user="+value(j,"userRotation")+" postures="+value(j,"states")+" fixed="+j.getJSONObject("fixed"));
   // No rotation write can occur before the original preferences are durably saved.
  }
  void abandonUnapplied()throws Exception{
   if(!journal.delete())throw new IOException("Could not clear unused rotation journal");
   unlock();
  }
  FoldRotationProof readFrozenPair(long session)throws Exception{
   Object a=display(),b=info.invoke(dm,1);
   if(a==null||b==null)return null;
   String first=String.valueOf(a.getClass().getField("uniqueId").get(a));
   String second=String.valueOf(b.getClass().getField("uniqueId").get(b));
   int r0=a.getClass().getField("rotation").getInt(a),r1=b.getClass().getField("rotation").getInt(b);
   if(first.equals(second)||heldRotation(first,-1)!=r0||heldRotation(second,-1)!=r1||
      !(boolean)frozen.invoke(wm,0)||!(boolean)frozen.invoke(wm,1)||
      (int)userRotation.invoke(wm,0)!=r0||(int)userRotation.invoke(wm,1)!=r1||
      findPanel(first)!=0||findPanel(second)!=1)return null;
   return new FoldRotationProof(session,SystemClock.elapsedRealtime(),first,r0,second,r1);
  }
  void hold(int rotation)throws Exception {
   JSONObject j=saved();
   freeze.invoke(wm,0,rotation,"Duo temporary fold hold");
   JSONObject routes=j.getJSONObject("routes");
   for(java.util.Map.Entry<Integer,Integer> pref:preferences(j).entrySet())if(pref.getValue()!=0)
    postureSetting.invoke(wm,routes.getInt(pref.getKey().toString()),false);
  }
  static java.util.Map<Integer,Integer> preferences(JSONObject j)throws Exception {String value=value(j,"states");return value==null||value.isEmpty()?java.util.Collections.emptyMap():RotationPreferences.parse(value);}
  void save(JSONObject j)throws Exception{
   File temp=new File(journal.getPath()+".tmp");
   try(FileOutputStream out=new FileOutputStream(temp)){out.write(j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
   android.system.Os.chmod(temp.getPath(),0600);
   java.nio.file.Files.move(temp.toPath(),journal.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
   ownedJournal=j;
  }
  int fixedMode(int id)throws Exception {
   java.lang.Process process=new ProcessBuilder("wm","fixed-to-user-rotation","-d",Integer.toString(id)).redirectErrorStream(true).start();
   try{if(!process.waitFor(2,TimeUnit.SECONDS))throw new IOException("Rotation policy query timed out");
    String mode=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
    if(process.exitValue()!=0)throw new IOException("Rotation policy query failed: "+mode);
    return FixedRotationMode.parse(mode);
   }finally{process.destroyForcibly();}
  }
  boolean restoreFixed(JSONObject j,String keep)throws Exception{
   JSONObject modes=j.optJSONObject("fixed");if(modes==null)return true;
   java.util.List<RotationRelease.Step> steps=new java.util.ArrayList<>();
   for(java.util.Iterator<String> it=modes.keys();it.hasNext();){
    String physical=it.next();if(physical.equals(keep))continue;
    steps.add(()->{
     int id=findPanel(physical);
     if(id<0)throw new IOException("Rotation policy recovery waiting for physical display "+physical);
     int expected=modes.getInt(physical);
     fixed.invoke(wm,id,expected);
     if(findPanel(physical)!=id||fixedMode(id)!=expected)throw new IOException("Fixed rotation readback mismatch on "+physical);
    });
   }
   RotationRelease.all(steps);return true;
  }
  int findPanel(String physical)throws Exception{
   for(int id:new int[]{0,1}){Object d=info.invoke(dm,id);if(d!=null&&physical.equals(String.valueOf(d.getClass().getField("uniqueId").get(d))))return id;}
   return -1;
  }
  JSONObject panelSaved(JSONObject j,Object d)throws Exception{
   String physical=String.valueOf(d.getClass().getField("uniqueId").get(d));
   JSONObject secondary=j.optJSONObject("secondary");
   if(secondary!=null&&physical.equals(secondary.getString("physical")))return secondary;
   // Legacy journals did not name the original primary. On these two built-in
   // displays it is the panel other than the saved secondary; never infer external displays.
   if(j.has("primaryPhysical")?physical.equals(j.getString("primaryPhysical")):
      physical.startsWith("local:")&&secondary!=null&&!physical.equals(secondary.getString("physical")))return j;
   return null;
  }
  boolean expectedPrimaryLock(JSONObject j,Object d)throws Exception{
   int w=d.getClass().getField("logicalWidth").getInt(d),h=d.getClass().getField("logicalHeight").getInt(d);
   int posture=Math.min(w,h)/(float)Math.max(w,h)>.7f?2:0;
   return RotationRelease.locked(preferences(j).get(posture),j.getBoolean("locked"));
  }
  void restorePanelLocks(JSONObject j)throws Exception{
   java.util.List<RotationRelease.Step> steps=new java.util.ArrayList<>();
   // Secondary first; primary last, then restore the asynchronous posture preferences.
   for(int id:new int[]{1,0})steps.add(()->{
    Object d=info.invoke(dm,id);if(d==null){if(id==0)throw new IOException("Primary missing during rotation release");return;}
    JSONObject saved=panelSaved(j,d);
    if(saved==null){if(id==0)saved=j;else return;}
    String physical=String.valueOf(d.getClass().getField("uniqueId").get(d));
    boolean locked=id==0?expectedPrimaryLock(j,d):saved.getBoolean("locked");
    if(locked)freeze.invoke(wm,id,saved.getInt("rotation"),"Duo restore saved rotation");
    else thaw.invoke(wm,id,"Duo release fold rotation");
    if(findPanel(physical)!=id)throw new IOException("Panel remapped during rotation release; retrying");
   });
   RotationRelease.all(steps);
  }
  void restoreUserRotation(JSONObject j)throws Exception{
   Object d=display();if(d==null)throw new IOException("Primary missing while restoring user rotation");
   String physical=String.valueOf(d.getClass().getField("uniqueId").get(d));
   JSONObject panel=panelSaved(j,d);
   String target=RotationRelease.userRotationTarget(expectedPrimaryLock(j,d),panel==null?null:panel.getInt("rotation"),value(j,"userRotation"));
   put(Settings.System.class,"user_rotation",target);
   if(findPanel(physical)!=0)throw new IOException("Primary remapped while restoring user rotation");
  }
  void verifyRelease(JSONObject j)throws Exception{
   for(int id:new int[]{0,1}){
    Object d=info.invoke(dm,id);if(d==null){if(id==0)throw new IOException("Primary missing during release verification");continue;}
    JSONObject saved=panelSaved(j,d);
    if(saved==null){if(id==0)saved=j;else continue;}
    String physical=String.valueOf(d.getClass().getField("uniqueId").get(d));
    boolean expected=id==0?expectedPrimaryLock(j,d):saved.getBoolean("locked");
    if((boolean)frozen.invoke(wm,id)!=expected)throw new IOException("Rotation lock readback mismatch on display "+id);
    if(expected&&(int)userRotation.invoke(wm,id)!=saved.getInt("rotation"))throw new IOException("Saved locked rotation readback mismatch on display "+id);
    JSONObject modes=j.optJSONObject("fixed");
    if(modes!=null&&modes.has(physical)){int actual=fixedMode(id),expectedMode=modes.getInt(physical);if(actual!=expectedMode)throw new IOException("Fixed override remains on display "+id+" physical="+physical+" expected="+expectedMode+" actual="+actual);}
    if(findPanel(physical)!=id)throw new IOException("Panel remapped during release verification");
   }
   java.util.Map<Integer,Integer> saved=preferences(j);
   if(!saved.isEmpty()&&!saved.equals(RotationPreferences.parse(get(Settings.Secure.class,"device_state_rotation_lock"))))throw new IOException("Posture preferences still restoring");
  }
  void enforce(String physical,int rotation)throws Exception{
   JSONObject j=saved();
   JSONObject modes=j.optJSONObject("fixed");if(modes==null){modes=new JSONObject();j.put("fixed",modes);}
   // Retain both panels' policies until the transition finishes.
   if(!modes.has(physical)){modes.put(physical,fixedMode(0));save(j);}
   freeze.invoke(wm,0,rotation,"Duo fold orientation hold");
   fixed.invoke(wm,0,2); // FIXED_TO_USER_ROTATION_ENABLED also blocks app/sensor overrides.
  }
  int heldRotation(String physical,int fallback)throws Exception{
   JSONObject j=saved();
   JSONObject secondary=j.optJSONObject("secondary");
   return FoldRotationPanelPolicy.rotation(physical,j.optString("primaryPhysical"),j.optInt("holdRotation",fallback),
     secondary==null?"":secondary.optString("physical"),secondary==null?fallback:secondary.optInt("holdRotation",fallback),fallback);
  }
  void holdSecondary()throws Exception{
   Object d=info.invoke(dm,1);if(d==null)return;
   JSONObject j=saved();
   JSONObject saved=j.optJSONObject("secondary");
   String physical=String.valueOf(d.getClass().getField("uniqueId").get(d));
   // Either saved physical panel can become logical display 1 after a remap.
   int rotation=FoldRotationPanelPolicy.rotation(physical,j.optString("primaryPhysical"),j.optInt("holdRotation",-1),
     saved==null?"":saved.optString("physical"),saved==null?-1:saved.optInt("holdRotation",-1),-1);
   if(rotation<0)return; // Never touch an unknown display without saved restoration data.
   JSONObject modes=j.getJSONObject("fixed");
   if(!modes.has(physical)){modes.put(physical,fixedMode(1));save(j);}
   if(!(boolean)frozen.invoke(wm,1)||d.getClass().getField("rotation").getInt(d)!=rotation)
    freeze.invoke(wm,1,rotation,"Duo secondary fold hold");
   fixed.invoke(wm,1,2);
  }
  static Object nullable(String value){return value==null?JSONObject.NULL:value;}
  static String value(JSONObject j,String key)throws Exception{return j.isNull(key)?null:j.getString(key);}
  void restore()throws Exception{
   if(lock==null)return;
   if(!journal.exists()){unlock();return;}
   JSONObject j=saved();
   if(reconcileFixed&&!j.optBoolean("reconcileFixedOnRelease",false)){j.put("reconcileFixedOnRelease",true);save(j);}
   // Release the fixed override BEFORE thaw: fixed-to-user mode disables sensor
   // selection even when frozen=false. Keep normal temporary locks until then.
   // Do not force portrait or interpret an unlocked bit as orientation correctness.
   long releaseBegan=SystemClock.elapsedRealtime();
   observeRelease("BEGIN savedAuto="+value(j,"auto")+" savedUser="+value(j,"userRotation")+" postures="+value(j,"states")+" fixed="+j.optJSONObject("fixed"));
   RotationRelease.restore(
    ()->releaseStep("PANEL_LOCKS",()->restorePanelLocks(j)),
    ()->releaseStep("POSTURES",()->{
     JSONObject routes=j.getJSONObject("routes");
     java.util.List<RotationRelease.Step> postures=new java.util.ArrayList<>();
     for(java.util.Map.Entry<Integer,Integer> pref:preferences(j).entrySet())if(pref.getValue()!=0)
      postures.add(()->postureSetting.invoke(wm,routes.getInt(pref.getKey().toString()),pref.getValue()==2));
     RotationRelease.all(postures);
    }),
    ()->releaseStep("USER_ROTATION",()->restoreUserRotation(j)),
    ()->releaseStep("FIXED_POLICY",()->restoreFixed(j,null)));
   boolean reconcile=reconcileFixed||j.optBoolean("reconcileFixedOnRelease",false);

   // Require consecutive readbacks after Samsung's asynchronous setting callbacks.
   int consecutive=0;Exception last=null;
   for(int i=0;i<10&&consecutive<2;i++){
    if(i>0)SystemClock.sleep(100);
    try{verifyRelease(j);consecutive++;}catch(Exception e){consecutive=0;last=e;if(reconcile){try{restoreFixed(j,null);}catch(Exception retry){last=retry;}}}
   }
   if(consecutive<2)throw new IOException("Rotation release not verified; original settings retained: "+(last==null?"unknown":root(last)),last);
   observeRelease("SETTINGS_VERIFIED elapsedMs="+(SystemClock.elapsedRealtime()-releaseBegan)+"; sensor response unmeasured");
   tailUntil=SystemClock.elapsedRealtime()+10000;nextTail=0;lastTail="";
   if(detailed){probes++;probe.request("AFTER_SETTINGS_RESTORE");}
   if(!journal.delete())throw new IOException("Could not clear rotation recovery journal");
   unlock();
  }
  void unlock()throws Exception{if(lock!=null){lock.release();lock=null;}ownedJournal=null;}
  public void close(){probe.close();try{unlock();lease.close();}catch(Exception ignored){}}
 }
}
