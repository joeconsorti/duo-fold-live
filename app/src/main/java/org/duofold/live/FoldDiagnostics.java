package org.duofold.live;
import android.content.*;
import android.os.*;
import java.util.LinkedHashSet;
import java.io.File;
/** App-side correlation; persistence is queued, with no disk waits on the reader/render thread. */
public final class FoldDiagnostics {
 private static final DisplayPowerEvidence power=new DisplayPowerEvidence();
 private static final DiagnosticTimeline timeline=new DiagnosticTimeline(4096,2400);
 private static final LinkedHashSet<String> imported=new LinkedHashSet<>();
 private static long began=SystemClock.elapsedRealtime(),nextSample,nextHealth,lastHealth,lastCpu;
 private static String previousTrace="",previousState="",previousPause="",previousRotation="",previousRotationStatus="";
 private static volatile String scene="session=0 stage=IDLE";
 private static boolean initialized;
 private static volatile PersistentFoldJournal journal;
 private static long persistentInputAt,persistentHealthAt;
 private static String previousRecovery="";
 private static final String[] mappings={"",""};
 public static void init(Context c){
  if(initialized)return;initialized=true;
  Context app=c.getApplicationContext();
  long wall=System.currentTimeMillis();int pid=android.os.Process.myPid();
  journal=new PersistentFoldJournal(new File(app.getFilesDir(),"fold-restart-history"),wall+"-"+pid+"-"+began,()->{
   String boot;
   try{boot=new String(java.nio.file.Files.readAllBytes(new File("/proc/sys/kernel/random/boot_id").toPath()),java.nio.charset.StandardCharsets.UTF_8).trim();}catch(Exception e){boot="unavailable";}
   int count=-1;try{count=android.provider.Settings.Global.getInt(app.getContentResolver(),"boot_count",-1);}catch(Exception ignored){}
   return "build="+BuildConfig.VERSION_NAME+" pid="+pid+" wallMs="+wall+" processStartElapsedMs="+began+" bootId="+boot+" bootCount="+count+"\nSame boot identity across processes does not imply a kernel reboot. Software metadata, not visible pixels.\nTimeline columns: wallMs observedElapsedMs receivedElapsedMs kind details";
  });
  event("PROCESS","build="+BuildConfig.VERSION_NAME+" pid="+android.os.Process.myPid()+" wallMs="+System.currentTimeMillis());
  c.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent i){event("BATTERY","batteryTenthsC="+i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1)+" plugged="+i.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1)+"; battery sensor only");}},new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
 }
 public static void event(String kind,String detail){long now=SystemClock.elapsedRealtime();add(now,now,kind,scene+" "+detail);}
 private static void add(long observed,long received,String kind,String detail){
  timeline.add(observed,received,kind,detail);
  PersistentFoldJournal j=journal;if(j==null)return;
  boolean active=!scene.startsWith("session=0 ");
  // Idle samples add no fold detail; avoid continuous disk churn while the phone is still.
  if("INPUT".equals(kind)){if(!active&&received-persistentInputAt<5000)return;persistentInputAt=received;}
  if("HEALTH".equals(kind)){if(!active&&received-persistentHealthAt<10000)return;persistentHealthAt=received;}
  j.append(System.currentTimeMillis()+"\t"+observed+"\t"+received+"\t"+kind+"\t"+detail);
 }
 public static String scene(){return scene;}
 public static void reader(Bundle b,boolean interactive,boolean locked,int thermal,long roundTrip){
  long now=SystemClock.elapsedRealtime();Bundle s=b.getBundle("b8Presentation");
  String value="session="+(s==null?0:s.getLong("session"))+" stage="+(s==null?"IDLE":s.getString("directStage","").isEmpty()?"MOVING/PREPARING":s.getString("directStage"))+" task="+(s==null?-1:s.getInt("task",-1))+" home="+(s!=null&&s.getBoolean("home"))+" check="+(s==null?0:s.getLong("primaryCheck"))+" revision="+(s==null?0:s.getInt("revision"));
  if(!value.equals(scene)){scene=value;event("SCENE","mapped="+(s!=null&&s.getBoolean("sceneMapped"))+" capturePhysical="+(s==null?"":s.getString("capturePhysical")));}
  Bundle panels=b.getBundle("panelIdentity");
  if(panels!=null)for(int id=0;id<2;id++){
   Bundle panel=panels.getBundle("d"+id);
   String mapping=panel==null?"unavailable":"physical="+panel.getString("id")+" state="+panel.getInt("state")+" size="+panel.getInt("w")+"x"+panel.getInt("h")+" rotation="+panel.getInt("r");
   if(!mapping.equals(mappings[id])){mappings[id]=mapping;add(panels.getLong("stamp"),now,"PANEL_MAPPING",scene+" D"+id+" "+mapping);}
  }
  if(panels!=null){Bundle a=panels.getBundle("d0"),z=panels.getBundle("d1");
   for(String row:power.observe(panels.getLong("stamp"),a==null?"":a.getString("id",""),a==null?0:a.getInt("state"),z==null?"":z.getString("id",""),z==null?0:z.getInt("state")))add(panels.getLong("stamp"),now,"POWER_INTERVAL",scene+" interactive="+interactive+" "+row);
  }
  String pause=b.getString("hingePauseReport","");if(!pause.equals(previousPause)){previousPause=pause;event("PAUSE",pause);}
  String state=b.getString("physicalState","unavailable");
  if(!state.equals(previousState)){previousState=state;event("DEVICE_STATE",state+" sampleAt="+b.getLong("physicalBaseAt"));}
  if(now>=nextSample){nextSample=now+250;event("INPUT","rawWallpaper="+b.getFloat("rawAngle")+" accepted="+b.getFloat("angle")+" replyAt="+b.getLong("last")+" replyAgeMs="+(now-b.getLong("last"))+" base="+b.getInt("physicalBase",-1)+" baseAgeMs="+(now-b.getLong("physicalBaseAt"))+" interactive="+interactive+" keyguard="+locked+" roundTripMs="+roundTrip+"; receipt freshness is not sensor freshness");}
  if(now>=nextHealth){nextHealth=now+1000;long cpu=android.os.Process.getElapsedCpuTime();event("HEALTH","thermal="+thermal+" appCpuDeltaMs="+(lastHealth>0?cpu-lastCpu:-1)+" intervalMs="+(lastHealth>0?now-lastHealth:-1)+"; excludes shell/GPU/compositor; sampling gaps remain unmeasured");lastCpu=cpu;lastHealth=now;}
  String rotation=b.getString("rotationHold","");
  String rotationStatus=rotation.split("\n",2)[0];
  if(b.containsKey("rotationHold")&&!rotationStatus.equals(previousRotationStatus)){previousRotationStatus=rotationStatus;event("ROTATION_STATUS",rotationStatus+"; observed on reader receipt, not the time the hold changed");}
  if(b.containsKey("rotationHold")&&!rotation.equals(previousRotation)){previousRotation=rotation;importTrace(rotation,now,"ROTATION");}
  String trace=b.getString("bridgeTrace","");
  if(!trace.equals(previousTrace)){
   previousTrace=trace;importTrace(trace,now,"SHELL");
   int start=trace.indexOf("Recovery evidence:");
   if(start>=0){String recovery=trace.substring(start);if(!recovery.equals(previousRecovery)){previousRecovery=recovery;if(journal!=null)journal.recovery(recovery);}}
  }
 }
 private static void importTrace(String trace,long now,String kind){
  for(String line:trace.split("\n")){
   int split=line.indexOf(' ');if(split<1||!Character.isDigit(line.charAt(0)))continue;
   try{long at=Long.parseLong(line.substring(0,split));if(at<began||at>now||!imported.add(line))continue;add(at,now,kind,line.substring(split+1));while(imported.size()>320){java.util.Iterator<String> it=imported.iterator();it.next();it.remove();}}
   catch(NumberFormatException ignored){}
  }
 }
 public static String report(){return (journal==null?"Persistent history not initialized":journal.report())+"\n"+power.report()+"\nCurrent-process fold timeline (last 240 entries; full bounded timeline and retained processes in diagnostic ZIP). Software state/capture evidence only.\n"+timeline.report(240);}
 public static String export(){return "build="+BuildConfig.VERSION_NAME+" processStart="+began+"\nRows are receipt order; sort observedElapsedMs for correlation. Shell events retain their original timestamps. No exact optical blackout duration.\n"+timeline.report(4096);}
 public static void archive(java.util.zip.ZipOutputStream zip)throws java.io.IOException{if(journal!=null)journal.archive(zip);}
}
