package android.graphics;
import java.util.Arrays;
/** CPU source-over oracle for a normal opaque Home composite; no GPU claim. */
public class Canvas {
 private final Bitmap target;
 public Canvas(Bitmap target){this.target=target;}
 public void drawColor(int color){Arrays.fill(target.pixels,color);}
 public void drawBitmap(Bitmap source,float left,float top,Paint paint){
  if(left!=0||top!=0||source.width!=target.width||source.height!=target.height)throw new AssertionError("Unexpected image transform");
  for(int i=0;i<source.pixels.length;i++){
   int s=source.pixels[i],d=target.pixels[i],a=s>>>24;
   if((d>>>24)!=255)throw new AssertionError("Expected opaque background");
   int out=0xff000000;
   for(int shift=0;shift<=16;shift+=8){int channel=(((s>>>shift)&255)*a+((d>>>shift)&255)*(255-a)+127)/255;out|=channel<<shift;}
   target.pixels[i]=out;
  }
 }
}
