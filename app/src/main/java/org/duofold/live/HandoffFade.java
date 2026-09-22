package org.duofold.live;
import android.os.*;
import android.view.SurfaceControl;
import java.lang.reflect.Method;
/** Owned black color layers above the existing preview; retained during logical remapping. */
final class HandoffFade {
 private final HandlerThread thread=new HandlerThread("duo-handoff-fade");
 private final Handler handler;
 private final HandoffFadePolicy policy=new HandoffFadePolicy();
 private final SurfaceControl[] layers=new SurfaceControl[2];
 private volatile boolean enabled,closed,drawnInner;
 private volatile float angle;
 private volatile long lease,drawn=-1;
 private Object dm,wm;private Method info,keyguard,stack,color,crop,colorLayer;
 private boolean ticking;
 volatile String status="Handoff fade idle";
 HandoffFade(){thread.start();handler=new Handler(thread.getLooper());}
 void update(boolean enabled,float angle){this.angle=angle;this.enabled=enabled;lease=SystemClock.elapsedRealtime();handler.post(start);}
 private final Runnable start=()->{if(!ticking&&!closed){ticking=true;this.tick.run();}};
 void drawn(boolean inner,long when){drawnInner=inner;drawn=when;}
 private int value(Object o,String f)throws Exception{return o.getClass().getField(f).getInt(o);}
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
  try{
   long now=SystemClock.elapsedRealtime();
   if(closed||!enabled||now-lease>750){clear();return;}
   init();if((boolean)keyguard.invoke(wm)){clear();return;}
   Object p=info.invoke(dm,0);if(p==null){clear();return;}
   boolean inner=Math.min(value(p,"logicalWidth"),value(p,"logicalHeight"))/(float)Math.max(value(p,"logicalWidth"),value(p,"logicalHeight"))>.7f;
   float alpha=policy.opacity(now,inner,angle,true,value(p,"state")==2,drawn,drawnInner);
   try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
    for(int i=0;i<2;i++){
     Object d=i==0?p:info.invoke(dm,i);
     if(d==null){if(layers[i]!=null)t.setVisibility(layers[i],false);continue;}
     if(layers[i]==null){
      SurfaceControl.Builder builder=new SurfaceControl.Builder().setName("Duo handoff black fade "+i).setHidden(true);
      colorLayer.invoke(builder);layers[i]=builder.build();
      SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,layers[i],true);
      color.invoke(t,layers[i],new float[]{0,0,0});t.setLayer(layers[i],Integer.MAX_VALUE-5);
     }
     stack.invoke(t,layers[i],value(d,"layerStack"));
     crop.invoke(t,layers[i],value(d,"logicalWidth"),value(d,"logicalHeight"));t.setAlpha(layers[i],alpha).setVisibility(layers[i],alpha>0);
    }
    t.apply();
   }
   status="Handoff fade: "+Math.round(alpha*100)+"%; primary="+(inner?"inner":"cover")+"; reveal 180 ms";
   handler.postDelayed(this,8);
  }catch(Exception e){clear();status="Handoff fade unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage();}
 }};
 private void clear(){
  handler.removeCallbacks(tick);ticking=false;policy.reset();
  for(int i=0;i<layers.length;i++)if(layers[i]!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(layers[i],false).reparent(layers[i],null).apply();}catch(Exception ignored){}layers[i].release();layers[i]=null;}
  status="Handoff fade idle";
 }
 void close(){closed=true;handler.post(()->{clear();thread.quitSafely();});}
}
