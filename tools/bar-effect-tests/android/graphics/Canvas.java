package android.graphics;
public class Canvas {
 private final Bitmap target;private float sx=1,sy=1;
 public Canvas(Bitmap b){target=b;}public void scale(float x,float y){sx*=x;sy*=y;}
 public void drawBitmap(Bitmap b,float x,float y,Paint p){drawBitmap(b,null,new Rect((int)x,(int)y,(int)x+b.width,(int)y+b.height),p);}
 public void drawBitmap(Bitmap b,Rect src,Rect dst,Paint p){int l=Math.round(dst.left*sx),t=Math.round(dst.top*sy),r=Math.round(dst.right*sx),bottom=Math.round(dst.bottom*sy);if(src==null)src=new Rect(0,0,b.width,b.height);for(int y=t;y<bottom;y++)for(int x=l;x<r;x++){if(x<0||y<0||x>=target.width||y>=target.height)throw new AssertionError("out-of-bounds draw");target.pixels[y*target.width+x]=b.pixels[(src.top+(y-t)*(src.bottom-src.top)/(bottom-t))*b.width+src.left+(x-l)*(src.right-src.left)/(r-l)];}}
}
