package org.duofold.live;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function3;

/** Exercise the compiled preview, including draw order and bitmap reuse, with a fake Canvas. */
public class FoldPreviewTest {
 private static int checks;
 private static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
 private static void noAddedDarkening(){
  check(!Canvas.draws.contains("preview-edges")&&!Canvas.draws.contains("preview-dim"),"preview contains neither added darkening overlay");
 }
 private static GlassFrame frame(int w,int h,String panel){
  return new GlassFrame(new Bitmap(w/4,h/4),w,h,100,List.of(new Bitmap(18,24)),panel,0,0,"test",0,null,null,List.of(),0,true,1f);
 }
 private static void route(int sw,int sh,int w,int h,boolean app){
  GlassFrame source=frame(sw,sh,"out"),nativeFrame=frame(w,h,"in");
  Canvas canvas=new Canvas(new Bitmap(w/4,h/4));
  List<GlassFrame> drawn=new ArrayList<>();
  List<FoldFrameStyle> styles=new ArrayList<>();
  Function3<Canvas,GlassFrame,FoldFrameStyle,Unit> draw=(target,f,style)->{
   check((target instanceof android.graphics.RecordingCanvas)==(style==FoldFrameStyle.APP_FROST && canvas.isHardwareAccelerated()),"only app frost is drawn into its blur layer");drawn.add(f);styles.add(style);return Unit.INSTANCE;};
  FoldFrameStyle nativeStyle=app?FoldFrameStyle.STANDARD:FoldFrameStyle.FULL_WIDTH_HOME;
  FoldFrameStyle frostStyle=app?FoldFrameStyle.APP_FROST:FoldFrameStyle.FULL_WIDTH_HOME;
  try(FoldPreview preview=new FoldPreview(source,w,h,0,app)){
   int initial=Bitmap.all.size(),initialShaders=android.graphics.RuntimeShader.created,initialGradients=android.graphics.RadialGradient.created;SystemClock.fixed=100;Canvas.draws.clear();
   preview.draw(canvas,nativeFrame,true,40,172,draw);
   check(drawn.equals(List.of(nativeFrame))&&styles.equals(List.of(nativeStyle))&&Bitmap.all.size()==initial,"already native skips texture preparation");
   check(!Canvas.draws.contains("preview-edges")&&android.graphics.RuntimeShader.created==initialShaders,"already native skips shading and shader allocation");
   drawn.clear();styles.clear();
   preview.draw(canvas,source,false,40,172,draw);
   check(drawn.size()==1&&styles.get(0)==frostStyle,"pending presents only frost");
   noAddedDarkening();
   GlassFrame frost=drawn.get(0);Bitmap tiny=frost.getBitmap();
   check(Math.max(tiny.getWidth(),tiny.getHeight())<=24&&tiny.getWidth()>0&&tiny.getHeight()>0,"bounded texture");
   check(!B8SnapshotPolicy.matches("in",w,h,0,frost.getPhysicalId(),frost.getWidth(),frost.getHeight(),frost.getRotation()),"cosmetic texture cannot satisfy capture readiness");
   check(frost.getLevels().size()==7,"Home and apps supply all seven blur levels");
   if(app)check(Math.max(tiny.getWidth(),tiny.getHeight())<=12,"strong app frost covers the entire panel even at zero hinge shading");
   if(app&&canvas.isHardwareAccelerated()){
    check(Canvas.draws.contains("blur")&&Canvas.draws.contains("tint"),"explicit whole-panel blur and tint");
    for(String line:Canvas.draws)if(line.startsWith("node:"))check(Integer.parseInt(line.substring(5))<=96,"bounded blur layer");
   }
   int allocations=Bitmap.all.size();
   for(int i=0;i<20;i++){drawn.clear();Canvas.draws.clear();preview.draw(canvas,source,false,40+i,172,draw);check(drawn.equals(List.of(frost)),"reuses the same image while the hinge moves");noAddedDarkening();}
   check(Bitmap.all.size()==allocations,"moving preview makes no new bitmaps");
   check(android.graphics.RuntimeShader.created==initialShaders&&android.graphics.RadialGradient.created==initialGradients,"moving preview allocates no added darkening shaders");
   drawn.clear();styles.clear();Canvas.draws.clear();preview.draw(canvas,nativeFrame,true,76,172,draw);
   check(drawn.equals(List.of(nativeFrame,frost))&&styles.equals(List.of(nativeStyle,frostStyle)),"first native frame underneath cached frost");
   noAddedDarkening();
   SystemClock.fixed+=60;drawn.clear();Canvas.draws.clear();preview.draw(canvas,nativeFrame,true,76,172,draw);
   check(drawn.equals(List.of(nativeFrame,frost))&&Canvas.draws.contains("alpha:127")&&Canvas.draws.contains("restore"),"half-way dissolve composites both frames");
   noAddedDarkening();
   SystemClock.fixed+=60;drawn.clear();Canvas.draws.clear();preview.draw(canvas,nativeFrame,true,76,172,draw);
   check(drawn.equals(List.of(nativeFrame))&&!preview.animating(),"dissolve stops");
   noAddedDarkening();
   check(Bitmap.all.size()==allocations,"native transition reuses cached images");
   // Rejected native geometry returns to frost, even if the hinge reaches an endpoint.
   drawn.clear();Canvas.draws.clear();preview.draw(canvas,source,false,0,172,draw);check(drawn.equals(List.of(frost)),"missing content stays covered");
   noAddedDarkening();
   drawn.clear();Canvas.draws.clear();preview.draw(canvas,nativeFrame,true,0,172,draw);check(drawn.equals(List.of(nativeFrame))&&!preview.animating(),"ready endpoint skips cosmetic delay");
   noAddedDarkening();
  }
 }
 public static void main(String[] args){
  for(boolean app:new boolean[]{true,false}){
   route(1248,1972,2448,1848,app);route(2448,1848,1248,1972,app);
   route(1972,1248,1848,2448,app);route(1848,2448,1972,1248,app);
  }
  Canvas.hardware=false;route(1248,1972,2448,1848,true);
  System.out.println("FoldPreviewTest: "+checks+" shared-preview, crop-size, reuse, readiness and dissolve checks passed");
 }
}
