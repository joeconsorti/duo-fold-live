package org.duofold.live.experimental.b8;
import java.io.File;import java.lang.reflect.Field;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;import android.view.SurfaceControl;import android.os.SystemClock;
final class LiveB8BrightnessRefresh {static int refreshes;static Set<Long> refreshed;long cleared;LiveB8BrightnessRefresh(File d){}void refresh(Set<Long> ids){refreshes++;refreshed=new HashSet<>(ids);cleared=SystemClock.now;}long clearedAt(long id){return cleared;}Set<Long> refreshedPhysical(){return new HashSet<>(Arrays.asList(1L,2L));}void close(){}}
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
  File root=new File(args[0]);int[] reads={0};LiveB8BrightnessRefresh.refreshes=0;
  // The early pass reads during panel wake, but cannot refresh until the
  // controller permits it. Cleanup during that wait must never start a probe.
  for(boolean cancel:new boolean[]{false,true}){
   LiveB8BrightnessRefresh.refreshes=0;SystemClock.now=10000;
   CountDownLatch waiting=new CountDownLatch(1),resume=new CountDownLatch(1);
   LiveB8Brightness early=new LiveB8Brightness(root,(phase,budget)->phase.equals("prepare")?sample(10000,-1):sample(SystemClock.now,.5f),ms->{waiting.countDown();if(!resume.await(1,TimeUnit.SECONDS))throw new AssertionError("test permission not delivered");SystemClock.now+=ms;});
   early.prepareEarlyAsync();check(waiting.await(1,TimeUnit.SECONDS),"early read completes before refresh permission");
   check(LiveB8BrightnessRefresh.refreshes==0&&early.preparing(),"missing samples cannot trigger an early brightness probe");
   if(cancel)early.close();else early.allowRefresh();resume.countDown();
   long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(early.preparing()&&System.nanoTime()<until)Thread.yield();
   check(!early.preparing(),"early worker finishes after cancellation or permission");
   if(cancel){rejected(early,"canceled early read cannot publish samples");check(LiveB8BrightnessRefresh.refreshes==0,"cleanup prevents a delayed refresh");}
   else{early.requirePrepared();check(LiveB8BrightnessRefresh.refreshes==1,"both-ON permission permits exactly one refresh");early.close();}
  }
  LiveB8BrightnessRefresh.refreshes=0;
  LiveB8Brightness earlyTimeout=new LiveB8Brightness(root,(phase,budget)->sample(10000,-1),ms->SystemClock.now+=ms);
  earlyTimeout.prepareEarlyAsync();long earlyEnd=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
  while(earlyTimeout.preparing()&&System.nanoTime()<earlyEnd)Thread.yield();
  check(!earlyTimeout.preparing()&&LiveB8BrightnessRefresh.refreshes==0,"missing panel readiness expires without a brightness probe");rejected(earlyTimeout,"timed-out early read supplies no samples");
  SystemClock.now=10000;LiveB8BrightnessRefresh.refreshes=0;
  hold=new LiveB8Brightness(root,(phase,budget)->{reads[0]++;return sample(SystemClock.now,.5f);},ms->SystemClock.now+=ms);
  hold.prepare();hold.requirePrepared();check(reads[0]==1&&LiveB8BrightnessRefresh.refreshes==0,"valid initial physical samples need no refresh or retries");hold.close();
  reads[0]=0;SystemClock.now=10000;LiveB8BrightnessRefresh.refreshes=0;
  hold=new LiveB8Brightness(root,(phase,budget)->{
   reads[0]++;
   if(phase.equals("prepare"))return sample(10000,-1);
   check(budget>0&&budget<=400,"restoration read has bounded timeout");
   return reads[0]<4?sample(9999,.51f):sample(SystemClock.now,.5f);
  },ms->SystemClock.now+=ms);
  hold.prepare();hold.requirePrepared();
  check(reads[0]==4&&LiveB8BrightnessRefresh.refreshes==1&&LiveB8BrightnessRefresh.refreshed.equals(Collections.singleton(2L)),"one missing panel triggers exactly one pair refresh; delayed restoration retries observation only");
  check(((Map<Long,BrightnessSamples.Sample>)field(hold,"prepared")).get(2L).value==.5f,"pre-clear probe was not cached");hold.close();
  // A missing incoming value causes a primary-driven pair refresh. The old
  // outgoing sample must NOT authorize a hold, even though it was valid before.
  for(long stalePanel:new long[]{1,2}){
   reads[0]=0;SystemClock.now=10000;LiveB8BrightnessRefresh.refreshes=0;
   hold=new LiveB8Brightness(root,(phase,budget)->{
    reads[0]++;
    if(phase.equals("prepare"))return sample(10000,-1);
    Map<Long,BrightnessSamples.Sample> pair=sample(SystemClock.now,.5f);
    if(reads[0]<3)pair.get(stalePanel).ms=9999;
    return pair;
   },ms->SystemClock.now+=ms);
   hold.prepare();hold.requirePrepared();
   check(reads[0]==3&&LiveB8BrightnessRefresh.refreshes==1,"both physical panels require post-clear restoration, including previously valid primary "+stalePanel);
   hold.close();
  }
  for(int invalid=0;invalid<3;invalid++){
   final int kind=invalid;reads[0]=0;SystemClock.now=10000;LiveB8BrightnessRefresh.refreshes=0;
   hold=new LiveB8Brightness(root,(phase,budget)->{reads[0]++;return phase.equals("prepare")?sample(10000,-1):sample(kind==0?9999:SystemClock.now,kind==1?Float.NaN:kind==2?-1:.51f);},ms->SystemClock.now+=ms);
   hold.prepare();rejected(hold,"stale, nonfinite or negative restored values never authorize brightness");
   check(reads[0]==7&&LiveB8BrightnessRefresh.refreshes==1,"unavailable restoration stops after six observations and one refresh");
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
  for(boolean available:new boolean[]{false,true}){
   reads[0]=0;LiveB8BrightnessRefresh.refreshes=0;
   hold=new LiveB8Brightness(root,(phase,budget)->{reads[0]++;check(budget==400,"passive observation is bounded at 400ms");return sample(SystemClock.now,available?.5f:-1);},ms->{throw new AssertionError("passive read must not sleep or nudge");});
   hold.preparePassive();
   check(reads[0]==1&&LiveB8BrightnessRefresh.refreshes==0,"Home never triggers a temporary brightness probe or restoration retry");
   if(available){hold.requirePrepared();hold.close();}else rejected(hold,"missing passive samples cannot authorize a brightness write");
  }
  LiveB8BrightnessRefresh.refreshes=0;
  hold=new LiveB8Brightness(root,(phase,budget)->{throw new java.io.IOException("logcat timeout");},ms->{throw new AssertionError("passive failure must not sleep");});
  hold.preparePassive();rejected(hold,"failed passive observation publishes no guessed brightness");check(LiveB8BrightnessRefresh.refreshes==0,"read timeout never probes brightness");
  // First Home opening: old/missing cover sample is refreshed before remap,
  // and only a restored command is published, never the probe itself.
  reads[0]=0;SystemClock.now=10000;LiveB8BrightnessRefresh.refreshes=0;
  hold=new LiveB8Brightness(root,(phase,budget)->{
   reads[0]++;check(budget>0&&budget<=400,"Home logcat observations stay bounded");
   return phase.equals("prepare")?sample(0,-1):reads[0]==2?sample(9999,.51f):sample(SystemClock.now,.5f);
  },ms->SystemClock.now+=ms);
  hold.prepareHomeAsync();
  long waitUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
  while(hold.preparing()&&System.nanoTime()<waitUntil)Thread.yield();
  check(!hold.preparing(),"Home preparation terminates");hold.requirePrepared();
  check(reads[0]==3&&LiveB8BrightnessRefresh.refreshes==1,"first Home opening repairs missing sample with one refresh and post-clear observation");
  hold.begin("home");worker=(Thread)field(hold,"worker");
  check(!hold.retainCoverTail("unknown")&&!hold.retainCoverTail("local:3"),"tail rejects unknown physical panel without changing ownership");
  check(hold.retainCoverTail("local:2"),"active Home writer narrows to cover");
  int afterTail=SurfaceControl.panels.size();CountDownLatch coverWrite=new CountDownLatch(1);
  SurfaceControl.onWrite=id->{if(id==2)coverWrite.countDown();};
  check(coverWrite.await(1,TimeUnit.SECONDS),"cover receives a command during the existing tail");
  hold.close();worker.join(500);SurfaceControl.onWrite=null;
  check(!worker.isAlive(),"cover tail writer is stopped on cleanup");
  for(long id:SurfaceControl.panels.subList(afterTail,SurfaceControl.panels.size()))check(id==2,"no inner commands after tail narrowing");
  check(!hold.retainCoverTail("local:2"),"closed writer cannot be revived");
  hold=new LiveB8Brightness(root,(phase,budget)->sample(SystemClock.now,.5f),ms->SystemClock.now+=ms);
  hold.prepare();check(!hold.retainCoverTail("local:2"),"prepared samples alone cannot start a writer at full close");
  hold.begin("expiry");worker=(Thread)field(hold,"worker");SystemClock.now+=2001;
  check(!hold.retainCoverTail("local:2"),"expired brightness hold cannot be revived by the endpoint");hold.close();worker.join(500);
  // Exercise both physical directions. Reject either token on its second write
  // (during the remap), after both mandatory pre-switch writes have succeeded.
  for(long outgoing:new long[]{1,2})for(long failing:new long[]{1,2}){
   hold=new LiveB8Brightness(root,(phase,budget)->sample(SystemClock.now,.5f),ms->SystemClock.now+=ms);
   hold.prepare();SurfaceControl.panels.clear();
   java.util.concurrent.atomic.AtomicInteger attempts=new java.util.concurrent.atomic.AtomicInteger();
   CountDownLatch recovered=new CountDownLatch(1);
   SurfaceControl.accept=id->{
    if(id!=failing)return true;
    int n=attempts.incrementAndGet();
    if(n==2){if(failing==outgoing)throw new IllegalStateException("display remapping");return false;}
    if(n==3)recovered.countDown();
    return true;
   };
   hold.beginAppHandoff("local:"+outgoing);
   check(recovered.await(1,TimeUnit.SECONDS),"physical token is retried after transient remap rejection");
   hold.close();
   check(SurfaceControl.panels.size()>=5&&SurfaceControl.panels.get(2)==outgoing&&SurfaceControl.panels.indexOf(3-outgoing)>=0&&java.util.Collections.frequency(SurfaceControl.panels,3-outgoing)>=2,"outgoing goes first and rejection does not cancel the peer");
   String stats=hold.flashReport();
   check(stats.contains("outgoing=local:"+outgoing)&&!stats.contains("extraAttempts=0")&&stats.contains("rejected=1")&&stats.contains("firstRecoveryAt="+SystemClock.now),"flash evidence records failed token and recovery");
  }
  SurfaceControl.accept=id->true;
  hold=new LiveB8Brightness(root,(phase,budget)->sample(SystemClock.now,.5f),ms->SystemClock.now+=ms);hold.prepare();
  hold.beginAppHandoff("local:2");SystemClock.now+=320;
  CountDownLatch beyondWindow=new CountDownLatch(4);SurfaceControl.onWrite=id->beyondWindow.countDown();
  check(beyondWindow.await(1,TimeUnit.SECONDS),"normal paired writes continue past outgoing window");
  int attemptsAtBound=(Integer)field(hold,"extraWrites");
  CountDownLatch nextPairs=new CountDownLatch(4);SurfaceControl.onWrite=id->nextPairs.countDown();
  check(nextPairs.await(1,TimeUnit.SECONDS),"paired lease survives end of outgoing burst");
  hold.close();SurfaceControl.onWrite=null;
  check((Integer)field(hold,"extraWrites")==attemptsAtBound,"extra writes stop at the 320ms cutoff");
  SurfaceControl.accept=id->id!=2;
  hold=new LiveB8Brightness(root,(phase,budget)->sample(SystemClock.now,.5f),ms->SystemClock.now+=ms);hold.prepare();
  rejected=false;try{hold.beginAppHandoff("local:1");}catch(java.io.IOException expected){rejected=true;}
  check(rejected&&field(hold,"worker")==null,"both first writes still required to arm handoff");
  hold.close();SurfaceControl.accept=id->true;
  // Teardown must serialize with an in-flight Binder call, not just set a flag
  // between the worker's check and its write.
  hold=new LiveB8Brightness(root,(phase,budget)->sample(SystemClock.now,.5f),ms->SystemClock.now+=ms);hold.prepare();
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),closeStarted=new CountDownLatch(1),closeDone=new CountDownLatch(1);
  AtomicBoolean armed=new AtomicBoolean(false);
  SurfaceControl.onWrite=id->{if(armed.compareAndSet(true,false)){entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new AssertionError("test write was never released");}catch(InterruptedException e){throw new AssertionError(e);}}};
  hold.begin("close-race");worker=(Thread)field(hold,"worker");armed.set(true);
  check(entered.await(1,TimeUnit.SECONDS),"worker entered controlled in-flight physical write");
  final LiveB8Brightness closingHold=hold;
  Thread closer=new Thread(()->{closeStarted.countDown();closingHold.close();closeDone.countDown();});closer.start();
  check(closeStarted.await(1,TimeUnit.SECONDS),"cleanup started concurrently");
  boolean returnedEarly=closeDone.await(50,TimeUnit.MILLISECONDS);release.countDown();
  check(!returnedEarly,"cleanup cannot return while an older physical write is in flight");
  check(closeDone.await(1,TimeUnit.SECONDS),"cleanup finishes after physical write returns");
  int finalWrites=SurfaceControl.writes;worker.join(500);closer.join(500);SurfaceControl.onWrite=null;
  check(!worker.isAlive()&&SurfaceControl.writes==finalWrites,"no physical writes arrive after cleanup returns");
  System.out.println("LiveB8BrightnessTest: "+checks+" brightness freshness, bounded retry, cached-only writer and cleanup checks passed (fake platform)");
 }
}
