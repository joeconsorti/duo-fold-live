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
 private void unlink(){if(client!=null){client.unlinkToDeath(death,0);client=null;}}
 private synchronized void stop(String reason){
  mirror.close();if(capture!=null){capture.close();capture=null;}
  if(request!=null)try{manager.getClass().getMethod("cancelStateRequest").invoke(manager);}
   catch(Exception e){System.exit(0);}
  request=null;unlink();status=reason;
 }
 // Read-only task diagnostics: never resumes, focuses, or reparents a task.
 private String nativeSnapshot(){
  try{
   Object atm=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
   Class<?> api=Class.forName("android.app.IActivityTaskManager");
   StringBuilder text=new StringBuilder("primary=").append(panel());
   for(int display=0;display<=1;display++){
    List<?> roots=(List<?>)api.getMethod("getAllRootTaskInfosOnDisplay",int.class).invoke(atm,display);
    text.append("; display ").append(display).append(" roots=").append(roots.size());
    for(Object root:roots){
     Class<?> type=root.getClass();
     text.append(" [task=").append(type.getField("taskId").get(root))
      .append(" top=").append(type.getField("topActivity").get(root))
      .append(" config=").append(type.getField("configuration").get(root)).append("]");
    }
   }
   return text.toString();
  }catch(Exception e){return "Task diagnostics unavailable: "+e;}
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
   else if(code==6||code==7){
    if(request==null||!primary.equals(panel()))throw new IllegalStateException("No fixed-primary session");
    if(code==6)mirror.close();
    out.putString("diagnostics",nativeSnapshot());
   }
   else throw new IllegalArgumentException("Unknown operation");
   out.putBoolean("ok",true);
  }catch(Exception e){out.putString("error",String.valueOf(e.getCause()!=null?e.getCause():e));}
  finally{Binder.restoreCallingIdentity(identity);}
  out.putBoolean("active",request!=null);out.putString("status",status);
  r.writeNoException();r.writeBundle(out);return true;
 }
}
