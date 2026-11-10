package org.duofold.live.experimental.b8;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class BrightnessLogSourceTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static String line(long ms,long id,String value){return String.format(Locale.ROOT,"%.3f I SurfaceFlinger: setDisplayBrightness(%d): displayBrightness: %s, displayBrightnessNits: 100, sdrWhitePointNits: 100, dimmingRatio: 1\n",ms/1000.0,id,value);}
 static class Child extends Process {
  final PipedInputStream input=new PipedInputStream(65536);
  final PipedOutputStream output=new PipedOutputStream(input);
  final CountDownLatch killed=new CountDownLatch(1);
  Child()throws IOException{}
  void emit(String s)throws IOException{output.write(s.getBytes(StandardCharsets.UTF_8));output.flush();}
  public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
  public InputStream getInputStream(){return input;}
  public InputStream getErrorStream(){return new ByteArrayInputStream(new byte[0]);}
  public int waitFor()throws InterruptedException{killed.await();return 0;}
  public boolean waitFor(long timeout,TimeUnit unit)throws InterruptedException{return killed.await(timeout,unit);}
  public boolean isAlive(){return killed.getCount()>0;}
  public int exitValue(){if(isAlive())throw new IllegalThreadStateException();return 0;}
  public void destroy(){killed.countDown();try{output.close();input.close();}catch(IOException ignored){}}
  public Process destroyForcibly(){destroy();return this;}
 }
 static void rejected(BrightnessLogSource source,String why)throws Exception{
  boolean failed=false;try{source.read("refreshed",80);}catch(IOException expected){failed=true;}check(failed,why);
 }
 public static void main(String[] args)throws Exception{
  File dir=new File(args[0]);SystemClock.now=10000;
  Child child=new Child();final int[] launches={0};
  BrightnessLogSource source=new BrightnessLogSource(dir,command->{
   launches[0]++;check(command.contains("-T")&&!command.contains("-d")&&!command.contains("-t"),"continuous reader never waits for a dump exit");
   check(command.get(command.indexOf("-T")+1).equals("1"),"old diagnostic history is not replayed");return child;
  });
  try{
   child.emit(line(9999,1,"0.4"));
   check(source.read("prepare",400).isEmpty(),"pre-preparation brightness is not accepted from initial tail");
   child.emit(line(10010,1,"0.5")+line(10010,2,"0.6"));
   Map<Long,BrightnessSamples.Sample> result=source.read("refreshed",400);
   check(result.size()==2&&child.isAlive(),"complete live commands are accepted without waiting for process exit");
   child.emit(line(10020,2,"-1"));
   check(source.read("refreshed",400).get(2L).value==-1,"latest OFF vetoes older positive command");
   child.emit(line(10015,2,"0.8"));
   check(source.read("refreshed",400).get(2L).value==-1,"out-of-order older positive cannot undo OFF");
   String restored=line(10030,2,"0.7");child.emit(restored.substring(0,restored.length()-1));
   rejected(source,"incomplete record cannot authorize a brightness value");
   child.emit("\n");check(source.read("refreshed",400).get(2L).ms==10030,"split command becomes usable only after complete newline");
   check(launches[0]==1,"every restoration retry shares a single reader");
  }finally{source.close();}
  check(!child.isAlive(),"close kills live reader process");rejected(source,"closed reader cannot restart");
  Child empty=new Child();BrightnessLogSource closing=new BrightnessLogSource(dir,c->empty);
  ExecutorService executor=Executors.newSingleThreadExecutor();
  try{
   Future<Boolean> future=executor.submit(()->{try{closing.read("refreshed",400);return false;}catch(IOException expected){return true;}});
   Thread.sleep(30);closing.close();
   check(future.get(1,TimeUnit.SECONDS)&&!empty.isAlive(),"cancellation wakes pending observation and closes child");
  }finally{closing.close();executor.shutdownNow();}
  Child malformed=new Child();BrightnessLogSource bounded=new BrightnessLogSource(dir,c->malformed);
  try{char[] huge=new char[17000];Arrays.fill(huge,'x');malformed.emit(new String(huge));rejected(bounded,"oversized incomplete command fails closed");}
  finally{bounded.close();}
  Child dead=new Child();BrightnessLogSource failed=new BrightnessLogSource(dir,c->dead);
  dead.emit(line(10050,1,"0.5"));dead.destroy();try{rejected(failed,"exited process cannot publish stale cached positives");}finally{failed.close();}
  final Process[] actual={null};
  BrightnessLogSource real=new BrightnessLogSource(dir,c->{
   actual[0]=new ProcessBuilder("python3","-u","-c","import sys; sys.stdout.write(sys.argv[1]); sys.stdout.flush(); sys.stdin.read()",line(10050,1,"0.5")+line(10050,2,"0.6")).start();return actual[0];
  });
  try{
   check(real.read("refreshed",400).size()==2&&actual[0].isAlive(),"real live pipe supplies complete commands before EOF");
   long start=System.nanoTime();real.read("refreshed",60);
   check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)>=50,"unchanged stream waits for new commands within observation budget");
  }finally{real.close();}
  check(actual[0].waitFor(1,TimeUnit.SECONDS),"real child exits after preparation cleanup");
  System.out.println("BrightnessLogSourceTest: "+checks+" stream, freshness, split-record and cleanup checks passed");
 }
}
