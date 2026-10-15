package org.duofold.live;
import android.os.*;
import android.util.Log;
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
 private boolean lastRecord;
 private static final boolean FAST_SWITCH_TICKS=DeviceCompatibility.isFold7(Build.MODEL);
 private final Choreographer.FrameCallback frame=when->this.tick.run();
 void settings(float smoothing,float gradualness,boolean requireGlass,float open,float handoff){handoffAngle=HandoffSettings.angle(handoff);blurSwitch=HandoffFadePolicy.blackAnglesFor(Build.MODEL,handoffAngle)[1];this.smoothing=smoothing;this.gradualness=gradualness;requireInnerGlass=requireGlass;openThreshold=open;}
 private volatile boolean idle;private volatile long lastMovedAt;
 // Right-half blur continuity (RightHalfBlur): one effect layer on the inner panel while it is primary.
 // Fold 7 unified renderer only: debug.duofold.unified=0 must leave the original path without this blur (review on PR #18).
 private final boolean rightBlurEnabled=DeviceCompatibility.isFold7(Build.MODEL)&&UnifiedRenderer.enabled();
 private SurfaceControl rightBlur;private Method effectLayer,blurRadius;
 // Max, glide and clear angle come from BlurTuning (shared with the mirror and the hold, 1 s cache).
 private float blurShown=Float.NaN,blurSwitch=101;private long blurAt;private int blurApplied=-1,blurStack=-1;private String blurCrop="";
 private long keyguardAt=-1;private boolean keyguardLocked;
 private void schedule(){
  // Idle (no switch, fully clear): no per-vsync work. A new angle wakes the tick at once (update()).
  if(idle){handler.postDelayed(tick,HandoffFadePolicy.IDLE_TICK_MS);return;}
  if(frames==null)frames=Choreographer.getInstance();
  frames.postFrameCallback(frame);
  // Display VSYNC can stop during the physical OFF interval. Keep lease and
  // lock checks alive without running a competing high-frequency timer.
  handler.postDelayed(tick,HandoffFadePolicy.tickDelayMs(policy.transitioning(),FAST_SWITCH_TICKS));
 }
 private volatile long lease,lastFresh;
 private long lastPrimary;
 private Object dm,wm;private Method info,keyguard,stack,color,crop,colorLayer;
 private volatile boolean ticking;
 volatile String status="Handoff fade idle";
 HandoffFade(){policy.coverLag(HandoffFadePolicy.coverLagFor(Build.MODEL));policy.readyTimeout(HandoffFadePolicy.readyTimeoutFor(Build.MODEL));policy.openingReadyTimeout(HandoffFadePolicy.openingReadyTimeoutFor(Build.MODEL));policy.coverGlass(DeviceCompatibility.isFold7(Build.MODEL)&&UnifiedRenderer.enabled());policy.closingReadyTimeout(HandoffFadePolicy.closingReadyTimeoutFor(Build.MODEL));policy.freshWindow(HandoffFadePolicy.freshWindowFor(Build.MODEL));policy.revealBase(HandoffFadePolicy.revealBaseFor(Build.MODEL));blurSwitch=HandoffFadePolicy.blackAnglesFor(Build.MODEL,HandoffSettings.DEFAULT)[1];thread.start();handler=new Handler(thread.getLooper());}
 void update(boolean enabled,float angle,boolean fresh){
  long now=SystemClock.elapsedRealtime();boolean moved=fresh&&Float.compare(angle,this.angle)!=0;if(fresh){this.angle=angle;lastFresh=now;}
  if(moved)lastMovedAt=now;
  // Wake on every real angle change (plan D8): reading `idle` from this Binder thread could miss the tick
  // that just turned idle; wakePending already drops duplicate posts.
  this.enabled=enabled;lease=now;if((!ticking||!enabled||moved)&&wakePending.compareAndSet(false,true))handler.post(start);
 }
 private final Runnable start=()->{wakePending.set(false);if(!enabled){clear();return;}if(closed)return;if(!ticking){ticking=true;this.tick.run();}else if(idle)this.tick.run();};
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
  try{effectLayer=SurfaceControl.Builder.class.getMethod("setEffectLayer");blurRadius=SurfaceControl.Transaction.class.getMethod("setBackgroundBlurRadius",SurfaceControl.class,int.class);}catch(Exception e){effectLayer=null;blurRadius=null;}
  dm=candidate;info=get;
 }
 private final Runnable tick=new Runnable(){public void run(){
  handler.removeCallbacks(this);if(frames!=null)frames.removeFrameCallback(frame);
  try{
   long now=SystemClock.elapsedRealtime();
   if(closed||!enabled||now-lease>1500||now-lastFresh>1500){clear();return;}
   init();
   // Keyguard is a Binder call; once per 200 ms is plenty for a lock that takes far longer to engage.
   if(keyguardAt<0||now-keyguardAt>=200){keyguardLocked=(boolean)keyguard.invoke(wm);keyguardAt=now;}
   if(keyguardLocked){clear();return;}
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
   policy.settings(smoothing,gradualness);policy.handoff(handoffAngle);
   policy.renderer(requireInnerGlass,openThreshold);
   Draw evidence=lastDraw;
   float alpha=policy.opacity(now,inner,angle,true,value(p,"state")==2,evidence.when,evidence.inner,evidence.kind,evidence.captured);
   Object secondary=info.invoke(dm,1);
   boolean secondaryInner=secondary!=null&&Math.min(value(secondary,"logicalWidth"),value(secondary,"logicalHeight"))/(float)Math.max(value(secondary,"logicalWidth"),value(secondary,"logicalHeight"))>.7f;
   float mirrorAlpha=closingMirror.opacity(now,inner,secondaryInner&&value(secondary,"state")==2,mirrorSubmitted,gradualness);
   if(BuildConfig.DIAGNOSTICS)trace(now,inner,value(p,"state"),alpha,mirrorAlpha,secondary==null?-1:value(secondary,"state"));
   try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
    boolean changed=false;
    boolean record=RecordVisible.enabled();
    if(record!=lastRecord){lastRecord=record;for(SurfaceControl l:layers)if(l!=null){RecordVisible.hide(t,l);changed=true;}if(rightBlur!=null){RecordVisible.hide(t,rightBlur);changed=true;}}
    for(int i=0;i<2;i++){
     Object d=i==0?p:secondary;
     float layerAlpha=i==1&&!inner?Math.max(alpha,mirrorAlpha):alpha;
     if(d==null){if(layers[i]!=null&&alphas[i]!=layerAlpha){t.setAlpha(layers[i],layerAlpha).setVisibility(layers[i],layerAlpha>0);alphas[i]=layerAlpha;changed=true;}continue;}
     if(layers[i]==null){
      SurfaceControl.Builder builder=new SurfaceControl.Builder().setName("Duo handoff black fade "+i).setHidden(true);
      colorLayer.invoke(builder);layers[i]=builder.build();changed=true;
      RecordVisible.hide(t,layers[i]);
      color.invoke(t,layers[i],new float[]{0,0,0});t.setLayer(layers[i],Integer.MAX_VALUE-5);
     }
     int targetStack=value(d,"layerStack");
     if(stacks[i]!=targetStack){stack.invoke(t,layers[i],targetStack);stacks[i]=targetStack;changed=true;}
     int extent=Math.max(2448,Math.max(value(d,"logicalWidth"),value(d,"logicalHeight")));
     if(extents[i]!=extent){crop.invoke(t,layers[i],extent,extent);extents[i]=extent;changed=true;}
     if(alphas[i]!=layerAlpha){t.setAlpha(layers[i],layerAlpha).setVisibility(layers[i],layerAlpha>0);alphas[i]=layerAlpha;changed=true;}
    }
    if(rightHalfBlur(t,now,inner,p))changed=true;
    if(changed)t.apply();
   }
   // Idle only when nothing fades and the hinge is at rest (not resting-in-band within PollCadence's hold).
   idle=!policy.transitioning()&&alpha<=0f&&mirrorAlpha<=0f&&!blurMoving&&!PollCadence.fast(now-lastMovedAt,angle);  // C1: not while resting in the band
   if(now>=statusAt){statusAt=now+250;status="Handoff fade: "+Math.round(alpha*100)+"%; primary="+(inner?"inner":"cover")+"; "+(policy.transitioning()?"destination black/reveal":"angle fade")+"; "+policy.readiness+"; glass commit settle 2 ms; legacy ON settle 32 ms; reveal "+policy.revealMs(gradualness)+" ms";}
   schedule();
  }catch(Exception e){clear();status="Handoff fade unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage();}
 }};
 private boolean blurMoving;
 /** Glides the right-half blur toward RightHalfBlur.radius at display rate; true when the transaction changed. */
 private boolean rightHalfBlur(SurfaceControl.Transaction t,long now,boolean inner,Object p)throws Exception{
  boolean upright=inner&&value(p,"rotation")==0;
  boolean wanted=rightBlurEnabled&&requireInnerGlass&&effectLayer!=null&&upright;
  // Cover primary (closed side), rotated, or disabled: drop to 0 at once and never follow the primary
  // display onto the cover; the mirror's own blur takes over on the inner panel.
  if(!wanted){
   blurShown=Float.NaN;blurMoving=false;
   if(rightBlur!=null&&blurApplied!=0){blurRadius.invoke(t,rightBlur,0);t.setVisibility(rightBlur,false);blurApplied=0;return true;}
   return false;
  }
  float clear=BlurTuning.rightClear();
  float target=RightHalfBlur.radius(angle,blurSwitch,clear>0?clear:openThreshold,BlurTuning.max());
  if(rightBlur==null&&target<1){blurShown=Float.NaN;blurMoving=false;return false;}
  // Start at the target (the switch is under black); afterwards follow the 2-3 deg hinge steps smoothly.
  blurShown=Float.isFinite(blurShown)?PreviewBlurPolicy.glide(blurShown,target,now-blurAt,BlurTuning.smoothMs()):target;blurAt=now;
  blurMoving=Math.abs(blurShown-target)>=.5f;
  boolean changed=false;
  if(rightBlur==null){
   SurfaceControl.Builder b=new SurfaceControl.Builder().setName("Duo right-half blur");effectLayer.invoke(b);rightBlur=b.build();
   RecordVisible.hide(t,rightBlur);t.setLayer(rightBlur,Integer.MAX_VALUE-6);blurApplied=-1;blurStack=-1;blurCrop="";changed=true;
  }
  int w=value(p,"logicalWidth"),h=value(p,"logicalHeight"),stackId=value(p,"layerStack");
  if(stackId!=blurStack){stack.invoke(t,rightBlur,stackId);blurStack=stackId;changed=true;}
  String crop=w+"x"+h;
  if(!crop.equals(blurCrop)){t.setCrop(rightBlur,new android.graphics.Rect(w/2,0,w,h));blurCrop=crop;changed=true;}
  int r=Math.round(blurShown);
  if(r!=blurApplied){blurRadius.invoke(t,rightBlur,r);t.setVisibility(rightBlur,r>0);blurApplied=r;changed=true;}
  return changed;
 }
 private void releaseRightBlur(){
  if(rightBlur==null)return;
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(rightBlur,false).reparent(rightBlur,null).apply();}catch(Exception ignored){}
  rightBlur.release();rightBlur=null;blurShown=Float.NaN;blurApplied=-1;blurStack=-1;blurCrop="";blurMoving=false;
 }
 private String lastTrace="";
 /** DIAGNOSTICS builds only (debug, fold7test): one logcat line per change in fade decision, for timing against display events. */
 private void trace(long now,boolean inner,int state,float alpha,float mirrorAlpha,int secondaryState){
  String key=(inner?"inner":"cover")+" s="+state+" s2="+secondaryState+" a="+Math.round(alpha*20)*5+" m="+Math.round(mirrorAlpha*20)*5
   +" "+(policy.transitioning()?"switch":"angle")+" "+policy.readiness;
  if(key.equals(lastTrace))return;
  lastTrace=key;
  Log.i("DuoHandoff","t="+now+" angle="+angle+" age="+(now-lastFresh)+" "+key);
 }
 private void clear(){
  handler.removeCallbacks(readinessTick);handler.removeCallbacks(tick);if(frames!=null)frames.removeFrameCallback(frame);
  java.util.Arrays.fill(alphas,-1);java.util.Arrays.fill(stacks,-1);java.util.Arrays.fill(extents,-1);ticking=false;idle=false;keyguardAt=-1;policy.reset();closingMirror.reset();mirrorSubmitted=-1;
  for(int i=0;i<layers.length;i++)if(layers[i]!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(layers[i],false).reparent(layers[i],null).apply();}catch(Exception ignored){}layers[i].release();layers[i]=null;}
  releaseRightBlur();status="Handoff fade idle";
 }
 void close(){closed=true;handler.post(()->{clear();thread.quitSafely();});}
}
