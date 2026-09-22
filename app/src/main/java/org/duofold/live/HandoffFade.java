package org.duofold.live;
import android.os.*;
import android.view.SurfaceControl;
import android.view.Choreographer;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Method;
/** Owned black color layers above the existing preview; retained during logical remapping. */
final class HandoffFade {
 private final HandlerThread thread=new HandlerThread("duo-handoff-fade");
 private final Handler handler;
 private final HandoffFadePolicy policy=new HandoffFadePolicy();
 private final ClosingMirrorFadePolicy closingMirror=new ClosingMirrorFadePolicy();
 private volatile long mirrorSubmitted=-1;
 void mirrorSubmitted(){mirrorSubmitted=SystemClock.elapsedRealtime();}
 private final SurfaceControl[] layers=new SurfaceControl[2];
 private volatile boolean enabled,closed,drawnInner;
 private volatile float angle,smoothing=FadeSettings.DEFAULT_SMOOTHING,gradualness=FadeSettings.DEFAULT_GRADUALNESS;
 private Choreographer frames;
 private final AtomicBoolean wakePending=new AtomicBoolean();
 private final HashMap<String,Field> fields=new HashMap<>();
 private final float[] alphas={-1,-1};
 private final int[] stacks={-1,-1},extents={-1,-1};
 private long statusAt;
 private final Choreographer.FrameCallback frame=when->this.tick.run();
 void settings(float smoothing,float gradualness){this.smoothing=smoothing;this.gradualness=gradualness;}
 private void schedule(){
  if(frames==null)frames=Choreographer.getInstance();
  frames.postFrameCallback(frame);
  // Display VSYNC can stop during the physical OFF interval. Keep lease and
  // lock checks alive without running a competing high-frequency timer.
  handler.postDelayed(tick,80);
 }
 private volatile long lease,lastFresh,drawn=-1;
 private long lastPrimary;
 private Object dm,wm;private Method info,keyguard,stack,color,crop,colorLayer;
 private volatile boolean ticking;
 volatile String status="Handoff fade idle";
 HandoffFade(){thread.start();handler=new Handler(thread.getLooper());}
 void update(boolean enabled,float angle,boolean fresh){
  long now=SystemClock.elapsedRealtime();if(fresh){this.angle=angle;lastFresh=now;}
  this.enabled=enabled;lease=now;if((!ticking||!enabled)&&wakePending.compareAndSet(false,true))handler.post(start);
 }
 private final Runnable start=()->{wakePending.set(false);if(!enabled){clear();return;}if(!ticking&&!closed){ticking=true;this.tick.run();}};
 void drawn(boolean inner,long when){drawnInner=inner;drawn=when;}
 private Field field(Object o,String name)throws Exception{Field f=fields.get(name);if(f==null){f=o.getClass().getField(name);fields.put(name,f);}return f;}
 private int value(Object o,String f)throws Exception{return field(o,f).getInt(o);}
 private void init()throws Exception{
  if(dm!=null)return;
  Object candidate=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
  Method get=candidate.getClass().getMethod("getDisplayInfo",int.class);
  IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
  wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
  keyguard=Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked");
  stack=SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class);
  color=SurfaceControl.Transaction.class.getMethod("setColor",SurfaceControl.class,float[].class);
  crop=SurfaceControl.Transaction.class.getMethod("setWindowCrop",SurfaceControl.class,int.class,int.class);
  colorLayer=SurfaceControl.Builder.class.getMethod("setColorLayer");
  dm=candidate;info=get;
 }
 private final Runnable tick=new Runnable(){public void run(){
  handler.removeCallbacks(this);if(frames!=null)frames.removeFrameCallback(frame);
  try{
   long now=SystemClock.elapsedRealtime();
   if(closed||!enabled||now-lease>1500||now-lastFresh>1500){clear();return;}
   init();if((boolean)keyguard.invoke(wm)){clear();return;}
   Object p=info.invoke(dm,0);
   if(p==null){
    if(now-lastPrimary>500){clear();return;}
    try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
     for(SurfaceControl layer:layers)if(layer!=null)t.setAlpha(layer,1f).setVisibility(layer,true);
     t.apply();
    }
    java.util.Arrays.fill(alphas,-1);schedule();return;
   }
   lastPrimary=now;
   policy.mapping((String)field(p,"uniqueId").get(p));
   boolean inner=Math.min(value(p,"logicalWidth"),value(p,"logicalHeight"))/(float)Math.max(value(p,"logicalWidth"),value(p,"logicalHeight"))>.7f;
   policy.settings(smoothing,gradualness);
   float alpha=policy.opacity(now,inner,angle,true,value(p,"state")==2,drawn,drawnInner);
   Object secondary=info.invoke(dm,1);
   boolean secondaryInner=secondary!=null&&Math.min(value(secondary,"logicalWidth"),value(secondary,"logicalHeight"))/(float)Math.max(value(secondary,"logicalWidth"),value(secondary,"logicalHeight"))>.7f;
   float mirrorAlpha=closingMirror.opacity(now,inner,secondaryInner&&value(secondary,"state")==2,mirrorSubmitted,gradualness);
   try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
    boolean changed=false;
    for(int i=0;i<2;i++){
     Object d=i==0?p:secondary;
     float layerAlpha=i==1&&!inner?Math.max(alpha,mirrorAlpha):alpha;
     if(d==null){if(layers[i]!=null&&alphas[i]!=layerAlpha){t.setAlpha(layers[i],layerAlpha).setVisibility(layers[i],layerAlpha>0);alphas[i]=layerAlpha;changed=true;}continue;}
     if(layers[i]==null){
      SurfaceControl.Builder builder=new SurfaceControl.Builder().setName("Duo handoff black fade "+i).setHidden(true);
      colorLayer.invoke(builder);layers[i]=builder.build();changed=true;
      SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,layers[i],true);
      color.invoke(t,layers[i],new float[]{0,0,0});t.setLayer(layers[i],Integer.MAX_VALUE-5);
     }
     int targetStack=value(d,"layerStack");
     if(stacks[i]!=targetStack){stack.invoke(t,layers[i],targetStack);stacks[i]=targetStack;changed=true;}
     int extent=Math.max(2448,Math.max(value(d,"logicalWidth"),value(d,"logicalHeight")));
     if(extents[i]!=extent){crop.invoke(t,layers[i],extent,extent);extents[i]=extent;changed=true;}
     if(alphas[i]!=layerAlpha){t.setAlpha(layers[i],layerAlpha).setVisibility(layers[i],layerAlpha>0);alphas[i]=layerAlpha;changed=true;}
    }
    if(changed)t.apply();
   }
   if(now>=statusAt){statusAt=now+250;status="Handoff fade: "+Math.round(alpha*100)+"%; primary="+(inner?"inner":"cover")+"; "+(policy.transitioning()?"destination black/reveal":"angle fade")+"; ON settle 120 ms; reveal "+FadeSettings.reveal(gradualness)+" ms";}
   schedule();
  }catch(Exception e){clear();status="Handoff fade unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage();}
 }};
 private void clear(){
  handler.removeCallbacks(tick);if(frames!=null)frames.removeFrameCallback(frame);
  java.util.Arrays.fill(alphas,-1);java.util.Arrays.fill(stacks,-1);java.util.Arrays.fill(extents,-1);ticking=false;policy.reset();closingMirror.reset();mirrorSubmitted=-1;
  for(int i=0;i<layers.length;i++)if(layers[i]!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(layers[i],false).reparent(layers[i],null).apply();}catch(Exception ignored){}layers[i].release();layers[i]=null;}
  status="Handoff fade idle";
 }
 void close(){closed=true;handler.post(()->{clear();thread.quitSafely();});}
}
