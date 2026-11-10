package org.duofold.live;

import java.lang.reflect.*;
import java.util.*;

/** Read-only identity of the visible native workspace. Never reparents or focuses a task. */
public final class MultiWindowTasks {
 private MultiWindowTasks(){}
 private static Object field(Object o,String key)throws Exception{return o.getClass().getField(key).get(o);}
 private static int number(Object o,String key)throws Exception{return ((Number)field(o,key)).intValue();}
 private static int window(Object o,String key)throws Exception{Object w=field(field(o,"configuration"),"windowConfiguration");return (int)w.getClass().getMethod(key).invoke(w);}
 private static boolean visible(Object o)throws Exception{
  try{return (boolean)field(o,"isVisible");}catch(NoSuchFieldException e){return (boolean)field(o,"visible");}
 }
 private static int parent(Object o){try{return number(o,"parentTaskId");}catch(Exception e){return -1;}}
 public static final class Snapshot {
  public final int[] tasks;public final boolean multitask;public final String signature;
  private final Map<Integer,String> identities;
  private final int right,bottom;
  Snapshot(TreeMap<Integer,String> ids,boolean multi,String geometry,int right,int bottom){
   this.right=right;this.bottom=bottom;
   tasks=ids.keySet().stream().mapToInt(Integer::intValue).toArray();identities=Collections.unmodifiableMap(ids);multitask=multi;signature=ids+geometry;
  }
  public boolean fits(int width,int height){return right<=width&&bottom<=height&&width>0&&height>0;}
  public boolean containsOnly(Snapshot original){
   if(tasks.length==0)return false;
   for(int id:tasks)if(!Objects.equals(identities.get(id),original.identities.get(id)))return false;
   return true;
  }
 }
 public static Snapshot inspect()throws Exception{
  Class<?> api=Class.forName("android.app.IActivityTaskManager");
  Object manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
  return inspect(manager,api);
 }
 public static Snapshot inspect(Object manager,Class<?> api)throws Exception{
  Object focused=api.getMethod("getFocusedRootTaskInfo").invoke(manager);
  if(focused==null||number(focused,"displayId")!=0)throw new IllegalStateException("Workspace not focused on D0");
  int focusedType=window(focused,"getActivityType");
  if(focusedType!=0&&focusedType!=1)throw new IllegalStateException("Workspace focus is not an app group");
  List<?> all=(List<?>)api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(manager,64,false,false,0);
  Set<Integer> parents=new HashSet<>();for(Object t:all){int id=parent(t);if(id>=0)parents.add(id);}
  TreeMap<Integer,String> ids=new TreeMap<>();TreeMap<Integer,String> geometry=new TreeMap<>();boolean multi=false,focusFound=false;
  Object top=field(focused,"topActivity");int root=number(focused,"taskId"),right=0,bottom=0;
  int[] children;try{children=(int[])field(focused,"childTaskIds");}catch(NoSuchFieldException e){children=null;}
  for(Object t:all){
   int id=number(t,"taskId");if(number(t,"displayId")!=0||parents.contains(id)||!visible(t))continue;
   int type=window(t,"getActivityType"),mode=window(t,"getWindowingMode");
   // Home behind floating apps is not a member of a split-screen app group.
   if(type==2)continue;
   if(type!=1||!(mode==1||mode==3||mode==4||mode==5||mode==6))throw new IllegalStateException("Unsupported visible workspace task");
   Object activity=field(t,"topActivity");if(activity==null||id<0)throw new IllegalStateException("Workspace member identity missing");
   String identity=number(t,"userId")+":"+activity;
   if(ids.put(id,identity)!=null)throw new IllegalStateException("Duplicate workspace member");
   Object config=field(t,"configuration"),wc=field(config,"windowConfiguration");
   Object bounds=wc.getClass().getMethod("getBounds").invoke(wc);
   if((int)bounds.getClass().getMethod("width").invoke(bounds)<=0||(int)bounds.getClass().getMethod("height").invoke(bounds)<=0)throw new IllegalStateException("Workspace layout pending");
   int left=number(bounds,"left"),y=number(bounds,"top");
   if(left<0||y<0)throw new IllegalStateException("Workspace pane outside display");
   right=Math.max(right,number(bounds,"right"));bottom=Math.max(bottom,number(bounds,"bottom"));
   geometry.put(id,mode+":"+bounds+":"+config);
   multi|=mode!=1;
   boolean child=id==root||parent(t)==root;if(children!=null)for(int c:children)child|=c==id;
   focusFound|=number(t,"userId")==number(focused,"userId")&&(child||Objects.equals(top,activity));
  }
  if(ids.isEmpty()||ids.size()>8||!focusFound)throw new IllegalStateException("Visible workspace membership unavailable");
  return new Snapshot(ids,multi||ids.size()>1,geometry.toString(),right,bottom);
 }
 public static Snapshot requireTasks(int[] tasks)throws Exception{
  if(tasks==null||tasks.length==0)throw new IllegalStateException("Workspace task selection missing");
  Snapshot s=inspect();int[] expected=tasks.clone();Arrays.sort(expected);
  if(!Arrays.equals(s.tasks,expected))throw new IllegalStateException("Workspace membership changed");
  return s;
 }
}
