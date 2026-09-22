package org.duofold.live;
import android.os.*;
import java.util.ArrayDeque;
/** Read-only state samples, not proof of illuminated pixels. */
final class ContinuityProbeDiagnostics {
 private final ArrayDeque<String> events=new ArrayDeque<>();
 private boolean wasActive;private long last,maxGap,endedAt;private String previous="",previousStatus="";
 String report(){return "Display samples (software state; not optical measurements); maximum sample gap="+maxGap+" ms\n"+String.join("\n",events);}
 void sample(boolean active,String status){
  long now=SystemClock.elapsedRealtime();
  if(active&&!wasActive){events.clear();last=0;maxGap=0;previous="";}
  String route=status.contains("\n")?status.substring(status.indexOf('\n')+1):"";
  if(active&&!route.equals(previousStatus))add(now+" "+route);
  previousStatus=route;
  if(!active&&wasActive){endedAt=now;add(now+" test hold ended: "+status);}
  wasActive=active;if(!active&&(endedAt==0||now-endedAt>2000))return;if(last>0&&now-last<100)return;
  if(last>0)maxGap=Math.max(maxGap,now-last);last=now;
  long identity=Binder.clearCallingIdentity();
  try{
   IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");
   Object dm=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
   StringBuilder line=new StringBuilder();
   for(int id=0;id<=1;id++){
    Object info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,id);
    if(info==null){line.append(" display ").append(id).append(" absent");continue;}
    Class<?> c=info.getClass();line.append(" display ").append(id).append(" physical=").append(c.getField("uniqueId").get(info)).append(" state=").append(c.getField("state").getInt(info)).append(" size=").append(c.getField("logicalWidth").getInt(info)).append('x').append(c.getField("logicalHeight").getInt(info));
   }
   String value=line.toString();if(!value.equals(previous)){previous=value;add(now+value);}
  }catch(Exception e){String value="Sampling error: "+e;if(!value.equals(previous)){previous=value;add(now+" "+value);}}
  finally{Binder.restoreCallingIdentity(identity);}
 }
 private void add(String value){if(events.size()==32)events.removeFirst();events.addLast(value);}
}
