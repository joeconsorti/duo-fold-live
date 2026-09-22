package org.duofold.live;
import android.os.*;
import java.util.ArrayDeque;
/** A single native-task excursion per timed hold; never requests a device state. */
final class NativeContinuityProbe {
 private TaskDisplayRouter router;
 private boolean active,attempted,verified,finished,repaired,focusReported;
 private long requestedAt;private boolean focusSnapshot;
 private String cover,inner;
 private final ArrayDeque<String> events=new ArrayDeque<>();
 boolean nativeVisible(){return active&&verified&&!finished;}
 String report(){return String.join("\n",events);}
 private void note(String text){if(events.size()==12)events.removeFirst();events.addLast(SystemClock.elapsedRealtime()+" "+text);}
 private String panel(int id)throws Exception{
  Object dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
  Object info=dm.getClass().getMethod("getDisplayInfo",int.class).invoke(dm,id);
  if(info==null)throw new IllegalStateException("Display "+id+" missing");
  return String.valueOf(info.getClass().getField("uniqueId").get(info));
 }
 private boolean sameMapping()throws Exception{return cover!=null&&cover.equals(panel(0))&&inner.equals(panel(1));}
 private String error(Exception e){Throwable t=e;while(t.getCause()!=null)t=t.getCause();return t.getClass().getSimpleName()+": "+t.getMessage();}
 void update(boolean holding,float angle,boolean interactive){
  long identity=Binder.clearCallingIdentity();
  try{
   if(!holding){if(active)finish(interactive);active=false;return;}
   if(!active){active=true;attempted=false;verified=false;finished=false;repaired=false;focusReported=false;focusSnapshot=false;router=null;cover=null;inner=null;events.clear();note("Fixed mapping hold started; native route waits for 98 degrees");}
   if(finished)return;
   if(!attempted&&angle>=98){
    attempted=true;requestedAt=SystemClock.elapsedRealtime();
    cover=panel(0);inner=panel(1);
    router=new TaskDisplayRouter();note(router.beginProbe());note(router.probeSnapshot());requestedAt=SystemClock.elapsedRealtime();
   }
   if(attempted&&router!=null){
    if(!sameMapping())throw new IllegalStateException("Physical mapping changed during native task test");
    if(angle<=94){finish(interactive);return;}
    boolean placed=router.probePlaced();
    if(placed&&!verified){verified=true;note("Task placement on inner verified; removing cover mirror. Global focus checked separately; no timed rollback of a placed task.");note(router.probeSnapshot());}
    if(!focusReported&&router.probeVerified()){focusReported=true;note("Global focus verified on inner task");}
    if(!repaired&&!focusReported&&SystemClock.elapsedRealtime()-requestedAt>=350){
     repaired=true;note(router.probeSnapshot());
     try{note(router.repairProbe());}catch(Exception e){note("One-time placement/focus repair failed: "+error(e));}
    }
    if(repaired&&!focusSnapshot&&SystemClock.elapsedRealtime()-requestedAt>=1000){
     focusSnapshot=true;note("After focus request: "+router.probeSnapshot());note(displayCapabilities());
    }
    if(!placed&&!verified&&SystemClock.elapsedRealtime()-requestedAt>=2000){
     note(router.probeSnapshot());throw new IllegalStateException("Task placement not verified within 2000 ms");
    }

   }
  }catch(Exception e){note("Native route failed: "+error(e));if(router!=null)try{note(router.probeSnapshot());}catch(Exception ignored){}finish(interactive);}
  finally{Binder.restoreCallingIdentity(identity);}
 }
 private String displayCapabilities(){
  StringBuilder out=new StringBuilder("Inner display capabilities: ");
  try{
   Object dm=Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null);
   Object info=dm.getClass().getMethod("getDisplayInfo",int.class).invoke(dm,1);
   out.append("flags=0x").append(Integer.toHexString(info.getClass().getField("flags").getInt(info)));
   IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
   Object wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
   Class<?> api=Class.forName("android.view.IWindowManager");
   for(String name:new String[]{"hasNavigationBar","shouldShowSystemDecors","getDisplayImePolicy"}){
    try{out.append("; ").append(name).append('=').append(api.getMethod(name,int.class).invoke(wm,1));}
    catch(Exception e){out.append("; ").append(name).append(": ").append(error(e));}
   }
  }catch(Exception e){out.append(error(e));}
  return out.toString();
 }
 private void finish(boolean interactive){
  if(finished)return;
  finished=true;verified=false;
  if(router==null)return;
  try{
   if(sameMapping()){router.endProbe(interactive);note("Owned task return requested; fixed mapping retained until test exit");}
   else note("Task return skipped: mapping already changed; no task moved using stale display IDs");
  }catch(Exception e){note("Task return failed: "+error(e));}
  finally{router=null;}
 }
}
