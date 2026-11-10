package org.duofold.live.experimental.b8;
import android.hardware.display.IDisplayManager;
import android.os.SystemClock;
import java.io.*;
import java.util.*;

/** Executes the real refresh against a concurrent-display brightness follower.
 * This model mirrors Android's follower-before-temporary strategy precedence;
 * physical brightness/nits are distinct, never copied from the primary. */
public class LiveB8BrightnessRefreshTest {
 static int checks;
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 public static final class Info {
  public int state=2;public String uniqueId;
  Info(long id){uniqueId="local:"+id;}
 }
 public static final class Brightness {public float brightness=.4f;}
 public static final class Display implements IDisplayManager {
  final Info[] info;final Brightness brightness=new Brightness();
  final List<Integer> calls=new ArrayList<>();final List<Float> values=new ArrayList<>();
  final Map<Long,BrightnessSamples.Sample> commands=new HashMap<>();
  boolean failClear,failProbe,watchdogReady,markersPresent=true;
  File marker;Runnable beforeInfo,onProbe;
  Display(boolean opening){info=new Info[]{new Info(opening?2:1),new Info(opening?1:2)};}
  public Object getDisplayInfo(int id){if(beforeInfo!=null){Runnable r=beforeInfo;beforeInfo=null;r.run();}return info[id];}
  public Object getBrightnessInfo(int id){return brightness;}
  public void setTemporaryBrightness(int id,float value){
   if(!Float.isNaN(value)&&failProbe)throw new IllegalStateException("probe rejected");
   if(Float.isNaN(value)&&failClear)throw new IllegalStateException("clear rejected");
   calls.add(id);values.add(value);
   markersPresent&=marker!=null&&marker.isFile();
   if(!Float.isNaN(value))check(watchdogReady&&markersPresent,"cleanup watchdog and durable marker precede mutation");
   // D1 ignores its own temporary setting while following D0. A D0 update
   // generates separate hardware commands for both physical panels.
   if(id==0){
    for(int n=0;n<2;n++){
     BrightnessSamples.Sample s=new BrightnessSamples.Sample();s.id=Long.parseLong(info[n].uniqueId.substring(6));s.ms=SystemClock.now;
     s.value=Float.isNaN(value)?(n==0?.4f:.3f):(n==0?.41f:.31f);
     s.nits=s.sdr=n==0?100:95;s.ratio=1;commands.put(s.id,s);
    }
   }
   if(!Float.isNaN(value)&&onProbe!=null)onProbe.run();
  }
 }
 static LiveB8BrightnessRefresh create(File dir,Display d,LiveB8BrightnessRefresh.Pause pause){
  PowerExperiment.display=d;
  return new LiveB8BrightnessRefresh(dir,pause,(marker,transaction)->{
   check(transaction==42&&marker.isFile(),"watchdog uses discovered transaction and existing journal");
   d.marker=marker;d.watchdogReady=true;
  });
 }
 interface Action {void run()throws Exception;}
 static void fails(Action a,String why)throws Exception{boolean failed=false;try{a.run();}catch(Exception expected){failed=true;}check(failed,why);}
 public static void main(String[] args)throws Exception{
  File dir=new File(args[0]);dir.mkdirs();
  for(boolean opening:new boolean[]{true,false})for(int missingPanel:new int[]{0,1,2}){
   SystemClock.now=10000;Display d=new Display(opening);
   Set<Long> missing=new HashSet<>();if(missingPanel!=1)missing.add(Long.parseLong(d.info[0].uniqueId.substring(6)));if(missingPanel!=0)missing.add(Long.parseLong(d.info[1].uniqueId.substring(6)));
   LiveB8BrightnessRefresh refresh=create(dir,d,ms->SystemClock.now+=ms);
   refresh.refresh(missing);
   check(d.calls.equals(Arrays.asList(0,0)),"only D0 is nudged and cleared in either fold direction");
   check(d.values.get(0)>.4f&&d.values.get(0)<.405f&&Float.isNaN(d.values.get(1)),"single bounded probe then native brightness policy");
   check(refresh.refreshedPhysical().equals(new HashSet<>(Arrays.asList(1L,2L))),"restoration evidence required for both affected physical panels");
   for(long id:new long[]{1,2}){
    BrightnessSamples.Sample sample=d.commands.get(id);
    check(BrightnessSamples.usable(sample,SystemClock.now)&&sample.ms>=refresh.clearedAt(id),"restored physical command for "+id+" is usable before any endpoint");
   }
   check(d.commands.get(opening?2L:1L).value==.4f&&d.commands.get(opening?1L:2L).value==.3f,"primary and follower keep independent calibrated command values");
   check(!d.marker.exists()&&d.markersPresent,"successful clear removes journal only after policy request");
   check(SystemClock.now==10430,"pair refresh retains bounded 180ms probe and 250ms settle");
   refresh.close();check(d.calls.size()==2,"cleanup after successful refresh cannot re-probe");
  }
  // Demonstrate the former failure: changing D1 yields no hardware commands.
  Display old=new Display(true);old.marker=File.createTempFile("refresh-test-",".pending",dir);old.watchdogReady=true;
  old.setTemporaryBrightness(1,.41f);SystemClock.now+=180;old.setTemporaryBrightness(1,Float.NaN);
  check(old.commands.isEmpty(),"incoming-only refresh reproduces missing-command stall with brightness follower");old.marker.delete();
  // Cancellation during the probe clears it and prevents publication.
  Display canceled=new Display(false);final LiveB8BrightnessRefresh[] active={null};
  active[0]=create(dir,canceled,ms->{SystemClock.now+=ms;if(ms==180)active[0].close();});
  fails(()->active[0].refresh(Collections.singleton(2L)),"canceled refresh cannot finish successfully");
  check(canceled.calls.equals(Arrays.asList(0,0))&&!canceled.marker.exists(),"cancel clears temporary primary and leaves no pending override");
  // Remaps/OFF during the probe still clear the logical override, but reject
  // readiness; no physical sample is used to authorize an unstable mapping.
  for(boolean off:new boolean[]{false,true}){
   Display changed=new Display(true);
   LiveB8BrightnessRefresh refresh=create(dir,changed,ms->{SystemClock.now+=ms;if(ms==180){if(off)changed.info[1].state=1;else changed.info[1].uniqueId="local:9";}});
   fails(()->refresh.refresh(Collections.singleton(1L)),"mapping/power changes reject pair refresh");
   check(Float.isNaN(changed.values.get(changed.values.size()-1))&&!changed.marker.exists(),"mapping failure still clears temporary override");refresh.close();
  }
  for(float invalid:new float[]{Float.NaN,0,-1,1.1f}){
   Display d=new Display(true);d.brightness.brightness=invalid;LiveB8BrightnessRefresh refresh=create(dir,d,ms->{});
   fails(()->refresh.refresh(Collections.singleton(1L)),"unsupported primary brightness cannot be guessed");
   check(d.calls.isEmpty(),"invalid brightness causes no mutation");refresh.close();
  }
  Display clearFailure=new Display(true);clearFailure.failClear=true;
  LiveB8BrightnessRefresh failing=create(dir,clearFailure,ms->SystemClock.now+=ms);
  fails(()->failing.refresh(Collections.singleton(1L)),"failed clear cannot authorize handoff");
  check(clearFailure.marker.exists()&&failing.refreshedPhysical().isEmpty(),"failed clear retains independent cleanup journal and no restored boundary");
  clearFailure.failClear=false;failing.close();check(!clearFailure.marker.exists(),"later cleanup retries clear without another probe");
  Display stopped=new Display(true);LiveB8BrightnessRefresh closed=create(dir,stopped,ms->{});closed.close();
  fails(()->closed.refresh(Collections.singleton(1L)),"closed refresh cannot start later");check(stopped.calls.isEmpty(),"no post-close mutation");
  System.out.println("LiveB8BrightnessRefreshTest: "+checks+" primary/follower, both-direction, restoration and cleanup checks passed (fake platform)");
 }
}
