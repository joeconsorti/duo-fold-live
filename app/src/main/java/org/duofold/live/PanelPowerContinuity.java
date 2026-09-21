package org.duofold.live;
import android.os.*;
import java.lang.reflect.*;
/** Bounded physical-inner power experiment; never changes brightness or logical display routing. */
final class PanelPowerContinuity {
 private final HandlerThread thread=new HandlerThread("duo-panel-power");
 private final Handler handler;
 private volatile boolean enabled,closed;
 private volatile long lease;
 private volatile String requestedId;
 private String preparedId;
 private Object dm,wm,pm;
 private Method infoMethod,keyguard,interactive,setPower,getToken;
 private IBinder token;
 private boolean initialized,unavailable,wrotePower;
 private long started,maxCallMs;
 private int writes,reassertions,offSamples;
 volatile String status="Power continuity idle";
 PanelPowerContinuity(){if(android.os.Process.myUid()!=2000)throw new SecurityException("Panel power helper requires Shizuku ADB shell");thread.start();handler=new Handler(thread.getLooper());}
 void configure(boolean value){boolean changed=enabled!=value;enabled=value;lease=SystemClock.elapsedRealtime();if(changed&&!value)handler.post(()->finish("disabled"));}
 void prime(String id){
  if(!enabled||closed||id==null||id.equals(requestedId))return;
  requestedId=id;handler.post(()->{try{prepare(id);}catch(Throwable e){unavailable=true;status="Power continuity unavailable: "+root(e);}});
 }
 void begin(){final long requested=SystemClock.elapsedRealtime();handler.post(()->{
  if(!enabled||closed||unavailable||token==null||started>0)return;
  try{
   Object info=findInner();
   if(info==null||state(info)!=2||!unlocked()){status="Power continuity skipped: inner not already ON or phone locked";return;}
   started=requested;writes=reassertions=offSamples=0;maxCallMs=0;wrotePower=false;
   if(!PanelPowerPolicy.allowed(SystemClock.elapsedRealtime(),started,lease,enabled,true)){finish("start expired");return;}
   write(2);status="Power continuity armed: physical inner; maximum 650 ms";
   handler.post(tick);
  }catch(Throwable e){finish("arm failed: "+root(e));}
 });}
 void stop(String reason){handler.post(()->finish(reason));}
 private static String root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getClass().getSimpleName()+": "+e.getMessage();}
 private Object service(String name,String stub)throws Exception{
  IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
  return Class.forName(stub).getMethod("asInterface",IBinder.class).invoke(null,b);
 }
 // This method runs only inside the UID-2000 app_process helper, never the APK app process.
 // Runtime API/permission failures are reported and preserve the normal handoff path.
 @android.annotation.SuppressLint({"BlockedPrivateApi", "SoonBlockedPrivateApi", "PrivateApi"})
 private void initialize()throws Exception{
  if(initialized)return;
  dm=service("display","android.hardware.display.IDisplayManager$Stub");infoMethod=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class);
  wm=service("window","android.view.IWindowManager$Stub");keyguard=Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked");
  pm=service("power","android.os.IPowerManager$Stub");interactive=Class.forName("android.os.IPowerManager").getMethod("isInteractive");
  Class<?> surface=Class.forName("android.view.SurfaceControl");setPower=surface.getMethod("setDisplayPowerMode",IBinder.class,int.class);
  try{getToken=surface.getMethod("getPhysicalDisplayToken",long.class);}
  catch(NoSuchMethodException moved){
   String classpath=System.getenv("SYSTEMSERVERCLASSPATH");
   if(classpath==null||classpath.isEmpty())throw new IllegalStateException("System display classpath unavailable");
   Class<?> factory=Class.forName("com.android.internal.os.ClassLoaderFactory");
   ClassLoader loader=(ClassLoader)factory.getDeclaredMethod("createClassLoader",String.class,String.class,String.class,ClassLoader.class,int.class,boolean.class,String.class)
    .invoke(null,classpath,null,null,ClassLoader.getSystemClassLoader(),0,true,null);
   Class<?> control=loader.loadClass("com.android.server.display.DisplayControl");
   Method load=Runtime.class.getDeclaredMethod("loadLibrary0",Class.class,String.class);load.setAccessible(true);load.invoke(Runtime.getRuntime(),control,"android_servers");
   getToken=control.getMethod("getPhysicalDisplayToken",long.class);
  }
  initialized=true;
 }
 private void prepare(String id)throws Exception{
  if(started>0||unavailable)return;
  initialize();
  if(!id.startsWith("local:"))throw new IllegalArgumentException("Not a physical display");
  long physical=Long.parseUnsignedLong(id.substring(6));
  preparedId=id;Object info=findInner();
  if(info==null)throw new IllegalStateException("Inner display identity not found");
  int type=info.getClass().getField("type").getInt(info);
  int w=info.getClass().getField("logicalWidth").getInt(info),h=info.getClass().getField("logicalHeight").getInt(info);
  if(type!=1||Math.min(w,h)/(float)Math.max(w,h)<=.7f)throw new IllegalStateException("Target is not the built-in inner panel");
  token=(IBinder)getToken.invoke(null,physical);
  if(token==null)throw new IllegalStateException("No inner physical display token");
  status="Power continuity ready: physical inner token resolved";
 }
 private Object findInner()throws Exception{
  for(int id=0;id<=1;id++){
   Object info=infoMethod.invoke(dm,id);
   if(info!=null&&preparedId.equals(info.getClass().getField("uniqueId").get(info)))return info;
  }return null;
 }
 private int state(Object info)throws Exception{return info.getClass().getField("state").getInt(info);}
 private boolean unlocked()throws Exception{return (boolean)interactive.invoke(pm)&&!(boolean)keyguard.invoke(wm);}
 private void write(int mode)throws Exception{
  long before=SystemClock.elapsedRealtime();setPower.invoke(null,token,mode);maxCallMs=Math.max(maxCallMs,SystemClock.elapsedRealtime()-before);writes++;wrotePower=true;
 }
 private final Runnable tick=new Runnable(){public void run(){
  try{
   long now=SystemClock.elapsedRealtime();
   if(!PanelPowerPolicy.allowed(now,started,lease,enabled&&!closed,unlocked())){finish("timeout, lock, or stale lease");return;}
   Object info=findInner();
   if(info!=null&&state(info)==1){
    offSamples++;
    if(reassertions<PanelPowerPolicy.MAX_REASSERTIONS){write(2);reassertions++;}
   }
   status="Power continuity active; ON requests="+writes+"; OFF samples="+offSamples+"; max call="+maxCallMs+" ms (requests are not proof of visibility)";
   handler.postDelayed(this,8);
  }catch(Throwable e){unavailable=true;finish("failed: "+root(e));}
 }};
 private void finish(String reason){
  handler.removeCallbacks(tick);if(started==0)return;
  long duration=SystemClock.elapsedRealtime()-started;started=0;
  String restore="no write needed";
  if(wrotePower){
   try{
    Object info=findInner();int mode=info==null?-1:PanelPowerPolicy.restoreMode(state(info));
    // Respect the current logical power state, including the lock screen and intentional sleep.
    if(!(boolean)interactive.invoke(pm))mode=0;
    if(mode>=0){setPower.invoke(null,token,mode);restore="system-state mode "+mode;}else restore="mapping unavailable; no guessed power write";
   }catch(Throwable e){restore="restore failed: "+root(e);}
  }
  wrotePower=false;
  status="Power continuity ended: "+reason+"; elapsed="+duration+" ms; ON requests="+writes+"; OFF samples="+offSamples+"; max call="+maxCallMs+" ms; "+restore+". Logical OFF can still be reported while physical ON was requested.";
 }
 void close(){closed=true;enabled=false;handler.post(()->{finish("reader stopped");thread.quitSafely();});}
}
