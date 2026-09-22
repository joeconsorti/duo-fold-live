package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
/** Exercise routing and cleanup against a framework-shaped fake; no mocked success bundles. */
public class NativeHomeRoutingTest {
 public static class Window {public int getActivityType(){return 2;}}
 public static class Configuration {public Window windowConfiguration=new Window();}
 public static class Info {
  public int taskId,displayId;public Object topActivity="launcher";
  public Configuration configuration=new Configuration();public int[] childTaskIds=new int[0];
  Info(int id,int display){taskId=id;displayId=display;}
 }
 public static class Manager {
  final Info cover=new Info(10,0);Info inner,focused=cover;boolean reject,ignore;int moves;
  public List<Info> getTasks(int n,boolean a,boolean b,int d){return getAllRootTaskInfosOnDisplay(d);}
  public List<Info> getAllRootTaskInfosOnDisplay(int d){List<Info> l=new ArrayList<>();if(cover.displayId==d)l.add(cover);if(inner!=null&&inner.displayId==d)l.add(inner);return l;}
  public void moveRootTaskToDisplayOnTopOrBottom(int id,int display,boolean top){if(reject)throw new SecurityException("Home forbidden");moves++;if(!ignore&&id==10)cover.displayId=display;}
  public void setFocusedRootTask(int id){focused=id==10?cover:inner;}
  public Info getFocusedRootTaskInfo(){return focused;}
 }
 @Test public void transfersActualHomeAndRestoresOnlyOwnedRoot()throws Exception{
  Manager m=new Manager();TaskDisplayRouter r=new TaskDisplayRouter(m,Manager.class);
  r.beginProbe();assertTrue(r.probeVerified());assertEquals(1,m.cover.displayId);
  r.endProbe(true);assertEquals(0,m.cover.displayId);assertEquals(2,m.moves);
  r.endProbe(true);assertEquals(2,m.moves);
 }
 @Test public void apiReturnWithoutTransferIsNotSuccess()throws Exception{
  Manager m=new Manager();m.ignore=true;TaskDisplayRouter r=new TaskDisplayRouter(m,Manager.class);
  r.beginProbe();assertFalse(r.probeVerified());
 }
 @Test public void existingInnerHomeIsFocusedAndNeverMoved()throws Exception{
  Manager m=new Manager();m.inner=new Info(20,1);TaskDisplayRouter r=new TaskDisplayRouter(m,Manager.class);
  r.beginProbe();assertTrue(r.probeVerified());r.endProbe(true);
  assertEquals(0,m.moves);assertSame(m.cover,m.focused);assertEquals(1,m.inner.displayId);
 }
 @Test public void frameworkRejectionPropagates()throws Exception{
  Manager m=new Manager();m.reject=true;TaskDisplayRouter r=new TaskDisplayRouter(m,Manager.class);
  try{r.beginProbe();fail("must reject");}catch(java.lang.reflect.InvocationTargetException e){assertTrue(e.getCause() instanceof SecurityException);}
  assertFalse(r.probeVerified());assertEquals(0,m.cover.displayId);
 }
 @Test public void unrelatedInnerFocusDoesNotVerify()throws Exception{
  Manager m=new Manager();TaskDisplayRouter r=new TaskDisplayRouter(m,Manager.class);r.beginProbe();
  m.focused=new Info(99,1);assertFalse(r.probeVerified());
 }
}
