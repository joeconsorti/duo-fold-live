package org.duofold.live;
import android.os.*;import java.util.concurrent.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.atomic.AtomicInteger;
public class WindowPriorityTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
  ServiceManager.service=(fd,options)->{
   int n=calls.incrementAndGet();if(n==1){entered.countDown();if(!release.await(2,TimeUnit.SECONDS))throw new AssertionError("test release missing");}
   ParcelFileDescriptor.output(fd).write(("window sample "+n+"\n").getBytes(StandardCharsets.UTF_8));
  };
  var executor=Executors.newFixedThreadPool(2);
  try{
   long token=WindowWorkPriority.INSTANCE.optionalToken();
   var optional=executor.submit(()->{try{return EndpointWindowTrace.dumpOptional(token);}catch(Exception e){return "REJECTED";}});
   check(entered.await(1,TimeUnit.SECONDS),"optional IPC started");
   var critical=executor.submit(EndpointWindowTrace::dump);
   try{check(critical.get(1,TimeUnit.SECONDS).equals("window sample 2\n"),"critical reads while optional IPC remains blocked");}
   finally{release.countDown();}
   check(optional.get(1,TimeUnit.SECONDS).equals("REJECTED"),"superseded optional result cannot publish");
   check(EndpointWindowTrace.dump().startsWith("window sample"),"critical lane remains usable after optional timeout/rejection");
   WindowWorkPriority.INSTANCE.enterCritical();
   try{int before=calls.get();try{EndpointWindowTrace.dumpOptional(token);throw new AssertionError("optional should yield");}catch(java.io.IOException expected){}check(before==calls.get(),"busy app work prevents optional Binder call");}
   finally{WindowWorkPriority.INSTANCE.leaveCritical();}
   System.out.println("WindowPriorityTest: blocked optional IPC cannot reserve critical lane; stale optional result rejected; critical lane reusable; optional yields before Binder (fake service)");
  }finally{release.countDown();executor.shutdownNow();}
 }
}
