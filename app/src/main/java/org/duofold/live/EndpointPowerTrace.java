package org.duofold.live;
import android.os.*;
import java.util.*;
/** Independent ring so task dumps cannot evict endpoint power evidence. */
final class EndpointPowerTrace {
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private Handler handler;
 private long until,lastSample,maxGap;private String previous="";private volatile String apis="Not sampled";
 private Object dm,power;private Class<?> powerApi;private boolean inspected;
 private synchronized void add(String text){events.addLast(SystemClock.elapsedRealtime()+": "+text);while(events.size()>100)events.removeFirst();}
 synchronized void arm(String reason){
  if(handler==null){HandlerThread thread=new HandlerThread("Duo-endpoint-trace");thread.start();handler=new Handler(thread.getLooper());}
  handler.post(()->{until=SystemClock.elapsedRealtime()+3500;previous="";lastSample=0;maxGap=0;add(reason);handler.removeCallbacks(poll);handler.post(poll);});
 }
 private final Runnable poll=new Runnable(){public void run(){if(SystemClock.elapsedRealtime()>until){add("Sampling completed; maximum gap="+maxGap+" ms");return;}sample();handler.postDelayed(this,25);}};
 void sample(){
  long now=SystemClock.elapsedRealtime();if(now>until||now-lastSample<25)return;if(lastSample>0)maxGap=Math.max(maxGap,now-lastSample);lastSample=now;
  try{
   if(dm==null)dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
   if(power==null){powerApi=Class.forName("android.os.IPowerManager");IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"power");power=Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);}
   if(!inspected){
    inspected=true;StringBuilder methods=new StringBuilder();
    for(String name:new String[]{"android.os.IPowerManager","android.hardware.display.IDisplayManager"})for(java.lang.reflect.Method method:Class.forName(name).getMethods()){
     String n=method.getName().toLowerCase(Locale.ROOT);
     if(n.contains("sleep")||n.contains("wake")||n.contains("fold")||n.contains("displaypower")||n.contains("displaystate")||n.contains("stayon"))methods.append(method.toGenericString()).append("; ");
    }
    apis=methods.toString();
   }
   StringBuilder line=new StringBuilder("interactive=").append(powerApi.getMethod("isInteractive").invoke(power));
   try{line.append(" lastSleepReason=").append(powerApi.getMethod("getLastSleepReason").invoke(power));}catch(Exception unavailable){line.append(" lastSleepReason=unavailable");}
   for(int id=0;id<2;id++){
    Object d=dm.getClass().getMethod("getDisplayInfo",int.class).invoke(dm,id);
    line.append(" D").append(id).append('=');
    if(d==null){line.append("missing");continue;}
    Class<?> c=d.getClass();line.append(c.getField("uniqueId").get(d)).append(" state=").append(c.getField("state").get(d)).append(' ').append(c.getField("logicalWidth").get(d)).append('x').append(c.getField("logicalHeight").get(d));
   }
   String next=line.toString();if(!next.equals(previous)){previous=next;add(next);}
  }catch(Exception e){String next="Endpoint sample unavailable: "+e;if(!next.equals(previous)){previous=next;add(next);}}
 }
 synchronized String report(){return "Endpoint power trace (25 ms target; polling gaps possible; software states):\n"+String.join("\n",events)+"\nAvailable power APIs (inspection only): "+apis;}
}
