package com.mccal.folio;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;
/** A process-owned request: Android releases it if this Shizuku process dies. */
final class CoverHandoff {
 private Object manager,owned; private Method cancel,request; private Class<?> requestType,callbackType;
 private int coverId=-1; private final HandoffPolicy policy=new HandoffPolicy();
 String status="Normal display control";
 private void init()throws Exception{
  if(manager!=null)return;
  if(!"SM-F971U".equals(Build.MODEL))throw new IllegalStateException("Untested device model");
  Class<?> type=Class.forName("android.hardware.devicestate.DeviceStateManager");
  Object candidate=type.getConstructor().newInstance();
  for(Object state:(List<?>)type.getMethod("getSupportedDeviceStates").invoke(candidate)){
   if("CONCURRENT_OUTER_DEFAULT".equals(state.getClass().getMethod("getName").invoke(state)))coverId=(int)state.getClass().getMethod("getIdentifier").invoke(state);
  }
  if(coverId<0)throw new IllegalStateException("Cover state missing");
  requestType=Class.forName("android.hardware.devicestate.DeviceStateRequest");
  callbackType=Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
  request=type.getMethod("requestState",requestType,Executor.class,callbackType);cancel=type.getMethod("cancelStateRequest");manager=candidate;
 }
 synchronized void update(float angle,boolean fresh,boolean interactive){
  if(!fresh||!interactive){release();return;}
  int action=policy.update(angle,fresh,interactive);
  if(action<0){release();return;}if(action!=1)return;
  long identity=Binder.clearCallingIdentity();
  try{
   init();
   IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
   Object service=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
   Object info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(service);
   Object current=info.getClass().getField("currentState").get(info),base=info.getClass().getField("baseState").get(info);
   if(!current.getClass().getMethod("getIdentifier").invoke(current).equals(base.getClass().getMethod("getIdentifier").invoke(base)))throw new IllegalStateException("Another display override is active");
   Object builder=requestType.getMethod("newBuilder",int.class).invoke(null,coverId);
   Object next=builder.getClass().getMethod("build").invoke(builder);
   Object callback=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(proxy,m,args)->{
    if(m.getName().equals("hashCode"))return System.identityHashCode(proxy);
    if(m.getName().equals("equals"))return proxy==args[0];
    if(m.getName().equals("toString"))return "FolioCoverHandoff";
    if(m.getName().equals("onRequestCanceled")){synchronized(this){if(owned==next){owned=null;status="Cover request canceled by system";}}}return null;
   });
   owned=next;request.invoke(manager,next,(Executor)Runnable::run,callback);status="Cover held below handoff; restore at 98° or closed";
  }catch(Exception e){owned=null;Throwable root=e;while(root.getCause()!=null)root=root.getCause();status="Handoff unavailable: "+root.getClass().getSimpleName()+": "+root.getMessage();}
  finally{Binder.restoreCallingIdentity(identity);}
 }
 synchronized void release(){
  policy.reset();if(owned==null)return;long identity=Binder.clearCallingIdentity();
  try{cancel.invoke(manager);owned=null;status="Normal display control restored";}catch(Exception e){status="Display release pending";}finally{Binder.restoreCallingIdentity(identity);}
 }
}
