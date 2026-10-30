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
 private volatile boolean enabled,closed,requireInnerGlass;
 private volatile float openThreshold=172,handoffAngle=HandoffSettings.DEFAULT;
 private static final class Draw {
  final boolean inner;final long when,captured;final int kind;
  Draw(boolean inner,long when,int kind,long captured){this.inner=inner;this.when=when;this.kind=kind;this.captured=captured;}
 }
 private volatile Draw lastDraw=new Draw(false,-1,0,-1);
 private volatile float angle,smoothing=FadeSettings.DEFAULT_SMOOTHING,gradualness=FadeSettings.DEFAULT_GRADUALNESS;
 private Choreographer frames;
 private final AtomicBoolean wakePending=new AtomicBoolean();
 private final HashMap<String,Field> fields=new HashMap<>();
 private final float[] alphas={-1,-1};
 private final int[] stacks={-1,-1},extents={-1,-1};
 private long statusAt;
 private final Choreographer.FrameCallback frame=when->this.tick.run();
 void settings(float smoothing,float gradualness,boolean requireGlass,float open,float handoff){handoffAngle=HandoffSettings.angle(handoff);this.smoothing=smoothing;this.gradualness=gradualness;requireInnerGlass=requireGlass;openThreshold=open;}
 private void schedule(){schedule(false);}
 /** fast: a visible ramp is in progress, so the fallback must not drop it to 12 fps. */
 private void schedule(boolean fast){
  if(frames==null)frames=Choreographer.getInstance();
  frames.postFrameCallback(frame);
  // Display VSYNC can stop or lag around the physical OFF interval. Idle checks
  // stay at 80 ms; an active ramp uses a ~60 Hz fallback so it cannot stutter.
  handler.postDelayed(tick,fast?16:80);
 }
 // Graceful release: instead of deleting visible black layers in one frame
 // (a hard pop), ramp them out. Cancelled if the fade becomes valid again.
 private long releaseStart=-1;
 private final float[] releaseFrom={0,0};
 private void release(long now,String reason){
  if(releaseStart<0){
   boolean visible=false;
   for(int i=0;i<2;i++)if(layers[i]!=null&&alphas[i]>0)visible=true;
   if(!visible){clear();return;}
   releaseStart=now;for(int i=0;i<2;i++)releaseFrom[i]=Math.max(0,alphas[i]);
  }
  float k=HandoffFadePolicy.releaseEnvelope(now-releaseStart,gradualness);
  if(k<=0){clear();return;}
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   for(int i=0;i<2;i++)if(layers[i]!=null){float a=releaseFrom[i]*k;t.setAlpha(layers[i],a).setVisibility(layers[i],a>0);alphas[i]=a;}
   t.apply();
  }
  status="Handoff fade releasing ("+reason+"): "+Math.round(k*100)+"%";
  schedule(true);
 }
 private volatile long lease,lastFresh;
 private long lastPrimary;
 private Object dm,wm;private Method info,keyguard,stack,color,crop,colorLayer;
 private volatile boolean ticking;
 volatile String status="Handoff fade idle";
 HandoffFade(){thread.start();handler=new Handler(thread.getLooper());}
 void update(boolean enabled,float angle,boolean fresh){
  long now=SystemClock.elapsedRealtime();if(fresh){this.angle=angle;lastFresh=now;}
  this.enabled=enabled;lease=now;if((!ticking||!enabled)&&wakePending.compareAndSet(false,true))handler.post(start);
 }
 private final Runnable start=()->{wakePending.set(false);if(!enabled){if(ticking&&!closed)this.tick.run();else clear();return;}if(!ticking&&!closed){ticking=true;this.tick.run();}};
 private final Runnable readinessTick=()->{if(ticking&&!closed&&enabled)this.tick.run();};
 void drawn(boolean inner,long when,int kind,long captured){
  lastDraw=new Draw(inner,when,kind,captured);
  if(kind!=HandoffFadePolicy.UI_DRAW){handler.removeCallbacks(readinessTick);handler.postDelayed(readinessTick,HandoffFadePolicy.COMMIT_SETTLE_MS);}
 }
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
   if(closed){clear();return;}
   if(!enabled||now-lease>1500||now-lastFresh>1500){release(now,enabled?"stale angle":"disabled");return;}
   init();if((boolean)keyguard.invoke(wm)){release(now,"keyguard");return;}
   Object p=info.invoke(dm,0);
   if(p==null){
    if(now-lastPrimary>500){release(now,"primary display unavailable");return;}
    try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
     for(int i=0;i<2;i++)if(layers[i]!=null){t.setAlpha(layers[i],1f).setVisibility(layers[i],true);alphas[i]=1f;}
     t.apply();
    }
    releaseStart=-1;schedule();return;
   }
   // Conditions recovered before the release finished: resume without resetting
   // the policy, so an in-flight switch still gets its black hold and reveal.
   releaseStart=-1;
   lastPrimary=now;
   policy.mapping((String)field(p,"uniqueId").get(p));
   boolean inner=Math.min(value(p,"logicalWidth"),value(p,"logicalHeight"))/(float)Math.max(value(p,"logicalWidth"),value(p,"logicalHeight"))>.7f;
   policy.settings(smoothing,gradualness);policy.handoff(handoffAngle);
   policy.renderer(requireInnerGlass,openThreshold);
   Draw evidence=lastDraw;
   float alpha=policy.opacity(now,inner,angle,true,value(p,"state")==2,evidence.when,evidence.inner,evidence.kind,evidence.captured);
   Object secondary=info.invoke(dm,1);
   boolean secondaryInner=secondary!=null&&Math.min(value(secondary,"logicalWidth"),value(secondary,"logicalHeight"))/(float)Math.max(value(secondary,"logicalWidth"),value(secondary,"logicalHeight"))>.7f;
   float mirrorAlpha=closingMirror.opacity(now,inner,secondaryInner&&value(secondary,"state")==2,mirrorSubmitted,gradualness);
   boolean animating=policy.transitioning()&&value(p,"state")==2;
   try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
    boolean changed=false;
    for(int i=0;i<2;i++){
     Object d=i==0?p:secondary;
     float layerAlpha=i==1&&!inner?Math.max(alpha,mirrorAlpha):alpha;
     if(layerAlpha>0&&layerAlpha<1)animating=true;
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
   if(now>=statusAt){statusAt=now+250;status="Handoff fade: "+Math.round(alpha*100)+"%; primary="+(inner?"inner":"cover")+"; "+(policy.transitioning()?"destination black/reveal":"angle fade")+"; "+policy.readiness+"; glass commit settle 2 ms; legacy ON settle 32 ms; reveal "+FadeSettings.reveal(gradualness)+" ms";}
   schedule(animating);
  }catch(Exception e){clear();status="Handoff fade unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage();}
 }};
 private void clear(){
  handler.removeCallbacks(readinessTick);handler.removeCallbacks(tick);if(frames!=null)frames.removeFrameCallback(frame);
  java.util.Arrays.fill(alphas,-1);java.util.Arrays.fill(stacks,-1);java.util.Arrays.fill(extents,-1);ticking=false;releaseStart=-1;policy.reset();closingMirror.reset();mirrorSubmitted=-1;
  for(int i=0;i<layers.length;i++)if(layers[i]!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(layers[i],false).reparent(layers[i],null).apply();}catch(Exception ignored){}layers[i].release();layers[i]=null;}
  status="Handoff fade idle";
 }
 void close(){closed=true;handler.post(()->{clear();thread.quitSafely();});}
}
