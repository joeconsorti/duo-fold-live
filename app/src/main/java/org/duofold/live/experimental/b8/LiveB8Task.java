package org.duofold.live.experimental.b8;
import android.content.ComponentName;
import android.os.SystemClock;
import org.duofold.live.MultiWindowTasks;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Moves only exclusive standard app roots. Home is observed in place; never moved or focused by Duo. */
final class LiveB8Task {
 private final File dir;private final String pkg;
 
 private boolean sourceOnly,home;private int homeUser=-1;
 boolean isHome(){return home;}
 private MultiWindowTasks.Snapshot workspaceOriginal,workspaceCurrent;
 private final MultiWindowTasks.Snapshot rememberedWorkspace;
 boolean isWorkspace(){return workspaceOriginal!=null;}
 int[] workspaceTasks(){return workspaceCurrent==null?new int[0]:workspaceCurrent.tasks.clone();}
 private Object manager;private Class<?> api;
 private int root=-1,task=-1;private boolean pendingReturn;
 private String identity0,identity1,innerIdentity,coverIdentity;
 private String lastFocus="";
 LiveB8Task(File d,String p){this(d,p,null);}
 LiveB8Task(File d,String p,LiveB8Task remembered){dir=d;pkg=p;rememberedWorkspace=remembered==null?null:remembered.workspaceOriginal;}
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
 private void verifyLayout(Object config)throws Exception{
  Object window=field(config,"windowConfiguration");
  Object bounds=window.getClass().getMethod("getMaxBounds").invoke(window);
  if(bounds==null)throw new IllegalStateException("Task bounds unavailable");
  int w=(int)bounds.getClass().getMethod("width").invoke(bounds),h=(int)bounds.getClass().getMethod("height").invoke(bounds);
  if(!TaskLayoutPolicy.matches(w,h,num(config,"screenWidthDp"),num(config,"screenHeightDp"),num(config,"densityDpi")))
   throw new IllegalStateException("Task dp layout pending: "+config);
 }
 private String geometry(Object r)throws Exception{
  Object config=field(r,"configuration"),window=field(config,"windowConfiguration");verifyLayout(config);
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
  if(r==null||wc(r,"getActivityType")!=(home?2:1)||wc(r,"getWindowingMode")!=1)throw new IllegalStateException("Root missing or not standard fullscreen");
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
  // Observe the complete visible group before applying exclusive-fullscreen guards.
  // Organizer roots can be fullscreen/undefined even while their leaf tasks are split.
  int focusedType=wc(focused,"getActivityType");
  if(focusedType==0||focusedType==1){
   MultiWindowTasks.Snapshot visible=MultiWindowTasks.inspect(manager,api);
   if(visible.multitask||rememberedWorkspace!=null&&visible.containsOnly(rememberedWorkspace)){workspaceOriginal=rememberedWorkspace!=null&&visible.containsOnly(rememberedWorkspace)?rememberedWorkspace:visible;workspaceCurrent=visible;task=visible.tasks[0];root=num(focused,"taskId");identity0=displayIdentity(0);log("WORKSPACE_SELECT tasks="+Arrays.toString(visible.tasks)+"; native layout retained, no task moves");return;}
  }
  ComponentName top=(ComponentName)field(focused,"topActivity");
  if(top==null||!pkg.equals(top.getPackageName()))throw new IllegalStateException("Focused app differs from selection: "+top);
  int type=wc(focused,"getActivityType");
  if(type!=1&&type!=2)throw new IllegalStateException("Only app or Home roots supported");
  home=type==2;
  if(home){
   homeUser=num(focused,"userId");root=num(focused,"taskId");
   int[] children=(int[])field(focused,"childTaskIds");
   task=children==null||children.length==0?root:children.length==1?children[0]:-1;
   if(task<0)throw new IllegalStateException("Home root is not exclusive");
   verifyDestination(focused,0);identity0=displayIdentity(0);
   log("HOME_SELECT task="+task+" root="+root+" user="+homeUser+"; actual focused Home, no recent-task fallback");return;
  }
  Object exact=null;
  for(Object t:(List<?>)call("getTasks",new Class<?>[]{int.class,boolean.class,boolean.class,int.class},64,false,false,0)){
   int candidate=num(t,"taskId");
   selectionDiagnostic("CANDIDATE",t);selectionMatch(focused,t,top);
   if((top.equals(field(t,"topActivity"))||activityAliasMatch(focused,t,top))&&TaskGuard.exclusive(num(focused,"taskId"),candidate,(int[])field(focused,"childTaskIds"))){if(exact!=null)throw new IllegalStateException("Ambiguous task selection");exact=t;}
  }
  if(exact==null)throw new IllegalStateException("No exclusive matching task");root=num(focused,"taskId");task=num(exact,"taskId");validateRoot(focused);
  identity0=displayIdentity(0);if(sourceOnly)return;identity1=displayIdentity(1);if(identity0.equals(identity1))throw new IllegalStateException("Physical identity ambiguous");
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
 private void move(int destination)throws Exception{
  if(home||isWorkspace())throw new IllegalStateException("Native Home/workspace roots are never moved");
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

 static String focusedPackage()throws Exception{
  Class<?> a=Class.forName("android.app.IActivityTaskManager");Object m=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
  Object r=a.getMethod("getFocusedRootTaskInfo").invoke(m);
  if(r==null)throw new IllegalStateException("No focused root task");
  if(num(r,"displayId")!=0||(wc(r,"getActivityType")!=0&&wc(r,"getActivityType")!=1&&wc(r,"getActivityType")!=2))throw new IllegalStateException("Not a primary fullscreen app: display="+num(r,"displayId")+" activityType="+wc(r,"getActivityType")+" windowingMode="+wc(r,"getWindowingMode"));
  ComponentName top=(ComponentName)field(r,"topActivity");
  if(top==null)return wc(r,"getActivityType")==0?"@workspace":null;
  return top.getPackageName();
 }
 String configurationReport(){
  try{
   Object r=findRoot();if(r==null)return "task missing";
   int id=num(r,"displayId");Object d=displayInfo(id),config=field(r,"configuration");
   return "task="+task+" display="+id+" physical="+field(d,"uniqueId")
    +" displayDensityDpi="+num(d,"logicalDensityDpi")+" taskDensityDpi="+num(config,"densityDpi")
    +" widthDp="+num(config,"screenWidthDp")+" heightDp="+num(config,"screenHeightDp")+" fontScale="+field(config,"fontScale");
  }catch(Exception unavailable){return "density unavailable: "+cause(unavailable).getClass().getSimpleName();}
 }
 int taskId(){return task;}
 void moveSelected()throws Exception{move(1);}
 void selectSourceOnly()throws Exception{sourceOnly=true;try{select();}finally{sourceOnly=false;}}
 void selectOnly()throws Exception{select();}
 void selectAndMove()throws Exception{select();move(1);}
 String destinationGeometry()throws Exception{
  Object r=findRoot();verifyDestination(r,1);
  Object c=field(r,"configuration"),w=field(c,"windowConfiguration");verifyLayout(c);Object b=w.getClass().getMethod("getMaxBounds").invoke(w);
  int width=(int)b.getClass().getMethod("width").invoke(b),height=(int)b.getClass().getMethod("height").invoke(b);
  if(width!=displayWidth(1)||height!=displayHeight(1))throw new IllegalStateException("Destination geometry settling");
  return identity1+":"+width+"x"+height+":"+c;
 }
 private boolean retargeted;
 String verifyPrimary(String expected)throws Exception{
  if(isWorkspace())return verifyWorkspacePrimary(expected);
  if(home)return verifyHomePrimary(expected);
  unlocked();if(!expected.equals(displayIdentity(0)))throw new IllegalStateException("Physical primary pending");
  Object r=findRoot();validateRoot(r);
  if(num(r,"displayId")!=0){
   if(retargeted)throw new IllegalStateException("Retarget pending");
   retargeted=true;call("moveRootTaskToDisplayOnTopOrBottom",new Class<?>[]{int.class,int.class,boolean.class},root,0,true);r=findRoot();validateRoot(r);
  }
  verifyDestination(r,0);pendingReturn=false;return geometry(r);
 }
 String verifyNativePrimary(String expected)throws Exception{
  if(isWorkspace())return verifyWorkspacePrimary(expected);
  if(home)return verifyHomePrimary(expected);
  unlocked();if(!expected.equals(displayIdentity(0)))throw new IllegalStateException("Native physical primary pending");
  Object r=findRoot();verifyDestination(r,0);return geometry(r);
 }
 void focus(){if(home||isWorkspace())return;try{call("setFocusedTask",new Class<?>[]{int.class},task);}catch(Exception e){log("LIVE_FOCUS "+e);}}
 void restore(){if(task<0||home||isWorkspace())return;try{unlocked();Object r=findRoot();validateRoot(r);if(num(r,"displayId")!=0)call("moveRootTaskToDisplayOnTopOrBottom",new Class<?>[]{int.class,int.class,boolean.class},root,0,true);}catch(Exception e){log("LIVE_RESTORE_UNVERIFIED "+e);}}
 // Android may create a different Home task on the new primary. Accept only
 // the focused fullscreen HOME of the same launcher and user; no task mutation.
 String verifyHomePrimary(String expected)throws Exception{
  unlocked();if(!expected.equals(displayIdentity(0)))throw new IllegalStateException("Home physical primary pending");
  Object focused=call("getFocusedRootTaskInfo",new Class<?>[]{});
  if(focused==null||num(focused,"displayId")!=0||wc(focused,"getActivityType")!=2||wc(focused,"getWindowingMode")!=1||num(focused,"userId")!=homeUser)
   throw new IllegalStateException("Focused primary Home pending");
  ComponentName top=(ComponentName)field(focused,"topActivity");
  if(top==null||!pkg.equals(top.getPackageName()))throw new IllegalStateException("Home launcher changed");
  int rootId=num(focused,"taskId");int[] children=(int[])field(focused,"childTaskIds");
  int selected=children==null||children.length==0?rootId:children.length==1?children[0]:-1;
  if(selected<0)throw new IllegalStateException("Home root is not exclusive");
  root=rootId;task=selected;verifyDestination(focused,0);return geometry(focused);
 }
 String verifyWorkspacePrimary(String expected)throws Exception{
  unlocked();if(!expected.equals(displayIdentity(0)))throw new IllegalStateException("Workspace physical primary pending");
  MultiWindowTasks.Snapshot observed=MultiWindowTasks.inspect(manager,api);
  // Native cover policy may collapse split screen to one of its members. Do not
  // replace that policy or accept a different app/user as part of this animation.
  if(!observed.containsOnly(workspaceOriginal))throw new IllegalStateException("Workspace app membership changed");
  if(!observed.fits(displayWidth(0),displayHeight(0)))throw new IllegalStateException("Workspace destination bounds pending");
  workspaceCurrent=observed;task=observed.tasks[0];
  return expected+":"+displayWidth(0)+"x"+displayHeight(0)+":"+observed.signature;
 }
 private static Throwable cause(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
}
