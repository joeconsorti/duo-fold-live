package android.graphics;
public class LinearGradient extends Shader {
 public LinearGradient(float x,float y,float x2,float y2,int top,int bottom,TileMode tile){
  if(top!=0x12ffffff||bottom!=0x18000000||tile!=TileMode.CLAMP)throw new AssertionError("Home frost tint");
 }
}
