package org.duofold.live;
import android.graphics.Bitmap;
import java.util.Arrays;

public class HomeNativeCompositionTest {
 static int checks;
 static void check(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
 static Bitmap image(int color){Bitmap b=new Bitmap(64,64);Arrays.fill(b.pixels,color);return b;}
 static Bitmap icons(int alpha){Bitmap b=image(0);for(int y=20;y<40;y++)for(int x=20;x<40;x++)b.pixels[y*64+x]=(alpha<<24)|0x00ff0000;return b;}
 // Independent source-over oracle. Photo and live wallpaper have different
 // owners in the capture fixture. The final scene already contains the icons.
 static Bitmap scene(int background,Bitmap foreground){
  Bitmap b=image(background);
  for(int i=0;i<b.pixels.length;i++){
   int s=foreground.pixels[i],a=s>>>24,out=0xff000000;
   for(int shift=0;shift<=16;shift+=8)out|=((((s>>>shift)&255)*a+((background>>>shift)&255)*(255-a)+127)/255)<<shift;
   b.pixels[i]=out;
  }return b;
 }
 static HomeNativeComposition.Result validate(Bitmap scene,Bitmap launcher,HomeContentPolicy.Sample reference){
  return HomeNativeComposition.validateScene(scene,HomeNativeComposition.validateLauncher(launcher,reference));
 }
 static void rejects(Bitmap scene,Bitmap foreground,HomeContentPolicy.Sample reference,String reason){
  int before=Bitmap.all.size();
  try{validate(scene,foreground,reference);throw new AssertionError("Accepted "+reason);}catch(IllegalStateException expected){check(expected.getMessage().contains(reason),expected.getMessage());}
  check(!scene.recycled&&!foreground.recycled,"borrowed inputs remain owned by capture caller on rejection");
  for(int i=before;i<Bitmap.all.size();i++)check(Bitmap.all.get(i).recycled,"rejected composition releases its temporary images");
 }
 public static void main(String[] args){
  Bitmap launcher=icons(255),composed=scene(0xff00ff00,launcher);
  HomeNativeComposition.Result full=validate(composed,launcher,null);
  check(full.bitmap.getPixel(25,25)==0xffff0000,"opaque launcher icons stay visible");
  check(full.bitmap.getPixel(5,5)==0xff00ff00,"actual replacement photo retained, not underlying blue live wallpaper");
  for(int i=0;i<composed.pixels.length;i++)check(full.bitmap.pixels[i]==composed.pixels[i],"composed image preserved once including every background and icon pixel");
  check(!composed.recycled&&!launcher.recycled,"successful composition borrows inputs");
  check(full.agreement.equals("launcherAgreement mismatched=0/400"),"agreement diagnostic measures actual opaque content");
  for(int alpha:new int[]{16,60,128,200,239})rejects(composed,icons(alpha),full.content,"HOME_CONTENT_FADED");
  rejects(composed,image(0),full.content,"HOME_CONTENT_MISSING");
  rejects(image(0),launcher,full.content,"HOME_SCENE_INCOMPLETE");
  rejects(composed,new Bitmap(63,64),null,"HOME_NATIVE_BUFFER_MISMATCH");
  Bitmap edges=icons(255);edges.pixels[30*64+40]=0x80ff0000;
  Bitmap antialias=scene(0xff00ff00,edges);
  HomeNativeComposition.Result edge=validate(antialias,edges,full.content);
  check(edge.bitmap.getPixel(40,30)==0xff807f00,"legitimate partial edge alpha drawn once; probe must never be painted over scene");
  rejects(image(0xff00ff00),launcher,full.content,"HOME_SCENE_CONTENT_MISMATCH");
  // A solid launcher probe must not bless a washed-out or photo-overpainted scene.
  rejects(scene(0xff00ff00,icons(100)),launcher,full.content,"HOME_SCENE_CONTENT_MISMATCH");
  Bitmap changing=composed.copy(Bitmap.Config.ARGB_8888,true);
  for(int x=20;x<40;x++)changing.pixels[20*64+x]=0xff333333;
  HomeNativeComposition.Result changed=validate(changing,launcher,full.content);
  check(changed.bitmap.getPixel(20,20)==0xff333333,"small changing widget retained from actual scene; no probe overwrite");
  check(changed.agreement.equals("launcherAgreement mismatched=20/400"),"small inter-capture updates tolerated");
  HomeNativeComposition.Result blank=validate(image(0xff00ff00),image(0),null);
  check(blank.content.visible==0&&blank.agreement.contains("sparse evidence"),"legitimate blank source page permitted and labelled");
  // Bars must not supply foreground agreement. They come from another route.
  Bitmap bars=composed.copy(Bitmap.Config.ARGB_8888,true);Arrays.fill(bars.pixels,0,6*64,0xff000000);
  HomeNativeComposition.Result barResult=validate(bars,launcher,full.content);
  check(barResult.agreement.equals("launcherAgreement mismatched=0/400"),"bar pixels excluded from body readiness");
  for(HomeNativeComposition.Result r:new HomeNativeComposition.Result[]{full,edge,changed,blank,barResult})r.bitmap.recycle();
  System.out.println("HomeNativeCompositionTest: "+checks+" CPU pixels, cross-owner photo preservation, fade/overpaint rejection and ownership checks passed; not optical proof");
 }
}
