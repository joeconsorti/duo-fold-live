package org.duofold.live;
import android.os.*;
/** Read-only shell display mapping, shared by both animation routes. */
final class ShellPanelIdentity {
 private Object manager;private java.lang.reflect.Method getInfo;private Bundle cached;private long sampled;
 Bundle snapshot(){
  long now=SystemClock.elapsedRealtime();if(cached!=null&&now-sampled<32)return cached;
  Bundle result=new Bundle();result.putLong("stamp",now);
  long caller=Binder.clearCallingIdentity();
  try{
   if(manager==null){IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");manager=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);getInfo=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class);}
   for(int d=0;d<2;d++){Object info=getInfo.invoke(manager,d);if(info==null)continue;Class<?> c=info.getClass();Bundle row=new Bundle();row.putString("id",String.valueOf(c.getField("uniqueId").get(info)));row.putInt("w",c.getField("logicalWidth").getInt(info));row.putInt("h",c.getField("logicalHeight").getInt(info));row.putInt("r",c.getField("rotation").getInt(info));row.putInt("state",c.getField("state").getInt(info));result.putBundle("d"+d,row);}
  }catch(Exception e){manager=null;result=new Bundle();result.putLong("stamp",now);}finally{Binder.restoreCallingIdentity(caller);}
  sampled=now;cached=result;return result;
 }
}
