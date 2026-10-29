package android.app;
import android.content.ComponentName;import java.util.*;
public class IActivityTaskManager {
 public static class Bounds {public int w=1248,h=1972,left=0,top=0,right=1248,bottom=1972;public int width(){return w;}public int height(){return h;}public String toString(){return w+"x"+h;}}
 public static class Window {public int type=2,mode=1;public Bounds bounds=new Bounds();public int getActivityType(){return type;}public int getWindowingMode(){return mode;}public Bounds getMaxBounds(){return bounds;}public Bounds getBounds(){return bounds;}}
 public static class Config {public Window windowConfiguration=new Window();public int screenWidthDp=1248,screenHeightDp=1972,densityDpi=160;public float fontScale=1;}
 public static class Info {
  public int taskId,displayId,userId,parentTaskId=-1;public boolean visible=true;public int[] childTaskIds=new int[0];public ComponentName topActivity,baseActivity,realActivity,origActivity;public Config configuration=new Config();
  public Info(int id,int display,String pkg,int type){taskId=id;displayId=display;topActivity=new ComponentName(pkg,"Main");baseActivity=realActivity=topActivity;configuration.windowConfiguration.type=type;}
  public void layout(int w,int h){configuration.windowConfiguration.bounds.w=w;configuration.windowConfiguration.bounds.h=h;configuration.windowConfiguration.bounds.right=w;configuration.windowConfiguration.bounds.bottom=h;configuration.screenWidthDp=w;configuration.screenHeightDp=h;}
 }
 public boolean omitHomeFromTasks;public Info focused;public final List<Info> roots=new ArrayList<>();public int moves,focuses;
 public Info getFocusedRootTaskInfo(){return focused;}
 public List<Info> getTasks(int n,boolean a,boolean b,int d){if(omitHomeFromTasks)return Collections.singletonList(new Info(77,d,"stale.app",1));return getAllRootTaskInfosOnDisplay(d);}
 public List<Info> getAllRootTaskInfosOnDisplay(int d){List<Info> out=new ArrayList<>();for(Info r:roots)if(r.displayId==d)out.add(r);return out;}
 public void moveRootTaskToDisplayOnTopOrBottom(int id,int display,boolean top){moves++;for(Info r:roots)if(r.taskId==id){r.displayId=display;android.hardware.display.IDisplayManager.Info d=new android.hardware.display.IDisplayManager().getDisplayInfo(display);r.layout(d.logicalWidth,d.logicalHeight);}}
 public void setFocusedTask(int id){focuses++;for(Info r:roots)if(r.taskId==id)focused=r;}
}
