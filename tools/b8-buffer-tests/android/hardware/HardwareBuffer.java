package android.hardware;import java.util.*;
public class HardwareBuffer{public static final int RGBA_8888=1;public static final long USAGE_GPU_COLOR_OUTPUT=512,USAGE_GPU_SAMPLED_IMAGE=256,USAGE_COMPOSER_OVERLAY=2048;
 public static List<HardwareBuffer> all=new ArrayList<>();public boolean closed;public int renders;
 public static HardwareBuffer create(int w,int h,int f,int l,long u){if(u!=(512|256|2048))throw new AssertionError("usage");HardwareBuffer b=new HardwareBuffer();all.add(b);return b;}public void close(){closed=true;}}
