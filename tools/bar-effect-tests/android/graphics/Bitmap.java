package android.graphics;
import java.util.*;import java.util.concurrent.*;
/** Small CPU image oracle; no GPU/optical claims. */
public class Bitmap {
 public enum Config { ARGB_8888 }
 public static final List<Bitmap> all=new CopyOnWriteArrayList<>();
 public final int width,height;public final int[] pixels;public boolean recycled;
 public Bitmap(int w,int h){width=w;height=h;pixels=new int[w*h];all.add(this);}
 public int getWidth(){return width;}public int getHeight(){return height;}public int getAllocationByteCount(){return pixels.length*4;}
 public void recycle(){recycled=true;}public boolean isRecycled(){return recycled;}
 public int getPixel(int x,int y){return pixels[y*width+x];}
 public void getPixels(int[] dst,int offset,int stride,int x,int y,int w,int h){for(int row=0;row<h;row++)System.arraycopy(pixels,(y+row)*width+x,dst,offset+row*stride,w);}
 public Bitmap copy(Config c,boolean mutable){Bitmap b=new Bitmap(width,height);System.arraycopy(pixels,0,b.pixels,0,pixels.length);return b;}
 public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h);}
 public static Bitmap createBitmap(Bitmap base,int x,int y,int w,int h){Bitmap b=new Bitmap(w,h);for(int j=0;j<h;j++)System.arraycopy(base.pixels,(j+y)*base.width+x,b.pixels,j*w,w);return b;}
 public static Bitmap createScaledBitmap(Bitmap base,int w,int h,boolean filter){Bitmap b=new Bitmap(w,h);for(int y=0;y<h;y++)for(int x=0;x<w;x++)b.pixels[y*w+x]=base.pixels[y*base.height/h*base.width+x*base.width/w];return b;}
}
