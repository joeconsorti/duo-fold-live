package org.duofold.live;
import android.os.*;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
/** Read-only shell-side base state shared by motion admission and diagnostics. */
final class ClosureDeviceState {
 private Object service;private Method read;private long next,lastSample,gap;private String previous="",state="unavailable";
 private int base=-1;
 private final ArrayDeque<String> events=new ArrayDeque<>();
 int refresh(long now){
  if(now<next)return base;
  next=now+100;gap=lastSample==0?0:now-lastSample;
  long identity=Binder.clearCallingIdentity();
  try{
   if(service==null){IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");service=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);read=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo");}
   Object info=read.invoke(service),physical=info.getClass().getField("baseState").get(info);
   base=identifier(physical);lastSample=SystemClock.elapsedRealtime();
   state="base="+label(physical)+" current="+label(info.getClass().getField("currentState").get(info));
  }catch(Exception e){base=-1;lastSample=0;service=null;state="DEVICE_STATE_UNAVAILABLE "+e.getClass().getSimpleName();next=now+1000;}
  finally{Binder.restoreCallingIdentity(identity);}
  return base;
 }
 long sampledAt(){return lastSample;}
 String state(){return state;}
 void sample(float raw,float filtered,boolean fresh,boolean dual,boolean unlocked){
  String value="route="+(dual?"screenshot":"normal")+" "+state+" unlocked="+unlocked;
  if(!value.equals(previous)){previous=value;add(SystemClock.elapsedRealtime()+" ms DEVICE_STATE "+value+" raw="+raw+" filtered="+filtered+" fresh="+fresh+" sampleGapMs="+gap);}
 }
 private int identifier(Object state)throws Exception{return state instanceof Number?((Number)state).intValue():(int)state.getClass().getMethod("getIdentifier").invoke(state);}
 private String label(Object state)throws Exception{return state instanceof Number?state.toString():identifier(state)+":"+state.getClass().getMethod("getName").invoke(state);}
 private synchronized void add(String s){events.addLast(s);while(events.size()>80)events.removeFirst();}
 synchronized String report(){return "Base/current fold state (100 ms target; CLOSED vetoes wallpaper motion; callback/poll times are not optical timing):\n"+String.join("\n",events);}
}
