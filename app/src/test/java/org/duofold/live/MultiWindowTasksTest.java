package org.duofold.live;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MultiWindowTasksTest {
 public static class Bounds {
  public int left=0,top=0,right=600,bottom=900;
  public int width(){return right-left;}public int height(){return bottom-top;}
  public String toString(){return left+","+top+","+right+","+bottom;}
 }
 public static class Window {
  public int type=1,mode=6;public Bounds bounds=new Bounds();
  public int getActivityType(){return type;}public int getWindowingMode(){return mode;}public Bounds getBounds(){return bounds;}
 }
 public static class Config {public Window windowConfiguration=new Window();public String toString(){return "stable";}}
 public static class Info {
  public int taskId,displayId=0,userId=0,parentTaskId=100;public boolean isVisible=true;
  public int[] childTaskIds=new int[0];public String topActivity;public Config configuration=new Config();
  Info(int id){taskId=id;topActivity="app/"+id;}
 }
 public static class Api {
  public Info focused=new Info(100);public List<Info> tasks=new ArrayList<>();
  Api(){focused.configuration.windowConfiguration.type=0;focused.configuration.windowConfiguration.mode=1;focused.topActivity=null;tasks.add(new Info(1));tasks.add(new Info(2));}
  public Info getFocusedRootTaskInfo(){return focused;}
  public List<Info> getTasks(int n,boolean a,boolean b,int d){return tasks;}
 }
 private MultiWindowTasks.Snapshot read(Api api)throws Exception{return MultiWindowTasks.inspect(api,Api.class);}
 private void rejects(Api api)throws Exception{try{read(api);fail("Expected rejection");}catch(IllegalStateException expected){}}
 @Test public void selectsVisibleLeavesUnderOrganizerAndSortsIndependentlyOfFocusOrder()throws Exception{
  Api api=new Api();api.tasks.add(api.focused);MultiWindowTasks.Snapshot first=read(api);
  assertArrayEquals(new int[]{1,2},first.tasks);assertTrue(first.multitask);Collections.reverse(api.tasks);
  assertEquals(first.signature,read(api).signature);
 }
 @Test public void detectsThirdPaneHiddenPanesAndNativeCollapse()throws Exception{
  Api api=new Api();api.tasks.add(new Info(3));MultiWindowTasks.Snapshot original=read(api);assertEquals(3,original.tasks.length);
  api.tasks.get(1).isVisible=false;api.tasks.get(2).isVisible=false;
  MultiWindowTasks.Snapshot collapsed=read(api);assertArrayEquals(new int[]{1},collapsed.tasks);assertTrue(collapsed.containsOnly(original));
 }
 @Test public void sameTaskWithDifferentUserOrActivityIsNotOriginalWorkspace()throws Exception{
  Api api=new Api();MultiWindowTasks.Snapshot first=read(api);api.tasks.get(1).userId=10;assertFalse(read(api).containsOnly(first));
  api.tasks.get(1).userId=0;api.tasks.get(1).topActivity="different/activity";assertFalse(read(api).containsOnly(first));
 }
 @Test public void layoutChangeInvalidatesCaptureSignature()throws Exception{
  Api api=new Api();MultiWindowTasks.Snapshot first=read(api);api.tasks.get(1).configuration.windowConfiguration.bounds.right=800;
  assertNotEquals(first.signature,read(api).signature);assertFalse(read(api).fits(700,1000));assertTrue(read(api).fits(900,1000));
 }
 @Test public void rejectsPipRecentsMissingVisibilityAndUnfocusedLayout()throws Exception{
  Api api=new Api();api.tasks.get(0).configuration.windowConfiguration.mode=2;rejects(api);
  api=new Api();api.focused.configuration.windowConfiguration.type=3;rejects(api);
  api=new Api();for(Info t:api.tasks)t.isVisible=false;rejects(api);
  api=new Api();api.focused.displayId=1;rejects(api);
 }
 @Test public void normalFullscreenRemainsSingleTaskRoute()throws Exception{
  Api api=new Api();Info only=api.tasks.get(0);only.parentTaskId=-1;only.configuration.windowConfiguration.mode=1;api.tasks=Collections.singletonList(only);api.focused=only;
  assertFalse(read(api).multitask);
 }
}
