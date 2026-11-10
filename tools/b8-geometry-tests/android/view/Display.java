package android.view;
import android.graphics.Point;
public class Display {
 public static final int STATE_ON=2;
 public String physical="cover";
 public int state=2,rotation=0,width=2448,height=1848,reads=0;
 public Mode mode=new Mode(1248,1972);
 public int getDisplayId(){return 0;}
 public int getState(){return state;}
 public int getRotation(){return rotation;}
 public Mode getMode(){return mode;}
 public void getRealSize(Point p){reads++;p.x=width;p.y=height;}
 public static class Mode {
  private final int w,h;
  public Mode(int w,int h){this.w=w;this.h=h;}
  public int getPhysicalWidth(){return w;}
  public int getPhysicalHeight(){return h;}
 }
}
