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
 private volatile boolean enabled,fresh,closed;
 private volatile float angle,open;
 private volatile long heartbeat;
 volatile String status="Rotation hold idle";
 private Backend backend;
 private boolean active,restorationPending;
 private long retryAfter;
 private long lastApply;
 private String mapping="";
 private int heldRotation;
 private final int user;
 FoldRotationHold(int user){this.user=user;thread.start();handler=new Handler(thread.getLooper());handler.post(tick);}
 void update(boolean enabled,boolean fresh,float angle,float open){this.enabled=enabled;this.fresh=fresh;this.angle=angle;this.open=open;heartbeat=SystemClock.elapsedRealtime();}
 void close(){closed=true;handler.post(tick);}
 private final Runnable tick=new Runnable(){public void run(){
  handler.removeCallbacks(this);
  long identity=Binder.clearCallingIdentity();
  try {
   long now=SystemClock.elapsedRealtime();
   if(now<retryAfter){handler.postDelayed(this,Math.max(32,retryAfter-now));return;}
   if(backend==null)backend=new Backend(user);
   boolean wanted=!restorationPending&&policy.update(now,!closed&&enabled,fresh&&now-heartbeat<1000,angle,open);
   if(!active){backend.recover();restorationPending=false;if(wanted){
    Object info=backend.display();if(info==null)throw new IllegalStateException("Primary display unavailable");
    heldRotation=info.getClass().getField("rotation").getInt(info);
    backend.begin();active=true;backend.hold(heldRotation);mapping="";lastApply=0;
   }}
   if(active&&wanted){
    Object info=backend.display();
    if(info!=null){String current=String.valueOf(info.getClass().getField("uniqueId").get(info));
     // Posture changes can reload a different auto-rotate preference. Reassert only when needed.
     if(!mapping.equals(current)||now-lastApply>=100){
      if(!mapping.equals(current))backend.enforce(current,heldRotation);
      else if(!(boolean)backend.frozen.invoke(backend.wm,0)||info.getClass().getField("rotation").getInt(info)!=heldRotation)backend.freeze.invoke(backend.wm,0,heldRotation,"Duo temporary fold hold");
      backend.holdSecondary();
      mapping=current;lastApply=now;
     }
    }
    Object actual=backend.display();boolean verified=actual!=null&&actual.getClass().getField("rotation").getInt(actual)==heldRotation&&(boolean)backend.frozen.invoke(backend.wm,0);
    status=(verified?"Rotation hold verified":"Rotation hold requested; awaiting readback")+" · "+(heldRotation*90)+" degrees";
   } else if(active){backend.restore();active=false;restorationPending=false;status="Rotation preference restored";}
  } catch(Exception error){restorationPending=true;retryAfter=SystemClock.elapsedRealtime()+2000;status="Rotation hold: "+root(error);enabled=false;
   try{if(backend!=null)backend.restore();active=false;restorationPending=false;}catch(Exception restore){status="Rotation restoration pending: "+root(restore);}
  } finally {Binder.restoreCallingIdentity(identity);}
  if(!closed||active)handler.postDelayed(this,32);else {if(backend!=null)backend.close();thread.quitSafely();}
 }};
 private static String root(Throwable e){while(e instanceof java.lang.reflect.InvocationTargetException&&e.getCause()!=null)e=e.getCause();return e.getClass().getSimpleName()+": "+e.getMessage();}
 /** Called by the independent keep-awake service. A live owner prevents recovery via the file lock. */
 private static final java.util.concurrent.atomic.AtomicBoolean recovering=new java.util.concurrent.atomic.AtomicBoolean();
 static void recoverAbandoned(int user){if(!new File("/data/local/tmp/duofold-rotation-"+user+".json").exists()||!recovering.compareAndSet(false,true))return;Thread worker=new Thread(()->{try(Backend b=new Backend(user)){b.recover();}catch(Exception ignored){/* Journal retained for next retry. */}finally{recovering.set(false);}},"duo-rotation-recovery");worker.setDaemon(true);worker.start();}
 private static final class Backend implements AutoCloseable {
  final Object wm,dm;final Method freeze,thaw,frozen,userRotation,info,postureSetting,fixed;
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
   JSONObject j=new JSONObject();j.put("locked",frozen.invoke(wm,0));j.put("rotation",userRotation.invoke(wm,0));
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
   // Capture the secondary panel before freezing the primary can change shared settings.
   Object secondary=info.invoke(dm,1);
   if(secondary!=null){
    JSONObject saved=new JSONObject();
    saved.put("physical",String.valueOf(secondary.getClass().getField("uniqueId").get(secondary)));
    saved.put("locked",frozen.invoke(wm,1));saved.put("rotation",userRotation.invoke(wm,1));
    saved.put("holdRotation",secondary.getClass().getField("rotation").getInt(secondary));
    j.put("secondary",saved);
   }
   File temp=new File(journal.getPath()+".tmp");
   try(FileOutputStream out=new FileOutputStream(temp)){out.write(j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
   android.system.Os.chmod(temp.getPath(),0600);
   java.nio.file.Files.move(temp.toPath(),journal.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
   // No rotation write can occur before the original preferences are durably saved.
  }
  void hold(int rotation)throws Exception {
   JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(journal.toPath()),java.nio.charset.StandardCharsets.UTF_8));
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
   java.util.List<String> restored=new java.util.ArrayList<>();
   for(java.util.Iterator<String> it=modes.keys();it.hasNext();){String physical=it.next();if(physical.equals(keep))continue;
    for(int id:new int[]{0,1}){Object d=info.invoke(dm,id);if(d!=null&&physical.equals(String.valueOf(d.getClass().getField("uniqueId").get(d)))){
     fixed.invoke(wm,id,modes.getInt(physical));restored.add(physical);break;
    }}
   }
   for(String key:restored)modes.remove(key);save(j);return modes.length()==0||(keep!=null&&modes.length()==1&&modes.has(keep));
  }
  void enforce(String physical,int rotation)throws Exception{
   JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(journal.toPath()),java.nio.charset.StandardCharsets.UTF_8));
   JSONObject modes=j.optJSONObject("fixed");if(modes==null){modes=new JSONObject();j.put("fixed",modes);}
   // Retain both panels' policies until the transition finishes.
   if(!modes.has(physical)){modes.put(physical,fixedMode(0));save(j);}
   freeze.invoke(wm,0,rotation,"Duo fold orientation hold");
   fixed.invoke(wm,0,2); // FIXED_TO_USER_ROTATION_ENABLED also blocks app/sensor overrides.
  }
  void holdSecondary()throws Exception{
   Object d=info.invoke(dm,1);if(d==null)return;
   JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(journal.toPath()),java.nio.charset.StandardCharsets.UTF_8));
   JSONObject saved=j.optJSONObject("secondary");
   String physical=String.valueOf(d.getClass().getField("uniqueId").get(d));
   // Only touch the secondary panel captured at hold start. After primary remapping,
   // display 0 is already protected by enforce(); do not guess the old panel's policy.
   if(saved==null||!physical.equals(saved.getString("physical")))return;
   int rotation=saved.getInt("holdRotation");
   JSONObject modes=j.getJSONObject("fixed");
   if(!modes.has(physical)){modes.put(physical,fixedMode(1));save(j);}
   if(!(boolean)frozen.invoke(wm,1)||d.getClass().getField("rotation").getInt(d)!=rotation)
    freeze.invoke(wm,1,rotation,"Duo secondary fold hold");
   fixed.invoke(wm,1,2);
  }
  void restoreSecondary(JSONObject j)throws Exception{
   JSONObject saved=j.optJSONObject("secondary");if(saved==null)return;
   Object d=info.invoke(dm,1);
   if(d!=null&&saved.getString("physical").equals(String.valueOf(d.getClass().getField("uniqueId").get(d)))){
    if(saved.getBoolean("locked"))freeze.invoke(wm,1,saved.getInt("rotation"),"Duo restore secondary rotation");
    else thaw.invoke(wm,1,"Duo restore secondary auto-rotate");
   }
   // If that panel became primary, the global/per-posture restoration below owns it.
  }
  static Object nullable(String value){return value==null?JSONObject.NULL:value;}
  static String value(JSONObject j,String key)throws Exception{return j.isNull(key)?null:j.getString(key);}
  void restore()throws Exception{
   if(lock==null)return;
   if(!journal.exists()){unlock();return;}
   JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(journal.toPath()),java.nio.charset.StandardCharsets.UTF_8));
   if(!j.optBoolean("globalRestored",false)){
   restoreSecondary(j);
   if(j.getBoolean("locked"))freeze.invoke(wm,0,j.getInt("rotation"),"Duo restore rotation preference");
   else thaw.invoke(wm,0,"Duo restore auto-rotate");
   // Use Samsung's posture API, not writes to ACCELEROMETER_ROTATION / the secure
   // posture map: those bypass its asynchronous controller and can corrupt preferences.
   java.util.Map<Integer,Integer> saved=preferences(j);
   JSONObject routes=j.getJSONObject("routes");
   for(java.util.Map.Entry<Integer,Integer> pref:saved.entrySet())if(pref.getValue()!=0)
    postureSetting.invoke(wm,routes.getInt(pref.getKey().toString()),pref.getValue()==2);
   boolean verified=false;
   for(int i=0;i<10;i++){
    SystemClock.sleep(50);
    if(saved.isEmpty()?(boolean)frozen.invoke(wm,0)==j.getBoolean("locked"):saved.equals(RotationPreferences.parse(get(Settings.Secure.class,"device_state_rotation_lock")))){verified=true;break;}
   }
   if(!verified)throw new IOException("Per-posture rotation readback differs; original retained");
   put(Settings.System.class,"user_rotation",value(j,"userRotation"));
   j.put("globalRestored",true);save(j);
   }
   if(!restoreFixed(j,null))throw new IOException("Rotation policy recovery waiting for physical display");
   if(!journal.delete())throw new IOException("Could not clear rotation recovery journal");
   unlock();
  }
  void unlock()throws Exception{if(lock!=null){lock.release();lock=null;}}
  public void close(){try{unlock();lease.close();}catch(Exception ignored){}}
 }
}
