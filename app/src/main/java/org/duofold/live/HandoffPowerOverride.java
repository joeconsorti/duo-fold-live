package org.duofold.live;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
/** Opt-in experiment using Samsung's observed per-display call shape. */
final class HandoffPowerOverride {
 private final HandlerThread worker=new HandlerThread("Duo-power-override");
 private final Handler handler;
 private final IBinder[] tokens={new Binder(),new Binder()};
 private final boolean[] pending={false,false};
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private Object manager;private Method setter;private boolean attempted;private int generation,retries;
 HandoffPowerOverride(){worker.start();handler=new Handler(worker.getLooper());}
 private synchronized void log(String message){events.addLast(SystemClock.elapsedRealtime()+": "+message);while(events.size()>24)events.removeFirst();}
 synchronized String report(){return "Samsung ON override experiment:\n"+String.join("\n",events);}
 void newSession(){handler.post(()->{clear("new session");attempted=pending[0]||pending[1];retries=0;});}
 void arm(){handler.post(()->{
  if(attempted)return;attempted=true;final int epoch=++generation;
  long identity=Binder.clearCallingIdentity();
  try{
   if(setter==null){Class<?> api=Class.forName("android.hardware.display.IDisplayManager");IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");manager=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);setter=api.getMethod("setDisplayStateOverrideWithDisplayId",IBinder.class,int.class,int.class,int.class);}
   log("REQUEST ON D0 + D1; explicit release in 2000 ms; accepted is not optical proof");
   handler.postDelayed(()->{if(epoch==generation)clear("two-second deadline");},2000);
   for(int id=0;id<2;id++){pending[id]=true;try{setter.invoke(manager,tokens[id],2,id,10000);}catch(Exception rejected){if(root(rejected) instanceof SecurityException)pending[id]=false;throw rejected;}log("D"+id+" ON override accepted");}
  }catch(Exception e){log("ON override rejected/unavailable: "+root(e));clear("failed request");}
  finally{Binder.restoreCallingIdentity(identity);}
 });}
 void cancelUnlessFoldSleep(){handler.post(()->{
  try{
   Class<?> api=Class.forName("android.os.IPowerManager");IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"power");
   Object power=Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
   if(!(boolean)api.getMethod("isInteractive").invoke(power)&&(int)api.getMethod("getLastSleepReason").invoke(power)==13){log("Fold sleep observed; keep existing ON lease until deadline");return;}
  }catch(Exception ignored){}
  generation++;attempted=true;clear("host no longer usable");
 });}
 void cancel(String reason){handler.post(()->{generation++;attempted=true;clear(reason);});}
 private void clear(String reason){
  long identity=Binder.clearCallingIdentity();
  try{
   for(int id=0;id<2;id++)if(pending[id]){
    try{setter.invoke(manager,tokens[id],0,id,-1);pending[id]=false;log("D"+id+" override released: "+reason);}
    catch(Exception e){log("D"+id+" release pending: "+root(e));}
   }
   if((pending[0]||pending[1])&&retries++<12)handler.postDelayed(()->clear("release retry"),250);
  }finally{Binder.restoreCallingIdentity(identity);}
 }
 private static Throwable root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
}
