package android.graphics;
import java.util.ArrayList;
import java.util.List;
public class Canvas {
 public static final List<String> draws=new ArrayList<>();
 public static boolean hardware=true;
 public Canvas(Bitmap bitmap){}
 public boolean isHardwareAccelerated(){return hardware;}
 public void drawColor(int c){draws.add("color");}
 public void drawBitmap(Bitmap b,Rect src,Rect dst,Paint p){draws.add("background:"+dst);}
 public void drawBitmap(Bitmap b,Rect src,RectF dst,Paint p){draws.add("pane:"+dst);}
 public int saveLayerAlpha(float l,float t,float r,float b,int a){draws.add("alpha:"+a);return 1;}
 public int save(){return 1;}
 public void restoreToCount(int n){draws.add("restore");}
 public void scale(float x,float y){if(x<=0||y<=0)throw new AssertionError("invalid layer scale");}
 public void drawRenderNode(RenderNode node){
  if(node.effect==null||node.recording)throw new AssertionError("incomplete frost node");
  draws.add("blur");draws.add("node:"+Math.max(node.w,node.h));
 }
 public void drawRect(float l,float t,float r,float b,Paint paint){draws.add(paint.getShader() instanceof RadialGradient?"preview-dim":"tint");}
}
