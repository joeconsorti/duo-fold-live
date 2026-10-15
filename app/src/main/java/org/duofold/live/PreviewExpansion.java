package org.duofold.live;
import android.os.*;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.view.SurfaceControl;
/**
 * An owned, non-interactive compositor bridge. Uses an already prepared clean frame, never
 * requests a display state or secure capture. Fails closed on lock, stale lease, or timeout.
 */
final class PreviewExpansion extends Binder {
 static final String TOKEN="org.duofold.live.PreviewExpansion";
 private final int owner;
 private final HandlerThread thread=new HandlerThread("duo-preview-expansion");
 private final Handler handler;
 private Object dm,wm;private java.lang.reflect.Method displayInfo,keyguard;private long keyguardAt=-1;private boolean keyguardLocked;
 private boolean unified,lastRecord;
 private SurfaceControl layer,backdrop,seam,holdBlur;private java.lang.reflect.Method holdBlurRadius;private float holdRadius=56;private Bitmap seamHardware;private HardwareBuffer seamBuffer;private int seamPixels;private Bitmap hardware,cleanHardware;private HardwareBuffer buffer,cleanBuffer;
 private boolean frostedLeft,rightPreviewReady;
 private String innerId;private int bw,bh,sourceRotation;private boolean landscape;private String lastGeometry="";
 private volatile boolean enabled=false;
 private volatile float seamMotion=0;
 void motion(float angle,float open){seamMotion=RenderQuality.motion(angle,open);}
 private long stamp,start=0,ready=-1;private volatile long lastLease=0;
 private boolean polling=false,completed=false,pendingReady=false;
 private volatile boolean closed=false;
 private PreviewReleaseGate coverCommit;
 private volatile PreviewReleaseGate releaseGate;
 volatile String trace="No handoff yet";
 private int lastStack=-1,lastPanelState=-1;
 private long lastSample=0,maxSampleGap=0,offSince=-1,missingSince=-1,diagnosticStart=0;
 private String lastMapping="";
 private synchronized void diagnosticEvent(String text){event(text);}
 private String panel(Object info)throws Exception{
  if(info==null)return "absent";
  return (inner(info)?"inner":"cover")+" state="+number(info,"state")+" stack="+number(info,"layerStack")+" "+number(info,"logicalWidth")+"x"+number(info,"logicalHeight");
 }
 private void sample(Object primary,Object secondary,Object target,long now)throws Exception{
  if(diagnosticStart==0)return;
  if(lastSample>0)maxSampleGap=Math.max(maxSampleGap,now-lastSample);
  lastSample=now;
  String mapping="d0 "+panel(primary)+"; d1 "+panel(secondary);
  if(!mapping.equals(lastMapping)){lastMapping=mapping;diagnosticEvent(mapping);}
  if(target==null){if(missingSince<0){missingSince=now;diagnosticEvent("Inner mapping absent");}return;}
  if(missingSince>=0){diagnosticEvent("Inner mapping restored after "+(now-missingSince)+" ms (sampled)");missingSince=-1;}
  boolean off=number(target,"state")==1;
  if(off && offSince<0){offSince=now;diagnosticEvent("Inner reports OFF");}
  if(!off && offSince>=0){diagnosticEvent("Inner OFF interval "+(now-offSince)+" ms (sampled)");offSince=-1;}
 }
 void releaseReturned(){final long when=SystemClock.elapsedRealtime();final PreviewReleaseGate gate=releaseGate;
  if(gate!=null){gate.mayRelease(when);handler.post(()->diagnosticEvent("Cover-primary handoff returned; request="+gate.requestedAt+"; elapsed="+(when-gate.requestedAt)+" ms; gate="+gate.outcome()));}
 }
 void cancelReleaseWait(){final PreviewReleaseGate gate=releaseGate;if(gate==null)return;
  boolean waiting=gate.outcome().equals("WAITING");gate.abort("motion reversed, stale, disabled or locked");releaseGate=null;
  if(waiting)handler.post(()->{if(releaseGate==null)clear("Pre-release wait canceled");});
 }
 private synchronized void event(String text){trace=(trace+" | "+SystemClock.elapsedRealtime()+": "+text);if(trace.length()>6000)trace=trace.substring(trace.length()-6000);}
 volatile String status="Expansion idle";
 PreviewExpansion(int owner){this.owner=owner;thread.start();handler=new Handler(thread.getLooper());attachInterface(null,TOKEN);}
 void enabled(boolean value){boolean changed=enabled!=value;enabled=value;lastLease=SystemClock.elapsedRealtime();if(!value && changed)handler.post(()->clear("Expansion disabled"));if(value&&changed){PreviewReleaseGate old=releaseGate;if(old!=null)old.abort("new enabled session");releaseGate=null;}}
 protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(closed)throw new IllegalStateException("Expansion helper closed");
  data.enforceInterface(TOKEN);if(Binder.getCallingUid()!=owner)throw new SecurityException("Wrong caller");
  if(code==1){
   Bitmap bitmap=data.readTypedObject(Bitmap.CREATOR);Bitmap clean=data.readTypedObject(Bitmap.CREATOR);long captured=data.readLong();float seamOffset=data.dataAvail()>=4?RenderQuality.seam(data.readFloat()):.07f;
   boolean frosted=data.dataAvail()>=4&&data.readInt()!=0;boolean rightReady=data.dataAvail()>=4&&data.readInt()!=0;
   int rotation=data.dataAvail()>=4?data.readInt():0;
   if(bitmap==null || clean==null)throw new IllegalArgumentException("No prepared frame");
   // Plan D5: a post after close() lands on a quit looper and returns false; free the parcelled bitmaps then.
   if(!handler.post(()->{try{prepare(bitmap,clean,captured,seamOffset,frosted,rightReady,rotation);}catch(Exception e){clear("Expansion prepare failed: "+root(e));}finally{bitmap.recycle();clean.recycle();}})){bitmap.recycle();clean.recycle();}
  }else if(code==2){boolean endpoint=data.dataAvail()>=4&&data.readInt()!=0;handler.post(()->{if(layer!=null){pendingReady=true;event(endpoint?"Fully-open clear frame committed":"Fresh inner glass frame committed");if(start>0 && ready<0){ready=SystemClock.elapsedRealtime()-start;status=endpoint?"Fully open; expansion fading":"Inner glass committed; expansion fading";}}});}
  else if(code==3){handler.post(()->{completed=false;clear("Expansion reset");});}
  else throw new IllegalArgumentException("Unknown bridge operation");
  reply.writeNoException();reply.writeString(status);return true;
 }
 boolean holdBeforeRelease(){
  if(!enabled || closed)return true;
  long now=SystemClock.elapsedRealtime();PreviewReleaseGate current=releaseGate;
  if(current==null){
   final PreviewReleaseGate gate=new PreviewReleaseGate(now);releaseGate=gate;current=gate;
   handler.post(()->{
    if(releaseGate!=gate||!enabled||closed){gate.abort("inactive session");return;}
    if(!gate.maySubmit(SystemClock.elapsedRealtime())){event("Pre-release preparation skipped: queue missed deadline; request="+gate.requestedAt);return;}
    if(layer==null||start>0||!PreviewExpansionPolicy.fresh(stamp,SystemClock.elapsedRealtime())){gate.abort("no fresh prepared layer or hold already active");event("Pre-release "+gate.outcome()+"; request="+gate.requestedAt);return;}
    trace="";diagnosticStart=gate.requestedAt;lastSample=0;maxSampleGap=0;offSince=-1;missingSince=-1;lastMapping="";
    event("MEASURED SOFTWARE EVENTS ONLY: panel state is sampled; commit/draw is not photon visibility");
    event("Pre-release request="+gate.requestedAt+"; queue="+(SystemClock.elapsedRealtime()-gate.requestedAt)+" ms; prepared age="+(SystemClock.elapsedRealtime()-stamp)+" ms");
    start=SystemClock.elapsedRealtime();ready=-1;coverCommit=gate;
    handler.removeCallbacks(tick);tick.run();
   });
  }
  return current.mayRelease(now);
 }
 private static String root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getClass().getSimpleName()+": "+e.getMessage();}
 private Object service(String name,String stub)throws Exception{
  IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
  return Class.forName(stub).getMethod("asInterface",IBinder.class).invoke(null,b);
 }
 private void init()throws Exception{
  if(dm!=null)return;
  // DisplayManagerGlobal caches DisplayInfo and invalidates it on display changes (plan C4); the raw
  // IDisplayManager stub made two uncached system_server calls every 8 ms tick.
  dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
  displayInfo=dm.getClass().getMethod("getDisplayInfo",int.class);
  wm=service("window","android.view.IWindowManager$Stub");keyguard=Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked");
 }
 private int number(Object info,String field)throws Exception{return info.getClass().getField(field).getInt(info);}
 private String id(Object info)throws Exception{return (String)info.getClass().getField("uniqueId").get(info);}
 private boolean inner(Object info)throws Exception{
  int w=number(info,"logicalWidth"),h=number(info,"logicalHeight");return Math.min(w,h)/(float)Math.max(w,h)>.7f;
 }
 private void prepare(Bitmap bitmap,Bitmap clean,long captured,float seamOffset,boolean frosted,boolean rightReady,int rotation)throws Exception{
  long now=SystemClock.elapsedRealtime();
  if(!enabled || !PreviewExpansionPolicy.fresh(captured,now) || start>0)return;
  init();Object primary=displayInfo.invoke(dm,0),secondary=displayInfo.invoke(dm,1);
  if(primary==null || secondary==null || inner(primary) || !inner(secondary) || (boolean)keyguard.invoke(wm))return;
  if(completed)return; // Wait for the app to reset after leaving this cover session.
  sourceRotation=rotation;landscape=bitmap.getWidth()>bitmap.getHeight();
  innerId=id(secondary);stamp=captured;frostedLeft=frosted;rightPreviewReady=rightReady;
  Bitmap scaledLeft=Bitmap.createScaledBitmap(frosted?bitmap:clean,bitmap.getWidth(),bitmap.getHeight(),true);android.graphics.Matrix flip=new android.graphics.Matrix();flip.setScale(landscape?1f:-1f,1f);Bitmap reflected=Bitmap.createBitmap(scaledLeft,0,0,scaledLeft.getWidth(),scaledLeft.getHeight(),flip,true);Bitmap next=reflected.copy(Bitmap.Config.HARDWARE,false);if(reflected!=clean&&reflected!=bitmap)reflected.recycle();if(scaledLeft!=clean&&scaledLeft!=bitmap&&scaledLeft!=reflected)scaledLeft.recycle();HardwareBuffer nextBuffer=next.getHardwareBuffer();
  Bitmap nextClean=Bitmap.createScaledBitmap(clean,bitmap.getWidth(),bitmap.getHeight(),true).copy(Bitmap.Config.HARDWARE,false);HardwareBuffer nextCleanBuffer=nextClean.getHardwareBuffer();
  if(layer==null)layer=new SurfaceControl.Builder().setName("Duo clean right hold").setBufferSize(bitmap.getWidth(),bitmap.getHeight()).setOpaque(true).setHidden(true).build();
  if(holdBlur==null){try{SurfaceControl.Builder hb=new SurfaceControl.Builder().setName("Duo hold blur").setHidden(true);
   SurfaceControl.Builder.class.getMethod("setEffectLayer").invoke(hb);holdBlur=hb.build();
   try(SurfaceControl.Transaction ht=new SurfaceControl.Transaction()){RecordVisible.hide(ht,holdBlur);ht.apply();}
   holdBlurRadius=SurfaceControl.Transaction.class.getMethod("setBackgroundBlurRadius",SurfaceControl.class,int.class);}catch(Exception e){holdBlur=null;holdBlurRadius=null;}}
  holdRadius=holdBlurProp();unified=UnifiedRenderer.enabled();
  if(backdrop==null)backdrop=new SurfaceControl.Builder().setName("Duo reflected left handoff copy").setBufferSize(bitmap.getWidth(),bitmap.getHeight()).setOpaque(true).setHidden(true).build();
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   RecordVisible.hide(t,backdrop);
   t.setBuffer(backdrop,nextBuffer).setLayer(backdrop,Integer.MAX_VALUE-21).setAlpha(backdrop,1f);
   // Own surface only. Excluded from mirrored content and subsequent effect captures.
   RecordVisible.hide(t,layer);
   t.setBuffer(layer,nextCleanBuffer).setLayer(layer,Integer.MAX_VALUE-20).setAlpha(layer,1f).setVisibility(layer,false).apply();
  }
  if(buffer!=null)buffer.close();if(hardware!=null)hardware.recycle();
  if(cleanBuffer!=null)cleanBuffer.close();if(cleanHardware!=null)cleanHardware.recycle();
  hardware=next;buffer=nextBuffer;cleanHardware=nextClean;cleanBuffer=nextCleanBuffer;bw=bitmap.getWidth();bh=bitmap.getHeight();
  prepareSeam(bitmap,secondary,seamOffset);
  status="Reflected left hold prepared; clean right and unfold center blur ready";
  if(!polling){polling=true;handler.post(tick);}
 }
 private static float holdBlurProp(){return BlurTuning.max();}
 private void prepareSeam(Bitmap blurred,Object target,float fraction)throws Exception{
  if(fraction<=0){if(seam!=null)try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(seam,false).apply();}seamPixels=0;return;}
  if(landscape){prepareLandscapeSeam(blurred,target,fraction);return;}
  int w=number(target,"logicalWidth"),h=number(target,"logicalHeight");float[] layout=LiveMirrorLayout.preview(bw,bh,w,h);float fit=layout[0];
  int cropX=(int)layout[4],cropY=(int)layout[5],cropHeight=(int)(layout[7]-layout[5]);
  int strip=Math.min((int)(layout[6]-layout[4]),Math.max(1,(int)Math.ceil(w*fraction/fit)));int[] pixels=new int[strip*cropHeight];
  blurred.getPixels(pixels,0,strip,cropX,cropY,strip,cropHeight);
  for(int y=0;y<cropHeight;y++)for(int x=0;x<strip;x++){float f=RenderQuality.seamBlend(x*fit/w,fraction);int at=y*strip+x;pixels[at]=(pixels[at]&0x00ffffff)|((int)(255*f)<<24);}
  Bitmap soft=Bitmap.createBitmap(pixels,strip,cropHeight,Bitmap.Config.ARGB_8888);Bitmap next=soft.copy(Bitmap.Config.HARDWARE,false);soft.recycle();HardwareBuffer nextBuffer=next.getHardwareBuffer();
  if(seam==null)seam=new SurfaceControl.Builder().setName("Duo static center-edge blur").setBufferSize(strip,cropHeight).setHidden(true).build();
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   RecordVisible.hide(t,seam);
   t.setBuffer(seam,nextBuffer).setLayer(seam,Integer.MAX_VALUE-18).apply();
  }
  if(seamBuffer!=null)seamBuffer.close();if(seamHardware!=null)seamHardware.recycle();seamHardware=next;seamBuffer=nextBuffer;seamPixels=strip;
 }
 private void prepareLandscapeSeam(Bitmap blurred,Object target,float fraction)throws Exception{
  int dr=number(target,"rotation"),w=number(target,"logicalWidth"),h=number(target,"logicalHeight");
  float nw=dr%2==0?w:h,nh=dr%2==0?h:w,cw=sourceRotation%2==0?bw:bh,ch=sourceRotation%2==0?bh:bw;
  float scale=Math.min(nw/2f/cw,nh/ch);
  int[] pixels=new int[bw*bh];blurred.getPixels(pixels,0,bw,0,0,bw,bh);
  for(int y=0;y<bh;y++)for(int x=0;x<bw;x++){float nx=LandscapePreviewLayout.naturalX(x,y,bw,bh,sourceRotation);float blend=RenderQuality.seamBlend(nx*scale/nw,fraction);int at=y*bw+x;pixels[at]=(pixels[at]&0xffffff)|((int)(255*blend)<<24);}
  Bitmap soft=Bitmap.createBitmap(pixels,bw,bh,Bitmap.Config.ARGB_8888),next=soft.copy(Bitmap.Config.HARDWARE,false);soft.recycle();HardwareBuffer nextBuffer=next.getHardwareBuffer();
  if(seam==null)seam=new SurfaceControl.Builder().setName("Duo landscape hinge blur").setBufferSize(bw,bh).setHidden(true).build();
  try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
   SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,seam,true);
   t.setBuffer(seam,nextBuffer).setLayer(seam,Integer.MAX_VALUE-19).apply();
  }
  if(seamBuffer!=null)seamBuffer.close();if(seamHardware!=null)seamHardware.recycle();seamHardware=next;seamBuffer=nextBuffer;seamPixels=bw;
 }
 private void place(SurfaceControl.Transaction t,SurfaceControl sc,float[] m)throws Exception{
  SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class).invoke(t,sc,m[0],m[1],m[2],m[3]);
  t.setCrop(sc,new Rect(0,0,bw,bh)).setPosition(sc,m[4],m[5]);
 }
 private final Runnable tick=new Runnable(){public void run(){
  try{
   long now=SystemClock.elapsedRealtime();
   if(keyguardAt<0||now-keyguardAt>=200){keyguardLocked=(boolean)keyguard.invoke(wm);keyguardAt=now;}  // C4
   if(!enabled || now-lastLease>1000 || layer==null || keyguardLocked){clear("Expansion paused");return;}
   if(start==0 && !PreviewExpansionPolicy.fresh(stamp,now)){clear("Expansion waiting for fresh cover frame");return;}
   Object primary=displayInfo.invoke(dm,0),secondary=displayInfo.invoke(dm,1);
   Object target=primary!=null && innerId.equals(id(primary))?primary:secondary!=null && innerId.equals(id(secondary))?secondary:null;
   sample(primary,secondary,target,now);
   if(target==null){if(start>0 && now-start>1500){clear("Expansion panel disappeared");return;}handler.postDelayed(this,8);return;}
   boolean switched=primary!=null && innerId.equals(id(primary));
   boolean coverOff=primary!=null && !inner(primary) && number(primary,"state")!=2;
   if(start==0 && (switched || coverOff)){
    if(!PreviewExpansionPolicy.fresh(stamp,now)){clear("Expansion skipped: stale prepared frame");return;}
    start=now;ready=pendingReady?0:-1;status="Holding the existing two-column layout during handoff";
    if(BuildConfig.DIAGNOSTICS)android.util.Log.i("DuoHold","hold start readyAlready="+pendingReady+" blur="+Math.round(holdRadius));
   }
   long elapsed=start==0?0:now-start;
   float alpha=start==0?1f:PreviewExpansionPolicy.opacity(elapsed,ready,unified&&DeviceCompatibility.isFold7(android.os.Build.MODEL)?0:120);
   if(alpha<=0){if(BuildConfig.DIAGNOSTICS)android.util.Log.i("DuoHold","hold end elapsed="+elapsed+" readyAt="+ready);completed=true;clear("Prepared layout handed to inner content");return;}
   int stack=number(target,"layerStack"),panelState=number(target,"state");
   if(stack!=lastStack || panelState!=lastPanelState){lastStack=stack;lastPanelState=panelState;event("Inner stack="+stack+" state="+panelState);}
   int w=number(target,"logicalWidth"),h=number(target,"logicalHeight");
   // Unified Fold 7 (plan B3/B4): same right-half layout and uniform scale as the live mirror and the strip, so the
   // pane boundary sits at w/2 before the switch, during this hold and after it.
   // Both panels upright, like SecondaryShade: a rotated panel keeps the fitted layout the preview used (review on PR #18).
   boolean half=!landscape&&unified&&DeviceCompatibility.isFold7(android.os.Build.MODEL)&&(primary==null||number(primary,"rotation")==0)&&(secondary==null||number(secondary,"rotation")==0);
   float[] layout=landscape?new float[]{0,0,0,0,0,0,bw,bh}:LiveMirrorLayout.preview(bw,bh,w,h);float fit=half?LiveMirrorLayout.rightHalf(bw,bh,w,h)[0]:layout[0];
   float leftWidth=half||landscape?w/2f:Math.max(0f,w-bw*fit);
   float holdTop=(h-bh*fit)/2f;
   Rect crop=new Rect((int)layout[4],(int)layout[5],(int)layout[6],(int)layout[7]);
   if(coverCommit!=null&&!coverCommit.maySubmit(SystemClock.elapsedRealtime())){event("Replacement not submitted: gate deadline expired during preparation; request="+coverCommit.requestedAt);coverCommit=null;start=0;ready=-1;handler.postDelayed(this,8);return;}
   try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
    SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,layer,number(target,"layerStack"));
    if(landscape){
     int dr=number(target,"rotation");
     float[] clean=LandscapePreviewLayout.transform(bw,bh,sourceRotation,w,h,dr,false),reflection=LandscapePreviewLayout.transform(bw,bh,sourceRotation,w,h,dr,true);
     String geometry="source="+bw+"x"+bh+" r="+sourceRotation+" destination="+w+"x"+h+" r="+dr+" clean="+java.util.Arrays.toString(clean);
     if(!geometry.equals(lastGeometry)){lastGeometry=geometry;event("Landscape hinge layout "+geometry);}
     place(t,layer,clean);t.setAlpha(layer,alpha).setVisibility(layer,start>0);
     SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,backdrop,stack);
     place(t,backdrop,reflection);t.setAlpha(backdrop,alpha).setVisibility(backdrop,start>0||(frostedLeft&&rightPreviewReady));
     if(seam!=null){SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,seam,stack);place(t,seam,clean);t.setAlpha(seam,alpha*seamMotion).setVisibility(seam,seamPixels>0);}
    }else{
    SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class).invoke(t,layer,fit,0f,0f,layout[1]);
    SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,backdrop,number(target,"layerStack"));
    SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class).invoke(t,backdrop,half||bw>bh?fit:leftWidth/bw,0f,0f,half?fit:(bw>bh?layout[1]:h/(float)bh));
    // The left copy is visible BEFORE handoff. Never hide either layer merely because
    // Android reports a transient OFF state during the physical panel remap.
    boolean record=RecordVisible.enabled();
    if(record!=lastRecord){lastRecord=record;RecordVisible.hide(t,backdrop);RecordVisible.hide(t,layer);if(seam!=null)RecordVisible.hide(t,seam);if(holdBlur!=null)RecordVisible.hide(t,holdBlur);}
    t.setCrop(backdrop,crop).setPosition(backdrop,bw>bh?layout[2]-leftWidth:0,half?holdTop:(bw>bh?layout[3]:0)).setAlpha(backdrop,alpha).setVisibility(backdrop,(start>0 || (frostedLeft && rightPreviewReady && !unified)) && leftWidth>0);
    t.setCrop(layer,crop).setPosition(layer,half?leftWidth:layout[2],half?holdTop:layout[3]).setAlpha(layer,alpha).setVisibility(layer,start>0);
    if(holdBlur!=null&&holdBlurRadius!=null){
     // Same compositor blur as the live preview, so the frozen right frame continues its look.
     SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,holdBlur,stack);
     float top=(h-bh*fit)/2f;
     t.setLayer(holdBlur,Integer.MAX_VALUE-19).setCrop(holdBlur,new android.graphics.Rect(Math.round(leftWidth),Math.round(top),Math.round(leftWidth+bw*fit),Math.round(top+bh*fit)))
      .setPosition(holdBlur,0,0).setAlpha(holdBlur,alpha).setVisibility(holdBlur,start>0&&half);  // unified Fold 7 half-pane hold only
     holdBlurRadius.invoke(t,holdBlur,Math.round(holdRadius));
    }
    if(seam!=null){
     SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,seam,stack);
     SurfaceControl.Transaction.class.getMethod("setMatrix",SurfaceControl.class,float.class,float.class,float.class,float.class).invoke(t,seam,fit,0f,0f,half?fit:layout[1]);
     t.setPosition(seam,leftWidth,bw>bh?0:(h-bh*fit)/2f).setAlpha(seam,alpha*seamMotion).setVisibility(seam,seamPixels>0 && (start>0 || !unified));
    }
    }
    if(coverCommit!=null){final PreviewReleaseGate fence=coverCommit;coverCommit=null;
     final long submitted=SystemClock.elapsedRealtime();
     t.addTransactionCommittedListener(Runnable::run,()->{long committedAt=SystemClock.elapsedRealtime();boolean timely=fence.commit(committedAt);diagnosticEvent("Replacement transaction committed in "+(committedAt-submitted)+" ms; request="+fence.requestedAt+"; timely="+timely+"; gate="+fence.outcome());});
     event("Replacement submitted; request="+fence.requestedAt+"; age="+(submitted-fence.requestedAt)+" ms");
    }
    t.apply();
   }
   handler.postDelayed(this,8);
  }catch(Exception e){clear("Expansion failed: "+root(e));}
 }};
 private void clear(String message){
  if(start>0){event(message+"; hold duration="+(SystemClock.elapsedRealtime()-start)+" ms; maximum state-sampling gap="+maxSampleGap+" ms");
   if(offSince>=0)event("OFF interval still open at cleanup");if(missingSince>=0)event("Missing mapping interval still open at cleanup");}
  diagnosticStart=0;if(coverCommit!=null){coverCommit.abort(message);coverCommit=null;}
  if(releaseGate!=null)releaseGate.abort(message);
  handler.removeCallbacks(tick);polling=false;start=0;ready=-1;pendingReady=false;keyguardAt=-1;
  if(layer!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(layer,false).reparent(layer,null).apply();}catch(Exception ignored){}layer.release();layer=null;}
  if(backdrop!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(backdrop,false).reparent(backdrop,null).apply();}catch(Exception ignored){}backdrop.release();backdrop=null;}
  if(seam!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(seam,false).reparent(seam,null).apply();}catch(Exception ignored){}seam.release();seam=null;}
  if(holdBlur!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(holdBlur,false).reparent(holdBlur,null).apply();}catch(Exception ignored){}holdBlur.release();holdBlur=null;}
  if(seamBuffer!=null){seamBuffer.close();seamBuffer=null;}if(seamHardware!=null){seamHardware.recycle();seamHardware=null;}seamPixels=0;
  if(cleanBuffer!=null){cleanBuffer.close();cleanBuffer=null;}if(cleanHardware!=null){cleanHardware.recycle();cleanHardware=null;}
  if(buffer!=null){buffer.close();buffer=null;}if(hardware!=null){hardware.recycle();hardware=null;}status=message;
  if(!enabled)completed=false;
 }
 void close(){closed=true;enabled=false;handler.post(()->{clear("Expansion stopped");thread.quitSafely();});}
}
