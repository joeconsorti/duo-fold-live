package android.graphics;
public class RadialGradient extends Shader {
 public static int created;
 public RadialGradient(float x,float y,float radius,int[] colors,float[] positions,TileMode tile){
  if(radius<=0||colors.length!=positions.length||colors.length<2)throw new AssertionError("invalid dim gradient");
  created++;
 }
}
