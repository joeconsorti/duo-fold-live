package com.mccal.folio;
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
