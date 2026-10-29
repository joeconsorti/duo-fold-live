package android.hardware.display;
public class IDisplayManager {
 public static class Info {public String uniqueId;public int state=2,committedState=2,logicalWidth,logicalHeight,rotation=0,logicalDensityDpi=160;Info(String id,int w,int h){uniqueId=id;logicalWidth=w;logicalHeight=h;}}
 public static Info a=new Info("cover",1248,1972),b=new Info("inner",2448,1848);
 public Info getDisplayInfo(int id){return id==0?a:b;}
 public static void swap(){Info tmp=a;a=b;b=tmp;}
}
