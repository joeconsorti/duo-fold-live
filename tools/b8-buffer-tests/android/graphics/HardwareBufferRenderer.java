package android.graphics;import android.hardware.*;import java.util.*;import java.util.concurrent.Executor;import java.util.function.Consumer;
public class HardwareBufferRenderer{public static List<Runnable> draws=new ArrayList<>();public HardwareBuffer buffer;public boolean closed;
 public HardwareBufferRenderer(HardwareBuffer b){buffer=b;}public void setContentRoot(RenderNode n){}public RenderRequest obtainRenderRequest(){return new RenderRequest();}public void close(){closed=true;}
 public class RenderRequest{public RenderRequest setColorSpace(ColorSpace c){return this;}public void draw(Executor e,Consumer<RenderResult> c){buffer.renders++;draws.add(()->e.execute(()->c.accept(new RenderResult())));}}
 public static class RenderResult{public static final int SUCCESS=0;public SyncFence getFence(){return new SyncFence(true);}public int getStatus(){return SUCCESS;}}
 public static void finish(){List<Runnable> q=new ArrayList<>(draws);draws.clear();for(Runnable r:q)r.run();}}
