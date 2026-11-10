package android.graphics;
public class Matrix {
 public void setScale(float x,float y){if(x<=0||y<=0)throw new AssertionError("invalid gradient scale");}
 public boolean postTranslate(float x,float y){return true;}
}
