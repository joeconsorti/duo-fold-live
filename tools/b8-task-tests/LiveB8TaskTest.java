package org.duofold.live.experimental.b8;
import android.app.*;import android.hardware.display.IDisplayManager;import java.io.File;
public class LiveB8TaskTest {
 static int checks;static void check(boolean ok,String note){checks++;if(!ok)throw new AssertionError(note);}
 interface Query {void run()throws Exception;}
 static void reject(Query q,String note)throws Exception{try{q.run();throw new AssertionError(note);}catch(IllegalStateException expected){checks++;}}
 static IActivityTaskManager manager;static File root;
 static IActivityTaskManager.Info home(int id,int display){return new IActivityTaskManager.Info(id,display,"launcher",2);}
 static LiveB8Task fresh()throws Exception{
  manager=ActivityTaskManager.manager=new IActivityTaskManager();
  if(!IDisplayManager.a.uniqueId.equals("cover"))IDisplayManager.swap();
  android.view.IWindowManager.locked=false;android.os.IPowerManager.interactive=true;
  manager.omitHomeFromTasks=true;manager.focused=home(10,0);manager.roots.add(manager.focused);
  check("launcher".equals(LiveB8Task.focusedPackage()),"focused Home admitted");
  LiveB8Task t=new LiveB8Task(root,"launcher");t.selectSourceOnly();check(t.isHome()&&t.taskId()==10,"exact Home selected");return t;
 }
 public static void main(String[] args)throws Exception{root=new File(args[0]);
  LiveB8Task t=fresh();t.verifyHomePrimary("cover");
  final LiveB8Task noMove=t;reject(noMove::moveSelected,"must never move Home");t.focus();t.restore();
  check(manager.moves==0&&manager.focuses==0,"Home focus/restore cannot mutate task placement");
  // Android replaces Home on the new primary; Duo follows that actual focused Home.
  manager.focused.displayId=1;IDisplayManager.swap();manager.focused=home(20,0);manager.focused.layout(2448,1848);manager.roots.add(manager.focused);
  t.verifyPrimary("inner");check(t.taskId()==20,"native replacement Home task accepted");t.verifyNativePrimary("inner");
  check(manager.moves==0&&manager.focuses==0,"remap readiness is observation only");
  final LiveB8Task selected=t;
  reject(()->selected.verifyPrimary("cover"),"wrong physical primary rejected");
  manager.focused.configuration.windowConfiguration.type=3;reject(()->selected.verifyPrimary("inner"),"Recents cannot masquerade as Home");
  manager.focused.configuration.windowConfiguration.type=1;reject(()->selected.verifyPrimary("inner"),"launcher settings activity is not Home");
  manager.focused.configuration.windowConfiguration.type=2;manager.focused.userId=10;reject(()->selected.verifyPrimary("inner"),"different user Home rejected");manager.focused.userId=0;
  manager.focused.topActivity=new android.content.ComponentName("other.launcher","Main");reject(()->selected.verifyPrimary("inner"),"different launcher rejected");manager.focused.topActivity=new android.content.ComponentName("launcher","Main");
  manager.focused.childTaskIds=new int[]{20,21};reject(()->selected.verifyPrimary("inner"),"mixed Home root rejected");manager.focused.childTaskIds=new int[0];
  manager.focused.visible=false;reject(()->selected.verifyPrimary("inner"),"hidden Home rejected");manager.focused.visible=true;
  manager.focused.layout(1248,1972);reject(()->selected.verifyPrimary("inner"),"stale cover geometry rejected");manager.focused.layout(2448,1848);
  IActivityTaskManager.Info other=new IActivityTaskManager.Info(77,0,"other.app",1);manager.roots.add(other);reject(()->selected.verifyPrimary("inner"),"competing visible app rejected");manager.roots.remove(other);
  android.view.IWindowManager.locked=true;reject(()->selected.verifyPrimary("inner"),"locked Home rejected");android.view.IWindowManager.locked=false;
  t=fresh();manager.focused.configuration.windowConfiguration.type=3;reject(LiveB8Task::focusedPackage,"initial Recents rejected");
  t=fresh();manager.focused.configuration.windowConfiguration.type=1;
  manager.omitHomeFromTasks=false;t=new LiveB8Task(root,"launcher");t.selectOnly();check(!t.isHome(),"standard app keeps standard route");t.moveSelected();check(manager.moves==1&&manager.focused.displayId==1,"app root still moves to prepared display");
  IDisplayManager.swap();manager.focused.displayId=0;t.verifyPrimary("inner");t.restore();check(manager.moves==1,"already returned app not moved again");
  // Two/three visible leaves under an undefined fullscreen organizer root.
  for(int count:new int[]{2,3}){
   fresh();manager.omitHomeFromTasks=false;manager.roots.clear();
   IActivityTaskManager.Info organizer=new IActivityTaskManager.Info(100,0,"app.0",0);organizer.childTaskIds=new int[count];
   manager.focused=organizer;manager.roots.add(organizer);
   for(int n=0;n<count;n++){IActivityTaskManager.Info pane=new IActivityTaskManager.Info(101+n,0,"app."+n,1);pane.parentTaskId=100;pane.configuration.windowConfiguration.mode=6;pane.layout(600,900);manager.roots.add(pane);organizer.childTaskIds[n]=101+n;}
   t=new LiveB8Task(root,LiveB8Task.focusedPackage());t.selectSourceOnly();
   check(t.isWorkspace()&&!t.isHome()&&t.workspaceTasks().length==count,"all visible split panes selected");
   final LiveB8Task workspace=t;reject(workspace::moveSelected,"split root must never move");
   t.focus();t.restore();check(manager.moves==0&&manager.focuses==0,"workspace focus/cleanup do not alter group");
   t.verifyPrimary("cover");IDisplayManager.swap();t.verifyPrimary("inner");
   check(t.workspaceTasks().length==count,"group survives physical remap without fullscreen pane bounds");
   // Native cover policy can expose just one original member and later restore all.
   for(int n=2;n<manager.roots.size();n++)manager.roots.get(n).visible=false;
   t.verifyPrimary("inner");check(t.workspaceTasks().length==1,"native collapse accepted");
   // A new unfolding episode must not extract the one currently exposed member.
   manager.roots.get(1).configuration.windowConfiguration.mode=1;
   LiveB8Task resumed=new LiveB8Task(root,LiveB8Task.focusedPackage(),t);resumed.selectSourceOnly();
   check(resumed.isWorkspace()&&resumed.workspaceTasks().length==1,"collapsed cover member reuses native workspace route next episode");
   manager.roots.get(1).configuration.windowConfiguration.mode=6;
   LiveB8Task stagedResume=new LiveB8Task(root,LiveB8Task.focusedPackage(),t);stagedResume.selectSourceOnly();
   check(stagedResume.isWorkspace(),"collapsed member may retain multiwindow mode");
   for(int n=2;n<manager.roots.size();n++)manager.roots.get(n).visible=true;
   t.verifyPrimary("inner");check(t.workspaceTasks().length==count,"native group restored");
   resumed.verifyPrimary("inner");stagedResume.verifyPrimary("inner");
   check(resumed.workspaceTasks().length==count&&stagedResume.workspaceTasks().length==count,"new episodes restore all original group members from either collapsed windowing mode");
   IActivityTaskManager.Info pane=manager.roots.get(1);pane.userId=10;reject(()->workspace.verifyPrimary("inner"),"different user rejected");pane.userId=0;
   pane.topActivity=new android.content.ComponentName("foreign.app","Main");reject(()->workspace.verifyPrimary("inner"),"replacement app rejected");pane.topActivity=new android.content.ComponentName("app.0","Main");
   manager.roots.add(new IActivityTaskManager.Info(888,0,"foreign.app",1));reject(()->workspace.verifyPrimary("inner"),"new visible task rejected");manager.roots.remove(manager.roots.size()-1);
   pane.configuration.windowConfiguration.mode=2;reject(()->workspace.verifyPrimary("inner"),"PiP cannot be treated as split group");pane.configuration.windowConfiguration.mode=6;
   pane.layout(3000,3000);reject(()->workspace.verifyPrimary("inner"),"stale destination pane geometry rejected");pane.layout(600,900);
   android.view.IWindowManager.locked=true;reject(()->workspace.verifyPrimary("inner"),"locked workspace rejected");android.view.IWindowManager.locked=false;
   t.restore();check(manager.moves==0&&manager.focuses==0,"workspace failure cleanup still never moves or focuses");
  }
  System.out.println("LiveB8TaskTest: "+checks+" real task-routing checks passed (fake framework)");
 }
}
