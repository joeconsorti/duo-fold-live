package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class FixedTaskRouteTest {
 public static class Window {public int type=1,mode=1;public int getActivityType(){return type;}public int getWindowingMode(){return mode;}}
 public static class Config {public Window windowConfiguration=new Window();}
 public static class Task {public int taskId,displayId;public String topActivity="Duo";public int[] childTaskIds;public Config configuration=new Config();Task(int id,int display){taskId=id;displayId=display;childTaskIds=new int[]{id};}}
 public static class Api {public Task focus=new Task(42,0);public List<Task> source=new ArrayList<>(),target=new ArrayList<>();public Object getFocusedRootTaskInfo(){return focus;}public List<Task> getTasks(int n,boolean a,boolean b,int display){return display==0?source:target;}}
 @Test public void homeIsNeverSelected()throws Exception{Api api=new Api();api.focus.configuration.windowConfiguration.type=2;api.source.add(api.focus);FixedTaskRoute route=new FixedTaskRoute(api,Api.class);assertTrue(route.prepare().startsWith("SKIP"));assertFalse(route.selected());}
 @Test public void selectFocusedExactTaskAndRequireDestinationFocus()throws Exception{Api api=new Api();api.source.add(new Task(99,0));Task selected=new Task(42,0);api.source.add(selected);FixedTaskRoute route=new FixedTaskRoute(api,Api.class);assertTrue(route.prepare().contains("task=42"));assertFalse(route.placedAndFocused());selected.displayId=1;api.target.add(selected);assertFalse(route.placedAndFocused());api.focus=new Task(42,1);assertTrue(route.placedAndFocused());api.focus=new Task(99,1);assertFalse(route.placedAndFocused());}
 @Test public void multiWindowIsNeverSelected()throws Exception{Api api=new Api();api.focus.configuration.windowConfiguration.mode=6;api.source.add(api.focus);FixedTaskRoute route=new FixedTaskRoute(api,Api.class);route.prepare();assertFalse(route.selected());}
 @Test public void placementAndFocusAreIndependentDiagnostics()throws Exception{
  Api api=new Api();Task selected=new Task(42,0);api.source.add(selected);FixedTaskRoute route=new FixedTaskRoute(api,Api.class);route.prepare();
  api.target.add(selected);FixedTaskRoute.Observation placed=route.observe();assertTrue(placed.placed);assertFalse(placed.focused);assertFalse(placed.verified());assertEquals(0,placed.focusedDisplay);
  api.target.clear();api.focus=new Task(42,1);FixedTaskRoute.Observation focused=route.observe();assertFalse(focused.placed);assertTrue(focused.focused);assertFalse(focused.verified());
  api.focus=null;FixedTaskRoute.Observation missing=route.observe();assertFalse(missing.focused);assertEquals(-1,missing.focusedDisplay);
 }
}
