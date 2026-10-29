package org.duofold.live.experimental.b8;

import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;

/** Token-owned live session. No timed recipe or process-kill timer. */
final class LiveB8Power {
 private final IBinder[] tokens={new Binder(),new Binder()};
 private final boolean[] held={false,false};
 private final Object displays,states;
 private final Class<?> dc,sc;
 private final Method override,cancel;
 private volatile boolean owned;
 private volatile int generation;private int expected=-1;
 volatile boolean canceled;
 LiveB8Power()throws Exception{
  displays=PowerExperiment.service("display","android.hardware.display.IDisplayManager");
  dc=Class.forName("android.hardware.display.IDisplayManager");
  override=dc.getMethod("setDisplayStateOverrideWithDisplayId",IBinder.class,int.class,int.class,int.class);
  sc=Class.forName("android.hardware.devicestate.DeviceStateManager");states=sc.getConstructor().newInstance();cancel=sc.getMethod("cancelStateRequest");
 }
 Object info(int id)throws Exception{return dc.getMethod("getDisplayInfo",int.class).invoke(displays,id);}
 static int number(Object o,String name)throws Exception{return o.getClass().getField(name).getInt(o);}
 static String physical(Object o)throws Exception{return String.valueOf(o.getClass().getField("uniqueId").get(o));}
 int primary()throws Exception{Object i=info(0);int w=number(i,"logicalWidth"),h=number(i,"logicalHeight");return Math.min(w,h)/(float)Math.max(w,h)>.7f?1:2;}
 void on()throws Exception{for(int id=0;id<2;id++){held[id]=true;override.invoke(displays,tokens[id],2,id,10000);}}
 void park()throws Exception{
  // The selected source is D0. Let its normal power policy run, and stop the
  // unused panel with a short renewable token lease, not a persistent setting.
  if(held[0]){override.invoke(displays,tokens[0],0,0,-1);held[0]=false;}
  held[1]=true;override.invoke(displays,tokens[1],1,1,1000);
 }
 void request(int primary)throws Exception{
  String name=primary==1?"CONCURRENT_INNER_DEFAULT":"CONCURRENT_OUTER_DEFAULT";int id=-1;
  for(Object s:(List<?>)sc.getMethod("getSupportedDeviceStates").invoke(states))if(name.equals(s.getClass().getMethod("getName").invoke(s)))id=(int)s.getClass().getMethod("getIdentifier").invoke(s);
  if(id<0)throw new IllegalStateException("Unsupported "+name);
  Class<?> rc=Class.forName("android.hardware.devicestate.DeviceStateRequest"),cc=Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
  Object builder=rc.getMethod("newBuilder",int.class).invoke(null,id),request=builder.getClass().getMethod("build").invoke(builder);
  int gen=++generation;expected=id;owned=true;canceled=false;
  Object callback=Proxy.newProxyInstance(cc.getClassLoader(),new Class<?>[]{cc},(p,m,a)->{
   if(m.getName().equals("hashCode"))return System.identityHashCode(p);
   if(m.getName().equals("equals"))return p==a[0];
   if(m.getName().equals("toString"))return "DuoLiveB8#"+gen;
   if(gen==generation&&m.getName().equals("onRequestCanceled")){canceled=true;owned=false;}
   return null;
  });
  sc.getMethod("requestState",rc,Executor.class,cc).invoke(states,request,(Executor)Runnable::run,callback);
 }
 int state(boolean base)throws Exception{
  Object sm=PowerExperiment.service("device_state","android.hardware.devicestate.IDeviceStateManager");
  Object i=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(sm);
  Object s=i.getClass().getField(base?"baseState":"currentState").get(i);
  return (int)s.getClass().getMethod("getIdentifier").invoke(s);
 }
 boolean ready()throws Exception{
  Object a=info(0),b=info(1);
  return a!=null&&b!=null&&!physical(a).equals(physical(b))&&number(a,"state")==2&&number(b,"state")==2&&number(a,"committedState")==2&&number(b,"committedState")==2&&state(false)==expected;
 }
 void nativeRelease()throws Exception{generation++;if(owned){cancel.invoke(states);owned=false;}expected=-1;canceled=false;}
 boolean close(){
  boolean ok=true;try{nativeRelease();}catch(Exception e){ok=false;}
  for(int id=0;id<2;id++)if(held[id])try{override.invoke(displays,tokens[id],0,id,-1);held[id]=false;}catch(Exception e){ok=false;}
  return ok;
 }
}
