package org.duofold.live.experimental.b8;
import android.content.ComponentName;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Moves only the selected app's exclusive fullscreen root. No Home, no input injection. */
final class TaskMover {
 private final File dir;private final String pkg;
 private final int options;private final PowerExperiment power;
 private boolean outbound,returned,closing,powerAborted,focusOut,focusBack;private int off=-1;
 private Object manager;private Class<?> api;
 private int root=-1,task=-1;private boolean attempted,returnAttempted,pendingReturn;
 private String identity0,identity1;private String innerIdentity,coverIdentity;private long geometrySince=-1;private String geometryCandidate="";private long lastSample;
 private boolean promotionAttempted,promotionPending,promotionVerified,promotionRetargetAttempted;private long promotionDeadline;
 private String lastFocus="";
 private ShieldBridge bridge;private boolean bridgeAttempted,contentRecoveryAttempted;private long bridgeVerifiedAge=-1;
 private ShieldBridge returnBridge;private boolean returnBridgeAttempted,nativeReleaseAttempted,nativeReleaseVerified,nativeRetargetAttempted;private long nativeVerifiedAge=-1,lastBridgeGuard,nativeCandidateSince=-1;private String nativeCandidate="",lastNativeWait="not observed";
 TaskMover(File d,String p,int o,PowerExperiment e){RoutePlan.validate(o);dir=d;pkg=p;options=o;power=e;}
 private void log(String s){try(FileWriter w=new FileWriter(new File(dir,"task-route.txt"),true)){w.write(SystemClock.elapsedRealtime()+" "+s+"\n");}catch(IOException ignored){}}
 private Object call(String method,Class<?>[] types,Object...args)throws Exception{return api.getMethod(method,types).invoke(manager,args);}
 private static int num(Object o,String f)throws Exception{return o.getClass().getField(f).getInt(o);}
 private static Object field(Object o,String f)throws Exception{return o.getClass().getField(f).get(o);}
 private static int wc(Object o,String method)throws Exception{Object c=field(o,"configuration"),w=field(c,"windowConfiguration");return (int)w.getClass().getMethod(method).invoke(w);}
 private String displayIdentity(int id)throws Exception{return displayIdentity(id,true);}
 private String displayIdentity(int id,boolean requireOn)throws Exception{
  Object dm=PowerExperiment.service("display","android.hardware.display.IDisplayManager");Object d=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,id);
  if(d==null||(requireOn&&(num(d,"state")!=2||num(d,"committedState")!=2)))throw new IllegalStateException("D"+id+" not both requested/committed ON");return String.valueOf(field(d,"uniqueId"));
 }
 private Object displayInfo(int id)throws Exception{
  Object dm=PowerExperiment.service("display","android.hardware.display.IDisplayManager");
  return Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,id);
 }
 private int displayWidth(int id)throws Exception{return num(displayInfo(id),"logicalWidth");}
 private int displayHeight(int id)throws Exception{return num(displayInfo(id),"logicalHeight");}
 private String geometry(Object r)throws Exception{
  Object config=field(r,"configuration"),window=field(config,"windowConfiguration");
  Object bounds=window.getClass().getMethod("getMaxBounds").invoke(window);
  int w=(int)bounds.getClass().getMethod("width").invoke(bounds),h=(int)bounds.getClass().getMethod("height").invoke(bounds);
  int dw=displayWidth(0),dh=displayHeight(0);
  if(w!=dw||h!=dh)throw new IllegalStateException("Geometry pending task="+w+"x"+h+" display="+dw+"x"+dh);
  return displayIdentity(0)+":"+w+"x"+h+":"+field(r,"configuration");
 }
 private void unlocked()throws Exception{
  Object wm=PowerExperiment.service("window","android.view.IWindowManager");
  if((boolean)Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Keyguard locked");
  Object pm=PowerExperiment.service("power","android.os.IPowerManager");
  if(!(boolean)Class.forName("android.os.IPowerManager").getMethod("isInteractive").invoke(pm))throw new IllegalStateException("Not interactive");
 }
 private Object findRoot()throws Exception{
  for(int id=0;id<=1;id++)for(Object r:(List<?>)call("getAllRootTaskInfosOnDisplay",new Class<?>[]{int.class},id))if(num(r,"taskId")==root)return r;
  return null;
 }
 private void validateRoot(Object r)throws Exception{
  if(r==null||wc(r,"getActivityType")!=1||wc(r,"getWindowingMode")!=1)throw new IllegalStateException("Root missing or not standard fullscreen");
  ComponentName top=(ComponentName)field(r,"topActivity");
  if(top==null||!pkg.equals(top.getPackageName()))throw new IllegalStateException("Top activity no longer selected app");
  if(!TaskGuard.exclusive(num(r,"taskId"),task,(int[])field(r,"childTaskIds")))throw new IllegalStateException("Root contains unrelated tasks");
 }
 private StringBuilder selectionDiagnostics;
 private void selectionDiagnostic(String label,Object value){
  try{
   if(selectionDiagnostics==null)return;
   selectionDiagnostics.append(label);
   if(value==null){selectionDiagnostics.append(" null\n");return;}
   for(String key:new String[]{"taskId","displayId","topActivity","baseActivity","realActivity","origActivity","childTaskIds","userId","isVisible","visible","isFocused"}){
    selectionDiagnostics.append(" ").append(key).append("=");
    try{Object v=field(value,key);selectionDiagnostics.append(v instanceof int[]?Arrays.toString((int[])v):String.valueOf(v));}
    catch(Exception unavailable){selectionDiagnostics.append("<unavailable>");}
   }
   try{selectionDiagnostics.append(" activityType=").append(wc(value,"getActivityType")).append(" windowingMode=").append(wc(value,"getWindowingMode"));}catch(Exception unavailable){}
   selectionDiagnostics.append("\n");
  }catch(Exception ignored){}
 }
 private boolean activityAliasMatch(Object focused,Object candidate,ComponentName top){
  try{
   ComponentName other=(ComponentName)field(candidate,"topActivity");
   if(other==null||!pkg.equals(other.getPackageName())||!pkg.equals(top.getPackageName()))return false;
   if(num(focused,"taskId")!=num(candidate,"taskId")||num(focused,"displayId")!=num(candidate,"displayId")||num(focused,"userId")!=num(candidate,"userId"))return false;
   if(wc(focused,"getActivityType")!=1||wc(candidate,"getActivityType")!=1||wc(focused,"getWindowingMode")!=1||wc(candidate,"getWindowingMode")!=1)return false;
   return TaskGuard.activityAlias(top,other,field(focused,"realActivity"),field(candidate,"realActivity"),field(focused,"origActivity"),field(candidate,"origActivity"));
  }catch(Exception unavailable){return false;}
 }
 private void selectionMatch(Object focused,Object candidate,ComponentName top){
  try{if(selectionDiagnostics!=null)selectionDiagnostics.append("MATCH task=").append(num(candidate,"taskId"))
   .append(" topEqual=").append(top.equals(field(candidate,"topActivity")))
   .append(" aliasMatch=").append(activityAliasMatch(focused,candidate,top))
   .append(" exclusive=").append(TaskGuard.exclusive(num(focused,"taskId"),num(candidate,"taskId"),(int[])field(focused,"childTaskIds"))).append("\n");
  }catch(Exception ignored){}
 }
 private void select()throws Exception{
  selectionDiagnostics=new StringBuilder("Task selection diagnostics; existing snapshots only; exact activity or corroborated same-task alias.\n");
  try{selectObserved();selectionDiagnostics.append("RESULT selected task=").append(task).append(" root=").append(root).append("\n");}
  catch(Exception e){selectionDiagnostics.append("RESULT rejected: ").append(e).append("\n");throw e;}
  finally{
   try(FileWriter w=new FileWriter(new File(dir,"task-selection-diagnostics.txt"),true)){w.write(SystemClock.elapsedRealtime()+" package="+pkg+"\n"+selectionDiagnostics);}
   catch(Exception ignored){}finally{selectionDiagnostics=null;}
  }
 }
 private void selectObserved()throws Exception{
  unlocked();api=Class.forName("android.app.IActivityTaskManager");manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
  Object focused=call("getFocusedRootTaskInfo",new Class<?>[]{});
  selectionDiagnostic("FOCUSED_ROOT",focused);
  if(focused==null||num(focused,"displayId")!=0)throw new IllegalStateException("Selected app must be focused on D0");
  ComponentName top=(ComponentName)field(focused,"topActivity");
  if(top==null||!pkg.equals(top.getPackageName()))throw new IllegalStateException("Focused app differs from selection: "+top);
  if(pkg.equals("org.duofold.live")&&!top.getClassName().endsWith(".TaskProbeActivity"))throw new IllegalStateException("Built-in task not launched; refusing Lab controls");
  Object exact=null;
  for(Object t:(List<?>)call("getTasks",new Class<?>[]{int.class,boolean.class,boolean.class,int.class},64,false,false,0)){
   int candidate=num(t,"taskId");
   selectionDiagnostic("CANDIDATE",t);selectionMatch(focused,t,top);
   if((top.equals(field(t,"topActivity"))||activityAliasMatch(focused,t,top))&&TaskGuard.exclusive(num(focused,"taskId"),candidate,(int[])field(focused,"childTaskIds"))){if(exact!=null)throw new IllegalStateException("Ambiguous task selection");exact=t;}
  }
  if(exact==null)throw new IllegalStateException("No exclusive matching task");root=num(focused,"taskId");task=num(exact,"taskId");validateRoot(focused);
  identity0=displayIdentity(0);identity1=displayIdentity(1);if(identity0.equals(identity1))throw new IllegalStateException("Physical identity ambiguous");
  long area0=(long)displayWidth(0)*displayHeight(0),area1=(long)displayWidth(1)*displayHeight(1);if(area0==area1)throw new IllegalStateException("Cannot identify inner/cover geometry");innerIdentity=area0>area1?identity0:identity1;coverIdentity=innerIdentity.equals(identity0)?identity1:identity0;
  log("SELECT package="+pkg+" task="+task+" root="+root+" top="+top+" D0="+identity0+" D1="+identity1);
 }
 private void verifyDestination(Object r,int destination)throws Exception{
  validateRoot(r);
  if(num(r,"displayId")!=destination)throw new IllegalStateException("Selected root not on destination");
  List<?> all=(List<?>)call("getAllRootTaskInfosOnDisplay",new Class<?>[]{int.class},destination);
  int n=all.size();int[] roots=new int[n],displays=new int[n],types=new int[n];boolean[] visible=new boolean[n];
  for(int i=0;i<n;i++){Object a=all.get(i);roots[i]=num(a,"taskId");displays[i]=num(a,"displayId");types[i]=wc(a,"getActivityType");visible[i]=(boolean)field(a,"visible");}
  if(!TaskGuard.destinationVisible(root,destination,(boolean)field(r,"visible"),roots,displays,visible,types))throw new IllegalStateException("Destination task not visibly exclusive");
  Object focused=call("getFocusedRootTaskInfo",new Class<?>[]{});
  String f="focusedRoot="+(focused==null?-1:num(focused,"taskId"))+" focusedDisplay="+(focused==null?-1:num(focused,"displayId"))+" selectedRoot="+root+" destination="+destination;
  if(!f.equals(lastFocus)){log("FOCUS_OBSERVATION "+f+"; cross-display mismatch allowed; destination visibility/identity guards enforced");lastFocus=f;}
 }
 private void promotionTick(long age,boolean ready){
  if(RoutePlan.promotionDue(age,outbound,promotionAttempted)){
   promotionAttempted=true;
   try{
    unlocked();verifyDestination(findRoot(),1);
    if(!ready||!identity0.equals(displayIdentity(0))||!identity1.equals(displayIdentity(1)))throw new IllegalStateException("Original mapping and both-ON gate required");
    if(BridgePlan.enabled(options)&&(bridge==null||!bridge.ready()))throw new IllegalStateException("Live bridge not committed/active; refusing native promotion");
    log("PROMOTE_BEGIN targetPhysical="+identity1+" task="+task+" age="+age+"; task placed at +6s, dwell is not visible-pixel proof");
    promotionPending=true;promotionDeadline=SystemClock.elapsedRealtime()+4000;
    power.promoteDestination();
    log("PROMOTE_CALL_RETURNED; await physical mapping, task placement and navigation");
   }catch(Exception e){log("PROMOTE_REJECTED_OR_AMBIGUOUS "+cause(e));}
  }
  if(promotionPending&&!promotionVerified){
   try{
    String now0=displayIdentity(0),now1=displayIdentity(1);
    if(!identity1.equals(now0)||!identity0.equals(now1))throw new IllegalStateException("Awaiting swapped physical mapping");
    Object r=findRoot();validateRoot(r);unlocked();
    log("PROMOTE_MAPPING_OBSERVED D0="+now0+" D1="+now1+" taskDisplay="+num(r,"displayId"));
    // Android may leave the root on logical D1, which now points at the outgoing panel.
    // Retarget only the same exclusive app root; never move Home or an unrelated root.
    if(num(r,"displayId")!=0){
     if(promotionRetargetAttempted)throw new IllegalStateException("Retarget not yet observed; will not duplicate Binder request");
     promotionRetargetAttempted=true;
     log("PROMOTE_RETARGET_REQUEST same root="+root+" to D0 targetPhysical="+now0);
     call("moveRootTaskToDisplayOnTopOrBottom",new Class<?>[]{int.class,int.class,boolean.class},root,0,true);
     r=findRoot();validateRoot(r);
    }
    if(num(r,"displayId")!=0||!identity1.equals(displayIdentity(0)))throw new IllegalStateException("Task/physical destination not verified");
    if(HandoffPlan.fast(options)){
     verifyDestination(r,0);String g=geometry(r);
     if(!g.equals(geometryCandidate)){geometryCandidate=g;geometrySince=age;log("PROMOTE_GEOMETRY_SETTLING "+g);}
     if(!HandoffPlan.stable(age,geometrySince))return;
    }
    pendingReturn=false;promotionVerified=true;promotionPending=false;
    if(BridgePlan.enabled(options))bridgeVerifiedAge=age;
    log("PROMOTE_VERIFIED task="+task+" display=0 physical="+identity1+" configuration="+field(r,"configuration")+"; software placement, not photon visibility");
    try{call("setFocusedTask",new Class<?>[]{int.class},task);}catch(Exception e){log("PROMOTE_FOCUS rejected "+cause(e));}
    NavigationProbe.sample(dir,"after primary promotion");
   }catch(Exception e){geometryCandidate="";geometrySince=-1;if(SystemClock.elapsedRealtime()>=promotionDeadline){promotionPending=false;log("PROMOTE_TIMEOUT "+cause(e)+"; no further promotion attempts");NavigationProbe.sample(dir,"promotion timeout");}}
  }
 }
 private void move(int destination)throws Exception{
  unlocked();Object r=findRoot();validateRoot(r);
  if(!identity0.equals(displayIdentity(0))||!identity1.equals(displayIdentity(1)))throw new IllegalStateException("Mapping/power changed; refusing move");
  log("MOVE_REQUEST task="+task+" root="+root+" from="+num(r,"displayId")+" to="+destination);
  if(destination==1)pendingReturn=true; // Before Binder call: failures may be ambiguous.
  call("moveRootTaskToDisplayOnTopOrBottom",new Class<?>[]{int.class,int.class,boolean.class},root,destination,true);
  log("MOVE_CALL_RETURNED destination="+destination+"; placement and pixels unverified");
  Object after=findRoot();validateRoot(after);log("PLACEMENT task="+task+" display="+num(after,"displayId")+" configuration="+field(after,"configuration"));
  if(num(after,"displayId")!=destination)throw new IllegalStateException("Destination not observed after move");
  if(destination==0)pendingReturn=false;
  try{call("setFocusedTask",new Class<?>[]{int.class},task);log("FOCUS requested task="+task);}catch(Exception e){log("FOCUS rejected "+cause(e));}
 }
 synchronized void tick(long age,boolean ready){
  if(closing)return;
  if(outbound&&!RoutePlan.promotes(options)){
   int wanted=powerAborted?-1:RoutePlan.offPanel(age,options,outbound,returned);
   if(wanted>=0)try{
    unlocked();Object r=findRoot();
    int destination=1-wanted;verifyDestination(r,destination);
    if(num(r,"displayId")!=destination||!identity0.equals(displayIdentity(0,false))||!identity1.equals(displayIdentity(1,false)))throw new IllegalStateException("Task placement or physical mapping changed");
    displayIdentity(destination); // Never intentionally switch off the only observed lit destination.
   }catch(Exception e){log("PANEL TEST ABORTED "+cause(e));powerAborted=true;wanted=-1;}
   if(wanted!=off){log("PANEL_PLAN off="+wanted+" age="+age);power.panelOff(wanted);off=wanted;NavigationProbe.sample(dir,"panel request off="+off);}
   if((options==1||options==3||options==7)&&((!focusOut&&age>=7000&&age<14000)||(!focusBack&&returned&&age>=17000))){
    if(returned)focusBack=true;else focusOut=true;
    try{unlocked();Object r=findRoot();validateRoot(r);call("setFocusedTask",new Class<?>[]{int.class},task);log("FOCUS_REASSERT task="+task+" display="+num(r,"displayId"));}catch(Exception e){log("FOCUS_REASSERT rejected "+cause(e));}
    NavigationProbe.sample(dir,"after focus reassertion");
   }
  }

  if(!attempted&&age>=6000){attempted=true;try{if(!ready)throw new IllegalStateException("Fixed-state both-ON gate not ready");select();NavigationProbe.sample(dir,"before move");move(1);outbound=true;NavigationProbe.sample(dir,"after outbound");}catch(Exception e){log("OUTBOUND REJECTED "+cause(e));}}
  if(attempted&&task>=0&&age-lastSample>=1000){lastSample=age;try{Object r=findRoot();log("SAMPLE task="+task+" root="+root+" display="+(r==null?-1:num(r,"displayId"))+" top="+(r==null?null:field(r,"topActivity"))+" visible="+(r==null?null:field(r,"visible"))+" configuration="+(r==null?null:field(r,"configuration")));}catch(Exception e){log("SAMPLE unavailable "+cause(e));}}
  if(BridgePlan.enabled(options)){
   if(BridgePlan.prepare(age,outbound,bridgeAttempted)){
    bridgeAttempted=true;
    try{unlocked();verifyDestination(findRoot(),1);
     if(!ready||!identity0.equals(displayIdentity(0))||!identity1.equals(displayIdentity(1)))throw new IllegalStateException("Bridge requires original mapping and both ON");
     bridge=new ShieldBridge(dir,HandoffPlan.fast(options));bridge.arm();
    }catch(Exception e){log("BRIDGE REJECTED "+cause(e));if(bridge!=null)bridge.close();}
   }
   if(bridge!=null){
    if(HandoffPlan.fast(options))bridge.followPhysicalStacks();
    if(!HandoffPlan.fast(options)||age-lastBridgeGuard>=250){lastBridgeGuard=age;try{unlocked();Object r=findRoot();validateRoot(r);}catch(Exception e){log("BRIDGE task/keyguard guard failed "+cause(e));bridge.close();}}
    if(HandoffPlan.fast(options)&&promotionVerified&&!bridge.isClosed()){
     try{Object r=findRoot();verifyDestination(r,0);String g=geometry(r);
      if(!identity1.equals(displayIdentity(0)))throw new IllegalStateException("Promoted physical primary changed");
      if(!g.equals(geometryCandidate)){geometryCandidate=g;geometrySince=age;bridgeVerifiedAge=-1;log("REVEAL_GEOMETRY_CHANGED "+g);}
      if(bridgeVerifiedAge<0&&HandoffPlan.stable(age,geometrySince))bridgeVerifiedAge=age;
     }catch(Exception e){geometryCandidate="";geometrySince=-1;bridgeVerifiedAge=-1;}
    }
    if(BridgePlan.remove(age,bridgeVerifiedAge)){
     if(BridgePlan.stagedReveal(options))bridge.reveal();else bridge.close();
    }
   }
  }
  if(RoutePlan.promotes(options))promotionTick(age,ready);
  if(BridgePlan.recoveryDue(options,age,promotionVerified,bridge!=null&&bridge.isClosed(),contentRecoveryAttempted)){
   contentRecoveryAttempted=true;
   try{unlocked();Object r=findRoot();verifyDestination(r,0);
    if(!identity1.equals(displayIdentity(0)))throw new IllegalStateException("Promoted physical destination changed");
    log("CONTENT_RECOVERY_BEGIN age="+age+" root="+root+" task="+task+"; focus only, no injected touch");
    call("setFocusedRootTask",new Class<?>[]{int.class},root);
    call("setFocusedTask",new Class<?>[]{int.class},task);
    log("CONTENT_RECOVERY_RETURNED; not visible-pixel proof");
    verifyDestination(findRoot(),0);
   }catch(Exception e){log("CONTENT_RECOVERY_REJECTED_OR_AMBIGUOUS "+cause(e));}
   NavigationProbe.sample(dir,"after delayed content focus request");
  }
  if(options==14)protectedReturnTick(age);
  if(!RoutePlan.promotes(options)&&attempted&&!returnAttempted&&age>=16000){returnAttempted=true;returnTask();}
 }
 private String nativeExpectedPrimary()throws Exception{
  Object sm=PowerExperiment.service("device_state","android.hardware.devicestate.IDeviceStateManager");
  Object info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(sm);
  Object base=field(info,"baseState"),current=field(info,"currentState");
  int b=(int)base.getClass().getMethod("getIdentifier").invoke(base),c=(int)current.getClass().getMethod("getIdentifier").invoke(current);
  if(b!=c)throw new IllegalStateException("Awaiting base state");
  if(b==3)return innerIdentity;
  if(b==0)return coverIdentity;
  throw new IllegalStateException("Native physical target unknown for base="+b);
 }
 private void protectedReturnTick(long age){
  if(HandoffPlan.prepareReturn(options,age,promotionVerified,returnBridgeAttempted)){
   returnBridgeAttempted=true;
   try{
    unlocked();verifyDestination(findRoot(),0);
    if(bridge==null||!bridge.isClosed()||!identity1.equals(displayIdentity(0))||!identity0.equals(displayIdentity(1)))throw new IllegalStateException("Forward handoff/mirror cleanup not intact");
    log("RETURN_BRIDGE_PREPARE age="+age+"; fresh mirrors for native release");
    returnBridge=new ShieldBridge(dir,true);returnBridge.arm();
   }catch(Exception e){log("RETURN_BRIDGE_REJECTED "+cause(e));if(returnBridge!=null)returnBridge.close();}
  }
  if(returnBridge==null||returnBridge.isClosed())return;
  returnBridge.followPhysicalStacks();
  try{unlocked();validateRoot(findRoot());}catch(Exception e){log("RETURN_GUARD_REJECTED "+cause(e));returnBridge.close();return;}
  if(HandoffPlan.releaseReturn(options,age,nativeReleaseAttempted,returnBridge.ready())){
   nativeReleaseAttempted=true;
   try{
    verifyDestination(findRoot(),0);displayIdentity(0);displayIdentity(1);
    log("NATIVE_RELEASE_BEGIN age="+age+"; follows Android base state, no assumed physical destination");
    power.releaseNativeUnderBridge();
   }catch(Exception e){log("NATIVE_RELEASE_REJECTED_OR_AMBIGUOUS "+cause(e));}
  }
  if(nativeReleaseAttempted&&age<28000){
   try{
    String expected=nativeExpectedPrimary();
    String target=displayIdentity(0);
    if(!target.equals(expected))throw new IllegalStateException("Awaiting native physical primary="+expected+" observed="+target);
    if(!target.equals(identity0)&&!target.equals(identity1))throw new IllegalStateException("Unknown physical primary");
    Object r=findRoot();validateRoot(r);
    if(num(r,"displayId")!=0){
     if(nativeRetargetAttempted)throw new IllegalStateException("Awaiting one-shot native retarget");
     nativeRetargetAttempted=true;log("NATIVE_RELEASE_RETARGET root="+root+" D0 physical="+target);
     call("moveRootTaskToDisplayOnTopOrBottom",new Class<?>[]{int.class,int.class,boolean.class},root,0,true);
     r=findRoot();validateRoot(r);
    }
    verifyDestination(r,0);
    if(!target.equals(displayIdentity(0)))throw new IllegalStateException("Primary changed during verification");
    String candidate=geometry(r);
    if(!candidate.equals(nativeCandidate)){nativeReleaseVerified=false;nativeVerifiedAge=-1;nativeCandidate=candidate;nativeCandidateSince=age;log("NATIVE_RELEASE_SETTLING physical="+target+" age="+age);}
    if(!HandoffPlan.stable(age,nativeCandidateSince))return;
    if(!nativeReleaseVerified){nativeReleaseVerified=true;nativeVerifiedAge=age;
    log("NATIVE_RELEASE_VERIFIED age="+age+" physical="+target+" task="+task+"; geometry and mapping only, not fresh pixels");}
   }catch(Exception e){if(nativeReleaseVerified)log("NATIVE_RELEASE_VERIFICATION_REVOKED "+cause(e));nativeReleaseVerified=false;nativeVerifiedAge=-1;nativeCandidate="";nativeCandidateSince=-1;lastNativeWait=cause(e).toString();}
  }
  if(HandoffPlan.revealReturn(age,nativeVerifiedAge)){
   if(!nativeReleaseVerified){log("NATIVE_RELEASE_UNVERIFIED at reveal deadline; reason="+lastNativeWait+"; removing protection");returnBridge.close();}
   else returnBridge.reveal();
  }
 }
 synchronized void restore(){if(returnBridge!=null)returnBridge.close();if(bridge!=null)bridge.close();if(closing)return;closing=true;if(off>=0||pendingReturn)power.bothPanelsForReturn();off=-1;
  if(pendingReturn){long deadline=SystemClock.elapsedRealtime()+1200;while(SystemClock.elapsedRealtime()<deadline){try{displayIdentity(0);displayIdentity(1);break;}catch(Exception e){try{Thread.sleep(40);}catch(InterruptedException stop){Thread.interrupted();}}}}
  if(RoutePlan.promotes(options)&&promotionAttempted){
   try{unlocked();Object r=findRoot();validateRoot(r);displayIdentity(0);if(!nativeReleaseVerified)displayIdentity(1);
    if(num(r,"displayId")!=0){log("PROMOTE_CLEANUP_RETURN current logical D0 before native release");call("moveRootTaskToDisplayOnTopOrBottom",new Class<?>[]{int.class,int.class,boolean.class},root,0,true);r=findRoot();validateRoot(r);}
    if(num(r,"displayId")==0){pendingReturn=false;log("PROMOTE_CLEANUP task verified on current D0; native release may remap again");}
   }catch(Exception e){log("PROMOTE_CLEANUP NOT VERIFIED "+cause(e));}
  }else returnTask();
 }
 boolean bridgeCleaned(){return (bridge==null||bridge.isClosed())&&(returnBridge==null||returnBridge.isClosed());}
 private void returnTask(){if(!pendingReturn)return;try{
  Object r=findRoot();validateRoot(r);if(num(r,"displayId")==0){pendingReturn=false;returned=true;log("RETURN already D0");return;}
  move(0);returned=true;log("RETURN verified D0");NavigationProbe.sample(dir,"after return");
 }catch(Exception e){log("RETURN NOT VERIFIED "+cause(e)+"; task may remain on D1 until normal mapping resumes");}}
 private static Throwable cause(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
}
