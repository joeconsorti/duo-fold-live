package android.graphics;
public class RenderEffect {
 final float radius;
 RenderEffect(float radius){this.radius=radius;}
 public static RenderEffect createBlurEffect(float x,float y,Shader.TileMode tile){
  if(x<=0||x!=y||tile!=Shader.TileMode.CLAMP)throw new AssertionError("isotropic clamp blur");
  return new RenderEffect(x);
 }
}
