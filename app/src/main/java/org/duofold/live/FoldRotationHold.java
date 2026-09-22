package org.duofold.live;

import android.content.ContentResolver;
import android.content.Context;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.channels.FileLock;
import java.util.concurrent.FutureTask;
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
      if(!mapping.equals(current)||!(boolean)backend.frozen.invoke(backend.wm,0))backend.freeze.invoke(backend.wm,0,heldRotation,"Duo temporary fold hold");
      mapping=current;lastApply=now;
     }
    }
    status="Rotation held during fold/unfold ("+(heldRotation*90)+" degrees)";
   } else if(active){backend.restore();active=false;restorationPending=false;status="Rotation preference restored";}
  } catch(Exception error){restorationPending=true;retryAfter=SystemClock.elapsedRealtime()+2000;status="Rotation hold: "+root(error);enabled=false;
   try{if(backend!=null)backend.restore();active=false;restorationPending=false;}catch(Exception restore){status="Rotation restoration pending: "+root(restore);}
  } finally {Binder.restoreCallingIdentity(identity);}
  if(!closed||active)handler.postDelayed(this,32);else {if(backend!=null)backend.close();thread.quitSafely();}
 }};
 private static String root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getClass().getSimpleName()+": "+e.getMessage();}
 /** Called by the independent keep-awake service. A live owner prevents recovery via the file lock. */
 private static final java.util.concurrent.atomic.AtomicBoolean recovering=new java.util.concurrent.atomic.AtomicBoolean();
 static void recoverAbandoned(int user){if(!new File("/data/local/tmp/duofold-rotation-"+user+".json").exists()||!recovering.compareAndSet(false,true))return;Thread worker=new Thread(()->{try(Backend b=new Backend(user)){b.recover();}catch(Exception ignored){/* Journal retained for next retry. */}finally{recovering.set(false);}},"duo-rotation-recovery");worker.setDaemon(true);worker.start();}
 private static final class Backend implements AutoCloseable {
  final Object wm,dm;final Method freeze,thaw,frozen,userRotation,info,postureSetting;
  final ContentResolver resolver;final int user;
  final File journal;final RandomAccessFile lease;FileLock lock;
  Backend(int user)throws Exception {
   this.user=user;
   ShellFrameworkBootstrap.initialize();
   FutureTask<Context> context=new FutureTask<>(()->{
    Class<?> at=Class.forName("android.app.ActivityThread");Object t=at.getMethod("currentActivityThread").invoke(null);
    if(t==null)t=at.getMethod("systemMain").invoke(null);
    return ((Context)at.getMethod("getSystemContext").invoke(t)).createPackageContext("com.android.shell",0);
   });
   if(Looper.myLooper()==Looper.getMainLooper())context.run();else new Handler(Looper.getMainLooper()).post(context);
   resolver=context.get(5,TimeUnit.SECONDS).getContentResolver();
   IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
   Class<?> api=Class.forName("android.view.IWindowManager");
   wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
   freeze=api.getMethod("freezeDisplayRotation",int.class,int.class,String.class);
   thaw=api.getMethod("thawDisplayRotation",int.class,String.class);
   postureSetting=api.getMethod("setDeviceStateAutoRotateSetting",int.class,boolean.class);
   frozen=api.getMethod("isDisplayRotationFrozen",int.class);userRotation=api.getMethod("getDisplayUserRotation",int.class);
   dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);info=dm.getClass().getMethod("getDisplayInfo",int.class);
   journal=new File("/data/local/tmp/duofold-rotation-"+user+".json");
   lease=new RandomAccessFile(journal.getPath()+".lock","rw");
  }
  Object display()throws Exception{return info.invoke(dm,0);}
  String get(Class<?> table,String key)throws Exception{return (String)table.getMethod("getStringForUser",ContentResolver.class,String.class,int.class).invoke(null,resolver,key,user);}
  void put(Class<?> table,String key,String value)throws Exception{
   if(!(boolean)table.getMethod("putStringForUser",ContentResolver.class,String.class,String.class,int.class).invoke(null,resolver,key,value,user))throw new IOException("Could not restore "+key);
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
   java.util.Map<Integer,Integer> preferences=RotationPreferences.parse(states);
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
   j.put("states",states);j.put("routes",routes);
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
   for(java.util.Map.Entry<Integer,Integer> pref:RotationPreferences.parse(j.getString("states")).entrySet())if(pref.getValue()!=0)
    postureSetting.invoke(wm,routes.getInt(pref.getKey().toString()),false);
  }
  static Object nullable(String value){return value==null?JSONObject.NULL:value;}
  static String value(JSONObject j,String key)throws Exception{return j.isNull(key)?null:j.getString(key);}
  void restore()throws Exception{
   if(lock==null)return;
   if(!journal.exists()){unlock();return;}
   JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(journal.toPath()),java.nio.charset.StandardCharsets.UTF_8));
   if(j.getBoolean("locked"))freeze.invoke(wm,0,j.getInt("rotation"),"Duo restore rotation preference");
   else thaw.invoke(wm,0,"Duo restore auto-rotate");
   // Use Samsung's posture API, not writes to ACCELEROMETER_ROTATION / the secure
   // posture map: those bypass its asynchronous controller and can corrupt preferences.
   java.util.Map<Integer,Integer> saved=RotationPreferences.parse(j.getString("states"));
   JSONObject routes=j.getJSONObject("routes");
   for(java.util.Map.Entry<Integer,Integer> pref:saved.entrySet())if(pref.getValue()!=0)
    postureSetting.invoke(wm,routes.getInt(pref.getKey().toString()),pref.getValue()==2);
   boolean verified=false;
   for(int i=0;i<10;i++){
    SystemClock.sleep(50);
    if(saved.equals(RotationPreferences.parse(get(Settings.Secure.class,"device_state_rotation_lock")))){verified=true;break;}
   }
   if(!verified)throw new IOException("Per-posture rotation readback differs; original retained");
   put(Settings.System.class,"user_rotation",value(j,"userRotation"));
   if(!journal.delete())throw new IOException("Could not clear rotation recovery journal");
   unlock();
  }
  void unlock()throws Exception{if(lock!=null){lock.release();lock=null;}}
  public void close(){try{unlock();lease.close();}catch(Exception ignored){}}
 }
}
