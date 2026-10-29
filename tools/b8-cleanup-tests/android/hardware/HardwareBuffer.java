package android.hardware;
public class HardwareBuffer {
 private final int width,height;public boolean closed;
 public HardwareBuffer(int w,int h){width=w;height=h;}
 public int getWidth(){return width;}public int getHeight(){return height;}public void close(){closed=true;}
}
