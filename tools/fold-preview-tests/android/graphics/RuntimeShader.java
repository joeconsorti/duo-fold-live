package android.graphics;
/** Detects regression of the removed edge-darkening shader in compiled preview tests. */
public class RuntimeShader extends Shader {
 public static int created;
 public float[] size;
 public RuntimeShader(String source){created++;}
 public void setFloatUniform(String name,float x,float y){
  if(!name.equals("size"))throw new AssertionError("unexpected edge uniform");
  size=new float[]{x,y};
 }
}
