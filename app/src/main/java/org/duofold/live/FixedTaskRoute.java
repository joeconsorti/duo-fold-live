package org.duofold.live;
import android.app.ActivityOptions;
import android.os.Bundle;
import java.util.*;
/** Owns only the exact fullscreen app selected before concurrent activation. */
final class FixedTaskRoute {
 private final Object manager;private final Class<?> api;
 private int task=-1;private boolean requested;private String component="";
 FixedTaskRoute()throws Exception{api=Class.forName("android.app.IActivityTaskManager");manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);}
 FixedTaskRoute(Object manager,Class<?> api){this.manager=manager;this.api=api;}
 private int number(Object o,String name)throws Exception{return o.getClass().getField(name).getInt(o);}
 private Object field(Object o,String name)throws Exception{return o.getClass().getField(name).get(o);}
 private List<?> tasks(int display)throws Exception{return (List<?>)api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(manager,64,false,false,display);}
 private boolean contains(Object root,int id)throws Exception{if(number(root,"taskId")==id)return true;int[] children=(int[])field(root,"childTaskIds");if(children!=null)for(int child:children)if(child==id)return true;return false;}
 String prepare()throws Exception{
  if(requested)throw new IllegalStateException("Previous exact task still needs return");task=-1;
  Object root=api.getMethod("getFocusedRootTaskInfo").invoke(manager);
  if(root==null||number(root,"displayId")!=0)return "SKIP: outgoing display does not own focus";
  Object window=field(field(root,"configuration"),"windowConfiguration");
  int type=(int)window.getClass().getMethod("getActivityType").invoke(window);
  int mode=(int)window.getClass().getMethod("getWindowingMode").invoke(window);
  if(type!=1||mode!=1)return "SKIP: use a fullscreen app; Home, Recents and multi-window are not routed";
  for(Object candidate:tasks(0))if(contains(root,number(candidate,"taskId"))&&Objects.equals(field(root,"topActivity"),field(candidate,"topActivity"))){task=number(candidate,"taskId");component=String.valueOf(field(candidate,"topActivity"));return "SELECT task="+task+" component="+component;}
  return "SKIP: no exact focused app task";
 }
 int selectedTask(){return task;}
 boolean selected(){return task>=0;}
 boolean pending(){return requested;}
 String move()throws Exception{
  if(task<0)throw new IllegalStateException("No selected app");
  boolean present=false;for(Object item:tasks(0))if(number(item,"taskId")==task&&component.equals(String.valueOf(field(item,"topActivity"))))present=true;
  if(!present)throw new IllegalStateException("Selected app changed before route");
  requested=true;
  int result=(int)api.getMethod("startActivityFromRecents",int.class,Bundle.class).invoke(manager,task,ActivityOptions.makeBasic().setLaunchDisplayId(1).toBundle());
  if(result<0)throw new IllegalStateException("App route rejected: "+result);
  return "ROUTE requested task="+task+" to D1; result="+result;
 }
 static final class Observation {
  final boolean placed,focused;final int focusedDisplay,focusedTask;
  Observation(boolean placed,boolean focused,int display,int task){this.placed=placed;this.focused=focused;focusedDisplay=display;focusedTask=task;}
  boolean verified(){return placed&&focused;}
  String summary(){return "D1 placement="+placed+"; selected app focused on D1="+focused+"; focused display="+focusedDisplay+" root="+focusedTask;}
 }
 Observation observe()throws Exception{
  boolean placed=false;for(Object item:tasks(1))if(number(item,"taskId")==task)placed=true;
  Object root=api.getMethod("getFocusedRootTaskInfo").invoke(manager);
  int display=root==null?-1:number(root,"displayId"),rootTask=root==null?-1:number(root,"taskId");
  return new Observation(placed,root!=null&&display==1&&contains(root,task),display,rootTask);
 }
 boolean placedAndFocused()throws Exception{return observe().verified();}
 String primaryFocusSummary()throws Exception{
  Object root=api.getMethod("getFocusedRootTaskInfo").invoke(manager);
  return "selected task="+task+" focused on D0="+(root!=null&&number(root,"displayId")==0&&contains(root,task));
 }
 String restore(boolean unlocked)throws Exception{
  if(!requested)return "No task return pending";
  boolean onSecondary=false;for(Object item:tasks(1))if(number(item,"taskId")==task)onSecondary=true;
  if(!onSecondary){requested=false;return "Exact task no longer on D1; no return needed";}
  if(!unlocked)return "Exact task return deferred until unlocked";
  int result=(int)api.getMethod("startActivityFromRecents",int.class,Bundle.class).invoke(manager,task,ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
  if(result<0)throw new IllegalStateException("Exact task return rejected: "+result);
  for(Object item:tasks(1))if(number(item,"taskId")==task)return "Exact task return requested; waiting for placement";
  requested=false;return "RETURN task="+task+" to D0";
 }
}
