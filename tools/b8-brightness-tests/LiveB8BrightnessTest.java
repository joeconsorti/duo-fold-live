package org.duofold.live.experimental.b8;
import java.io.File;import java.lang.reflect.Field;import java.util.*;import android.view.SurfaceControl;import android.os.SystemClock;
final class BrightnessRefresh {static int refreshes;static Set<Long> refreshed;long cleared;BrightnessRefresh(File d){}void refresh(Set<Long> ids){refreshes++;refreshed=new HashSet<>(ids);cleared=SystemClock.now;}long clearedAt(long id){return cleared;}void close(){}}
public class LiveB8BrightnessTest {
 static int checks;
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static Map<Long,BrightnessSamples.Sample> sample(long coverAt,float value){Map<Long,BrightnessSamples.Sample> result=new HashMap<>();for(long id:new long[]{1,2}){BrightnessSamples.Sample s=new BrightnessSamples.Sample();s.id=id;s.ms=id==1?SystemClock.now:coverAt;s.value=id==1?.5f:value;s.nits=s.sdr=100;s.ratio=1;result.put(id,s);}return result;}
 static void rejected(LiveB8Brightness h,String why)throws Exception{boolean rejected=false;try{h.requirePrepared();}catch(Exception expected){rejected=true;}check(rejected,why);check(((Map<?,?>)field(h,"prepared")).isEmpty(),"failed preparation publishes no samples");h.close();}
 public static void main(String[] args)throws Exception{
  LiveB8Brightness hold=new LiveB8Brightness(new File(args[0]));boolean rejected=false;
  try{hold.requirePrepared();}catch(Exception expected){rejected=true;}check(rejected,"missing samples rejected before shielding");
  Map<Long,BrightnessSamples.Sample> samples=(Map<Long,BrightnessSamples.Sample>)field(hold,"prepared");
  for(long id:new long[]{1,2}){BrightnessSamples.Sample s=new BrightnessSamples.Sample();s.id=id;s.ms=9999;s.value=.5f;s.nits=s.sdr=100;s.ratio=1;samples.put(id,s);}
  hold.requirePrepared();hold.begin("hinge");check(SurfaceControl.writes>=2,"first brightness writes accepted without launching logcat");Thread worker=(Thread)field(hold,"worker");
  hold.begin("endpoint");check(worker==field(hold,"worker"),"endpoint extends same writer");hold.close();worker.join(300);check(!worker.isAlive(),"stop terminates bounded writer");
  File root=new File(args[0]);int[] reads={0};BrightnessRefresh.refreshes=0;
  hold=new LiveB8Brightness(root,(phase,budget)->{reads[0]++;return sample(SystemClock.now,.5f);},ms->SystemClock.now+=ms);
  hold.prepare();hold.requirePrepared();check(reads[0]==1&&BrightnessRefresh.refreshes==0,"valid initial physical samples need no refresh or retries");hold.close();
  reads[0]=0;SystemClock.now=10000;BrightnessRefresh.refreshes=0;
  hold=new LiveB8Brightness(root,(phase,budget)->{
   reads[0]++;
   if(phase.equals("prepare"))return sample(10000,-1);
   check(budget>0&&budget<=400,"restoration read has bounded timeout");
   return reads[0]<4?sample(9999,.51f):sample(SystemClock.now,.5f);
  },ms->SystemClock.now+=ms);
  hold.prepare();hold.requirePrepared();
  check(reads[0]==4&&BrightnessRefresh.refreshes==1&&BrightnessRefresh.refreshed.equals(Collections.singleton(2L)),"delayed restoration retries observation, refreshes only missing panel once");
  check(((Map<Long,BrightnessSamples.Sample>)field(hold,"prepared")).get(2L).value==.5f,"pre-clear probe was not cached");hold.close();
  for(int invalid=0;invalid<3;invalid++){
   final int kind=invalid;reads[0]=0;SystemClock.now=10000;BrightnessRefresh.refreshes=0;
   hold=new LiveB8Brightness(root,(phase,budget)->{reads[0]++;return phase.equals("prepare")?sample(10000,-1):sample(kind==0?9999:SystemClock.now,kind==1?Float.NaN:kind==2?-1:.51f);},ms->SystemClock.now+=ms);
   hold.prepare();rejected(hold,"stale, nonfinite or negative restored values never authorize brightness");
   check(reads[0]==7&&BrightnessRefresh.refreshes==1,"unavailable restoration stops after six observations and one refresh");
  }
  reads[0]=0;SystemClock.now=10000;
  hold=new LiveB8Brightness(root,(phase,budget)->{reads[0]++;if(phase.equals("prepare"))return sample(10000,-1);SystemClock.now+=1201;return sample(SystemClock.now,.5f);},ms->SystemClock.now+=ms);
  hold.prepare();rejected(hold,"fresh values arriving after the observation deadline are rejected");check(reads[0]==2,"deadline prevents further reads");
  reads[0]=0;SystemClock.now=10000;
  hold=new LiveB8Brightness(root,(phase,budget)->{reads[0]++;if(phase.equals("prepare"))return sample(10000,-1);if(reads[0]==2)throw new java.io.IOException("log snapshot temporarily unavailable");return sample(SystemClock.now,.5f);},ms->SystemClock.now+=ms);
  hold.prepare();hold.requirePrepared();check(reads[0]==3,"transient read failure can recover within bounded observation");hold.close();
  SystemClock.now=10000;final LiveB8Brightness[] stopped={null};
  stopped[0]=new LiveB8Brightness(root,(phase,budget)->{if(phase.equals("prepare"))return sample(10000,-1);stopped[0].close();return sample(SystemClock.now,.5f);},ms->SystemClock.now+=ms);
  stopped[0].prepare();rejected(stopped[0],"stop during observation cannot publish restored samples");
  System.out.println("LiveB8BrightnessTest: "+checks+" brightness freshness, bounded retry, cached-only writer and cleanup checks passed (fake platform)");
 }
}
