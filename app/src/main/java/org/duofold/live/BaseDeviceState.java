package org.duofold.live;
import android.os.IBinder;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
/**
 * Whether the hinge's BASE device state is CLOSED (CloseReleaseGate input), shared by CoverHandoff and
 * ConcurrentController. Called from the 4 ms angle poll while a release is deferred, under the AngleReader
 * monitor, so reflection is resolved once and the device_state Binder call is made at most every CACHE_MS.
 * Unknown layout or any failure reports CLOSED, so nothing is ever held because of this class.
 */
final class BaseDeviceState {
 private BaseDeviceState(){}
 static final long CACHE_MS=16;
 private static int closedId=-2;
 private static Object service;private static Method info,identifier;private static Field baseField;
 private static long readAt=-1;private static boolean cached=true;
 static synchronized boolean closed(){
  long now=SystemClock.elapsedRealtime();
  if(readAt>=0&&now-readAt<CACHE_MS)return cached;
  readAt=now;
  try{
   if(closedId==-2){
    closedId=-1;
    Class<?> type=Class.forName("android.hardware.devicestate.DeviceStateManager");
    Object manager=type.getConstructor().newInstance();
    for(Object state:(List<?>)type.getMethod("getSupportedDeviceStates").invoke(manager))
     if("CLOSED".equals(state.getClass().getMethod("getName").invoke(state)))closedId=(int)state.getClass().getMethod("getIdentifier").invoke(state);
   }
   if(closedId<0)return cached=true;
   Object state=info();
   if(baseField==null)baseField=state.getClass().getField("baseState");
   Object base=baseField.get(state);
   if(identifier==null)identifier=base.getClass().getMethod("getIdentifier");
   return cached=(int)identifier.invoke(base)==closedId;
  }catch(Exception e){return cached=true;}
 }
 /** Uncached DeviceStateInfo (currentState, baseState) from the device_state service; the stub is resolved once. */
 static synchronized Object info()throws Exception{
  try{
   if(service==null){
    IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
    info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo");
    service=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);
   }
   return info.invoke(service);
  }catch(Exception e){service=null;throw e;}
 }
}
