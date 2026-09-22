package org.duofold.live;
// Adapted from bunkaich/Folduo, MIT (2026 bunkaich); see assets/licenses/FOLDUO-MIT.txt.

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.*;

/** Move app and home tasks without swapping physical/logical display IDs. */
final class TaskDisplayRouter {
    private final Object manager;private final Class<?> api;
    private int lastDestination;
    private final Set<Integer> movedTasks=new LinkedHashSet<>();
    TaskDisplayRouter()throws Exception{
        manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
        api=Class.forName("android.app.IActivityTaskManager");
    }
    TaskDisplayRouter(Object manager,Class<?> api){this.manager=manager;this.api=api;}
    private int number(Object info,String field)throws Exception{return info.getClass().getField(field).getInt(info);}
    private int activityType(Object info)throws Exception{
        Object configuration=info.getClass().getField("configuration").get(info);
        Object window=configuration.getClass().getField("windowConfiguration").get(configuration);
        return (int)window.getClass().getMethod("getActivityType").invoke(window);
    }
    private boolean standard(Object info)throws Exception{return activityType(info)==1;}
    private List<?> roots(int display)throws Exception{return (List<?>)api.getMethod("getAllRootTaskInfosOnDisplay",int.class).invoke(manager,display);}
    private Object home(int display)throws Exception{
        for(Object root:roots(display))if(activityType(root)==2&&root.getClass().getField("topActivity").get(root)!=null)return root;
        return null;
    }
    private void moveHome(Object root,int destination,boolean focus)throws Exception{
        // Samsung permits one HOME root per display. Use an already-created HOME,
        // never reparent a second HOME root into it.
        Object existing=home(destination);
        if(existing!=null)root=existing;
        int id=number(root,"taskId");
        if(number(root,"displayId")!=destination)api.getMethod("moveRootTaskToDisplayOnTopOrBottom",int.class,int.class,boolean.class).invoke(manager,id,destination,focus);
        if(focus)api.getMethod("setFocusedRootTask",int.class).invoke(manager,id);
        lastDestination=destination;
    }
    synchronized void showHome(int display)throws Exception{
        showHome(display,null);
    }
    synchronized void showHome(int display,ComponentName preferred)throws Exception{
        if(preferred!=null){
            Object task=homeTask(display,preferred);
            if(task==null)task=homeTask(display==0?1:0,preferred);
            if(task!=null){moveHomeTask(task,display);return;}
        }
        Object root=home(display);if(root==null)root=home(display==0?1:0);
        if(root==null)throw new IllegalStateException("@folduo/err_home_missing");
        moveHome(root,display,true);
    }
    private Object homeTask(int display,ComponentName component)throws Exception{
        List<?> tasks=(List<?>)api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(manager,64,false,false,display);
        for(Object task:tasks)if(activityType(task)==2&&matchesLaunch(task,component))return task;
        return null;
    }
    private void moveHomeTask(Object task,int destination)throws Exception{
        int source=number(task,"displayId"),id=number(task,"taskId");
        if(source==destination){resumeHomeTask(id,destination);return;}
        Object sourceRoot=home(source),destinationRoot=home(destination);
        if(sourceRoot==null)throw new IllegalStateException("@folduo/err_source_home_missing");
        // Keep Samsung's one-HOME-root-per-display invariant, but transfer the
        // selected launcher's child task, not the other display's stale home.
        if(destinationRoot!=null&&id!=number(sourceRoot,"taskId")){
            api.getMethod("moveTaskToRootTask",int.class,int.class,boolean.class).invoke(manager,id,number(destinationRoot,"taskId"),true);
            resumeHomeTask(id,destination);
        }else moveHome(sourceRoot,destination,true);
    }
    private void resumeHomeTask(int id,int display)throws Exception{
        int result=(int)api.getMethod("startActivityFromRecents",int.class,Bundle.class).invoke(manager,id,ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle());
        if(result<0)throw new IllegalStateException("@folduo/err_home_missing");
        api.getMethod("setFocusedTask",int.class).invoke(manager,id);lastDestination=display;
    }
    private List<?> tasks(int display)throws Exception{return (List<?>)api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(manager,1,false,false,display);}
    synchronized Bundle move(int source,int destination,boolean idle)throws Exception{
        Bundle result=new Bundle();List<?> tasks=tasks(source);
        if(!tasks.isEmpty()&&activityType(tasks.get(0))==2){
            // Prefer the destination's own native Home. Moving the only Home task
            // would empty the cover, defeating the two-panel overlap.
            Object destinationHome=home(destination);
            ComponentName sourceHome=(ComponentName)tasks.get(0).getClass().getField("topActivity").get(tasks.get(0));
            ComponentName innerHome=destinationHome==null?null:(ComponentName)destinationHome.getClass().getField("topActivity").get(destinationHome);
            if(destinationHome==null || !Objects.equals(sourceHome,innerHome))
                throw new UnsupportedOperationException("Independent native inner Home unavailable; preserving cover Home");
            resumeHomeTask(number(destinationHome,"taskId"),destination);
            result.putBoolean("ok",true);result.putBoolean("moved",false);result.putBoolean("home",true);return result;
        }
        if(tasks.isEmpty()||!standard(tasks.get(0))){
            if(idle){result.putBoolean("ok",true);return result;}
            throw new UnsupportedOperationException("@folduo/err_system_screen");
        }
        Object task=tasks.get(0);int id=number(task,"taskId");
        if(number(task,"displayId")!=source)throw new IllegalStateException("@folduo/err_app_moved");
        // Recents restarts the existing task on its destination. A bare reparent left it undrawn
        // on this Fold7. The framework still checks launch/display and task restrictions.
        Bundle options=ActivityOptions.makeBasic().setLaunchDisplayId(destination).toBundle();
        api.getMethod("startActivityFromRecents",int.class,Bundle.class).invoke(manager,id,options);
        lastDestination=destination;movedTasks.add(id);result.putBoolean("ok",true);result.putBoolean("moved",true);result.putInt("taskId",id);return result;
    }
    // One task owned by the bounded continuity experiment. Never sweep other tasks.
    private int probeTask=-1,probeHomeRoot=-1,probeCoverRoot=-1,probeAppRoot=-1;
    private String probeComponent="unknown";
    synchronized String beginProbe() throws Exception {
        List<?> visible=tasks(0);
        if(visible.isEmpty())throw new IllegalStateException("No visible cover task");
        Object focused=api.getMethod("getFocusedRootTaskInfo").invoke(manager);
        if(focused==null||number(focused,"displayId")!=0)throw new IllegalStateException("Cover does not own focused task");
        Object task=null;
        if(activityType(focused)==2)task=focused;
        else {
            List<?> candidates=(List<?>)api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(manager,64,false,false,0);
            Object top=focused.getClass().getField("topActivity").get(focused);
            for(Object candidate:candidates)if(containsTask(focused,number(candidate,"taskId"))&&Objects.equals(top,candidate.getClass().getField("topActivity").get(candidate))){task=candidate;break;}
        }
        if(task==null)throw new IllegalStateException("No task matches focused cover root");
        int type=activityType(task);probeComponent=String.valueOf(task.getClass().getField("topActivity").get(task));
        if(type!=1&&type!=2)throw new UnsupportedOperationException("System screen cannot be routed");
        probeTask=number(task,"taskId");
        if(type==2){
            Object source=home(0),destination=home(1);
            if(source==null)throw new IllegalStateException("Cover Home root missing");
            probeCoverRoot=number(source,"taskId");
            if(destination!=null){
                Object a=task.getClass().getField("topActivity").get(task);
                Object b=destination.getClass().getField("topActivity").get(destination);
                if(!Objects.equals(a,b))throw new UnsupportedOperationException("Inner Home differs from cover Home");
                probeTask=number(destination,"taskId");
                api.getMethod("setFocusedRootTask",int.class).invoke(manager,probeTask);
            }else{
                // Try the real launcher root; Android/Samsung may reject secondary Home.
                probeHomeRoot=probeCoverRoot;
                api.getMethod("moveRootTaskToDisplayOnTopOrBottom",int.class,int.class,boolean.class).invoke(manager,probeHomeRoot,1,true);
                api.getMethod("setFocusedRootTask",int.class).invoke(manager,probeHomeRoot);
            }
        }else{
            int result=(int)api.getMethod("startActivityFromRecents",int.class,Bundle.class).invoke(manager,probeTask,ActivityOptions.makeBasic().setLaunchDisplayId(1).toBundle());
            if(result<0)throw new IllegalStateException("Task launch rejected: "+result);
            // Focus repair is separate: rejection must not undo successful placement.
        }
        return "Requested native "+(type==2?"Home":"app")+" task "+probeTask+" ("+probeComponent+") on inner display";
    }
    private boolean containsTask(Object root,int task)throws Exception{
        if(number(root,"taskId")==task)return true;
        int[] children=(int[])root.getClass().getField("childTaskIds").get(root);
        if(children!=null)for(int child:children)if(child==task)return true;
        return false;
    }
    private Object probeRoot(int display)throws Exception{
        for(Object root:roots(display))if(containsTask(root,probeTask))return root;
        return null;
    }
    private boolean exclusivelyOwnedApp(Object root)throws Exception{
        if(!standard(root))return false;
        int[] children=(int[])root.getClass().getField("childTaskIds").get(root);
        if(children==null||children.length==0)return number(root,"taskId")==probeTask;
        return children.length==1&&children[0]==probeTask;
    }
    synchronized boolean probePlaced()throws Exception{return probeTask>=0&&probeRoot(1)!=null;}
    synchronized boolean probeVerified() throws Exception {
        if(probeTask<0||probeRoot(1)==null)return false;
        Object focused=api.getMethod("getFocusedRootTaskInfo").invoke(manager);
        return focused!=null&&number(focused,"displayId")==1&&containsTask(focused,probeTask);
    }
    synchronized String repairProbe()throws Exception{
        Object inner=probeRoot(1);
        if(inner!=null){
            api.getMethod("setFocusedRootTask",int.class).invoke(manager,number(inner,"taskId"));
            return "Task placement found on inner; explicitly requested focus for root "+number(inner,"taskId");
        }
        Object source=probeRoot(0);
        if(source==null||probeCoverRoot>=0||!exclusivelyOwnedApp(source))throw new IllegalStateException("No exclusively owned app root available for fallback");
        probeAppRoot=number(source,"taskId");
        api.getMethod("moveRootTaskToDisplayOnTopOrBottom",int.class,int.class,boolean.class).invoke(manager,probeAppRoot,1,true);
        api.getMethod("setFocusedRootTask",int.class).invoke(manager,probeAppRoot);
        return "Recents did not place task on inner; explicit app root transfer requested for "+probeAppRoot;
    }
    private String describe(Object info)throws Exception{
        if(info==null)return "none";
        return "task="+number(info,"taskId")+" display="+number(info,"displayId")+" type="+activityType(info)+" top="+info.getClass().getField("topActivity").get(info)+" children="+Arrays.toString((int[])info.getClass().getField("childTaskIds").get(info));
    }
    synchronized String probeSnapshot()throws Exception{
        return "Selected "+probeTask+" "+probeComponent+"; cover root ["+describe(probeRoot(0))+"]; inner root ["+describe(probeRoot(1))+"]; focused ["+describe(api.getMethod("getFocusedRootTaskInfo").invoke(manager))+"]";
    }
    synchronized void endProbe(boolean interactive) throws Exception {
        try{
            if(probeAppRoot>=0){
                Object root=probeRoot(1);
                if(root!=null){
                    if(number(root,"taskId")!=probeAppRoot||!exclusivelyOwnedApp(root))throw new IllegalStateException("Return refused: app root now contains other tasks");
                    api.getMethod("moveRootTaskToDisplayOnTopOrBottom",int.class,int.class,boolean.class).invoke(manager,probeAppRoot,0,interactive);
                }
            }else if(probeHomeRoot>=0){
                api.getMethod("moveRootTaskToDisplayOnTopOrBottom",int.class,int.class,boolean.class).invoke(manager,probeHomeRoot,0,interactive);
            }else if(probeCoverRoot<0&&probeTask>=0){
                // Only restore this exact app if it actually reached display 1.
                List<?> list=(List<?>)api.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class).invoke(manager,64,false,false,1);
                for(Object task:list)if(number(task,"taskId")==probeTask){
                    if(!interactive)throw new IllegalStateException("App return skipped while locked; normal mapping will resume");
                    int result=(int)api.getMethod("startActivityFromRecents",int.class,Bundle.class).invoke(manager,probeTask,ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
                    if(result<0)throw new IllegalStateException("App return rejected: "+result);
                }
            }
            if(interactive&&probeCoverRoot>=0)api.getMethod("setFocusedRootTask",int.class).invoke(manager,probeCoverRoot);
        }finally{probeTask=-1;probeHomeRoot=-1;probeCoverRoot=-1;probeAppRoot=-1;}
    }
    private boolean matchesLaunch(Object task, ComponentName component) throws Exception {
        Intent base = (Intent) task.getClass().getField("baseIntent").get(task);
        if (base != null && component.equals(base.getComponent())) return true;
        return component.equals(task.getClass().getField("realActivity").get(task))
            || component.equals(task.getClass().getField("origActivity").get(task));
    }
    synchronized void restore()throws Exception{
        List<?> visible=tasks(1);Object top=visible.isEmpty()?null:visible.get(0);
        int active=top!=null&&standard(top)?number(top,"taskId"):-1;
        // Return the current app first. Do not sweep unrelated HOME roots or change
        // which unrelated application was selected after the fold.
        if(active>=0)api.getMethod("startActivityFromRecents",int.class,Bundle.class).invoke(manager,active,ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
        // Native secondary Home was not moved from the cover; do not reparent it on cleanup.
        for(Object root:roots(1))if(standard(root)&&movedTasks.contains(number(root,"taskId"))&&number(root,"taskId")!=active)
            api.getMethod("moveRootTaskToDisplayOnTopOrBottom",int.class,int.class,boolean.class).invoke(manager,number(root,"taskId"),0,false);
        movedTasks.clear();lastDestination=0;
    }
    synchronized boolean isHome(int display)throws Exception{List<?> list=tasks(display);return !list.isEmpty() && activityType(list.get(0))==2;}
    synchronized android.content.ComponentName topComponent(int display)throws Exception{
        List<?> list=tasks(display);return list.isEmpty()?null:(android.content.ComponentName)list.get(0).getClass().getField("topActivity").get(list.get(0));
    }

}
