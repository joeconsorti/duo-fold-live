package android.view;
import java.util.*;import java.util.concurrent.Executor;import java.util.function.Consumer;
public class SurfaceControl {
 public static final Object CREATOR=new Object();public static List<SurfaceControl> all=new ArrayList<>();public static List<Runnable> completions=new ArrayList<>();public static int transactions;
 public String name;public int stack=-1,layer;public float alpha=1;public boolean visible,released;public SurfaceControl parent;
 public boolean isValid(){return !released;}public void release(){released=true;}
 public static void finish(){List<Runnable> pending=new ArrayList<>(completions);completions.clear();for(Runnable r:pending)r.run();}
 public static class Builder {
  String name;SurfaceControl parent;
  public Builder setName(String s){name=s;return this;}public Builder setContainerLayer(){return this;}public Builder setColorLayer(){return this;}public Builder setParent(SurfaceControl s){parent=s;return this;}
  public SurfaceControl build(){SurfaceControl s=new SurfaceControl();s.name=name;s.parent=parent;all.add(s);return s;}
 }
 public static class Transaction implements AutoCloseable {
  List<Runnable> changes=new ArrayList<>(),done=new ArrayList<>();
  public Transaction setLayerStack(SurfaceControl s,int v){changes.add(()->s.stack=v);return this;}
  public Transaction setLayer(SurfaceControl s,int v){changes.add(()->s.layer=v);return this;}
  public Transaction setVisibility(SurfaceControl s,boolean v){changes.add(()->s.visible=v);return this;}
  public Transaction setAlpha(SurfaceControl s,float v){changes.add(()->s.alpha=v);return this;}
  public Transaction setColor(SurfaceControl s,float[] v){return this;}
  public Transaction setCrop(SurfaceControl s,android.graphics.Rect v){return this;}
  public Transaction setOpaque(SurfaceControl s,boolean v){return this;}
  public Transaction reparent(SurfaceControl s,SurfaceControl p){changes.add(()->s.parent=p);return this;}
  public Transaction addTransactionCompletedListener(Executor e,Consumer<Object> c){done.add(()->e.execute(()->c.accept(new Object())));return this;}
  public void apply(){transactions++;for(Runnable r:changes)r.run();completions.addAll(done);}
  public void close(){}
 }
}
