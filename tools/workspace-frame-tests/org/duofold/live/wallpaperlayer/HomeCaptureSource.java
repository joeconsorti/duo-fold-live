package org.duofold.live.wallpaperlayer;
import android.graphics.*;import android.accessibilityservice.AccessibilityService;import java.util.*;import java.util.concurrent.Executor;
public class HomeCaptureSource{
 public static int captures,generation,panes=2;public static boolean changed,mismatch,failSecond,lateSecond;public static AccessibilityService.TakeScreenshotCallback late;
 public static HomeCaptureSource last;
 public final Bitmap wallpaper=new Bitmap(1248,1972);public final Rect crop=new Rect(0,0,1248,1972);
 public final int width=1248,height=1972,rotation=0;public final String physical="cover";
 public static HomeCaptureSource current(){return last=new HomeCaptureSource();}
 public static class Window{public final int id;public final Rect bounds;Window(int id,Rect b){this.id=id;bounds=b;}}
 public static class Pane{public final int task,layer;public final Window window;Pane(int task,int layer,Window w){this.task=task;this.layer=layer;window=w;}public String toString(){return task+":"+layer+":"+window.id+":"+window.bounds;}}
 public List<Pane> workspace(int[] tasks){List<Pane> p=new ArrayList<>();for(int i=0;i<panes;i++)p.add(new Pane(tasks[i],i,new Window(generation*10+i,new Rect(i*400,0,(i+1)*400,1972))));if(changed&&captures>=panes)p.remove(p.size()-1);return p;}
 public void capture(Window w,Executor e,AccessibilityService.TakeScreenshotCallback c){captures++;if(captures==2){if(failSecond){c.onFailure(6);return;}if(lateSecond){late=c;return;}}e.execute(()->c.onSuccess(new AccessibilityService.ScreenshotResult(mismatch?1248:w.bounds.width(),w.bounds.height())));}
}
