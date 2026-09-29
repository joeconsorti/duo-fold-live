package org.duofold.live;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
/** Isolated bounded Camera-equivalent request; no task transfer or rotation writes. */
public final class CameraContinuityService extends Binder {
 public static final String TOKEN="org.duofold.live.CameraContinuity";
 private final CameraContentMirror mirror=new CameraContentMirror();
 private GlassCapture capture;
 private TaskDisplayRouter router;
 private boolean probeStarted=false,commitTask=false;
 private int owner=-1; private Object manager,request; private IBinder client;
 private final ScheduledExecutorService guard=Executors.newSingleThreadScheduledExecutor();
 private long deadline,heartbeat;private String primary,status="Idle";
 private final IBinder.DeathRecipient death=()->stop("App disconnected");
 public CameraContinuityService(){attachInterface(null,TOKEN);guard.scheduleAtFixedRate(()->{
  synchronized(this){if(request!=null)try{
   long now=SystemClock.elapsedRealtime();
   if(now>=deadline||now-heartbeat>3000)stop("Deadline or heartbeat expired");
   else if(!primary.equals(panel()))stop("Primary mapping changed");
  }catch(Exception e){stop("Display check failed");}}
 },250,250,TimeUnit.MILLISECONDS);}
 private Object info()throws Exception{
  IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
  Object svc=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);
  return Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(svc);
 }
 private int id(Object state)throws Exception{return (int)state.getClass().getMethod("getIdentifier").invoke(state);}
 private String panel()throws Exception{
  Object dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
  Object d=dm.getClass().getMethod("getDisplayInfo",int.class).invoke(dm,0);
  return String.valueOf(d.getClass().getField("uniqueId").get(d));
 }
 private void start(boolean inner,IBinder token,String expected)throws Exception{
  if(request!=null)throw new IllegalStateException("Test already active");
  Object state=info();
  if(id(state.getClass().getField("currentState").get(state))!=id(state.getClass().getField("baseState").get(state)))
   throw new IllegalStateException("Another display override is active; stop Camera and continuity tests");
  primary=panel();if(!primary.equals(expected))throw new IllegalStateException("Primary changed during preparation");
  Class<?> type=Class.forName("android.hardware.devicestate.DeviceStateManager");
  manager=type.getConstructor().newInstance();String wanted=inner?"CONCURRENT_INNER_DEFAULT":"CONCURRENT_OUTER_DEFAULT";
  int selected=-1;
  for(Object s:(List<?>)type.getMethod("getSupportedDeviceStates").invoke(manager))
   if(wanted.equals(s.getClass().getMethod("getName").invoke(s)))selected=id(s);
  if(selected<0)throw new IllegalStateException("Concurrent state unavailable");
  Class<?> req=Class.forName("android.hardware.devicestate.DeviceStateRequest");
  Object builder=req.getMethod("newBuilder",int.class).invoke(null,selected);
  // Samsung wrapper: inner state/flags 4, outer state/default flags.
  if(inner)builder.getClass().getMethod("setFlags",int.class).invoke(builder,4);
  Object next=builder.getClass().getMethod("build").invoke(builder);
  Class<?> cb=Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
  Object callback=Proxy.newProxyInstance(cb.getClassLoader(),new Class<?>[]{cb},(p,m,a)->{
   if(m.getName().equals("hashCode"))return System.identityHashCode(p);
   if(m.getName().equals("equals"))return p==a[0];
   if(m.getName().equals("toString"))return "DuoCameraProbe";
   synchronized(this){
    if(request==next&&m.getName().equals("onRequestActivated"))status="ACTIVE: "+wanted;
    if(request==next&&m.getName().equals("onRequestCanceled")){request=null;mirror.close();if(capture!=null)capture.close();status="Canceled by Samsung";unlink();}
   }return null;
  });
  client=token;client.linkToDeath(death,0);
  request=next;heartbeat=SystemClock.elapsedRealtime();deadline=heartbeat+30000;
  status="Requested "+wanted+" after secondary window attachment";
  try{type.getMethod("requestState",req,Executor.class,cb).invoke(manager,next,(Executor)Runnable::run,callback);}
  catch(Exception e){stop("Request failed");throw e;}
 }
 private Bundle moveFocusedTaskToInner()throws Exception{
  if(request==null||!primary.equals(panel()))throw new IllegalStateException("No fixed cover-primary session");
  if(router==null)router=new TaskDisplayRouter();
  if(probeStarted)throw new IllegalStateException("Native task probe already started");
  String requested=router.beginProbe();probeStarted=true;
  long start=SystemClock.elapsedRealtime();boolean repaired=false;String repair="";
  while(SystemClock.elapsedRealtime()-start<1600){
   boolean placed=router.probePlaced(),verified=router.probeVerified();
   if(placed&&verified){
    Bundle out=new Bundle();out.putBoolean("nativeReady",true);
    out.putString("nativeMove",requested+"; verified on display 1; "+router.probeSnapshot());
    return out;
   }
   if(!repaired&&SystemClock.elapsedRealtime()-start>=350){
    try{repair=router.repairProbe();}catch(Exception e){repair="repair failed: "+e.getMessage();}
    repaired=true;
   }
   Thread.sleep(50);
  }
  Bundle out=new Bundle();boolean placed=router.probePlaced(),verified=router.probeVerified();
  out.putBoolean("nativeReady",placed&&verified);
  out.putString("nativeMove",requested+"; verified="+verified+"; placed="+placed+(repair.isEmpty()?"":"; "+repair)+"; "+router.probeSnapshot());
  return out;
 }
 private void finishNativeHandoff()throws Exception{
  if(!probeStarted||router==null||!router.probePlaced())throw new IllegalStateException("Native inner task is not placed");
  mirror.close();commitTask=true;
  if(request!=null)manager.getClass().getMethod("cancelStateRequest").invoke(manager);
  request=null;unlink();status="Concurrent cover hold released after native inner task placement";
 }
 private String nativeSecondary()throws Exception{
  Object atm=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
  Class<?> api=Class.forName("android.app.IActivityTaskManager");
  List<?> tasks=(List<?>)api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(atm,8,false,false,1);
  Object focused=api.getMethod("getFocusedRootTaskInfo").invoke(atm);
  StringBuilder out=new StringBuilder("Native underlay exposed; display 1 tasks=");
  if(tasks.isEmpty())out.append("none");
  else for(int i=0;i<tasks.size();i++){
   Object task=tasks.get(i);
   if(i>0)out.append(" | ");
   out.append(task.getClass().getField("taskId").getInt(task))
      .append(":").append(task.getClass().getField("topActivity").get(task));
  }
  out.append("; focused=");
  if(focused==null)out.append("none");
  else out.append("display ").append(focused.getClass().getField("displayId").getInt(focused))
          .append(" task ").append(focused.getClass().getField("taskId").getInt(focused))
          .append(" top ").append(focused.getClass().getField("topActivity").get(focused));
  return out.toString();
 }
 private void unlink(){if(client!=null){client.unlinkToDeath(death,0);client=null;}}
 private synchronized void stop(String reason){
  mirror.close();if(capture!=null){capture.close();capture=null;}
  if(router!=null&&probeStarted&&!commitTask)try{router.endProbe(true);}catch(Exception ignored){}
  probeStarted=false;
  if(request!=null)try{manager.getClass().getMethod("cancelStateRequest").invoke(manager);}
   catch(Exception e){System.exit(0);}
  request=null;unlink();status=reason;
 }
 @Override protected synchronized boolean onTransact(int code,Parcel p,Parcel r,int flags)throws RemoteException{
  if(code==INTERFACE_TRANSACTION){r.writeString(TOKEN);return true;}
  if(code==16777115){stop("Helper stopped");System.exit(0);return true;}
  p.enforceInterface(TOKEN);int uid=Binder.getCallingUid();if(owner<0)owner=uid;if(owner!=uid)throw new SecurityException("Wrong caller");
  long identity=Binder.clearCallingIdentity();Bundle out=new Bundle();
  try{
   if(code==1){start(p.readInt()!=0,p.readStrongBinder(),p.readString());capture=new GlassCapture(owner);out.putBinder("capture",capture);}
   else if(code==2)heartbeat=SystemClock.elapsedRealtime();
   else if(code==3)stop("Stopped");
   else if(code==4){
    android.view.SurfaceControl parent=p.readTypedObject(android.view.SurfaceControl.CREATOR);
    int width=p.readInt(),height=p.readInt();
    if(request==null||!primary.equals(panel())){if(parent!=null)parent.release();throw new IllegalStateException("No fixed-primary session");}
    Bundle attached=mirror.attach(1,parent,width,height,true);
    if(!attached.getBoolean("ok"))throw new IllegalStateException(attached.getString("status"));
    out.putString("content",attached.getString("status"));
   }else if(code==5)mirror.close();
   else if(code==6)out.putString("native",nativeSecondary());
   else if(code==7){
    Bundle moved=moveFocusedTaskToInner();
    out.putBoolean("nativeReady",moved.getBoolean("nativeReady"));
    out.putString("nativeMove",moved.getString("nativeMove"));
   }else if(code==8)finishNativeHandoff();
   else throw new IllegalArgumentException("Unknown operation");
   out.putBoolean("ok",true);
  }catch(Exception e){out.putString("error",String.valueOf(e.getCause()!=null?e.getCause():e));}
  finally{Binder.restoreCallingIdentity(identity);}
  out.putBoolean("active",request!=null);out.putString("status",status);
  r.writeNoException();r.writeBundle(out);return true;
 }
}
