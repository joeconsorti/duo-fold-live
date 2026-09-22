package org.duofold.live;
import android.os.*;
import java.util.ArrayDeque;
/** A single native-task excursion per timed hold; never requests a device state. */
final class NativeContinuityProbe {
 private TaskDisplayRouter router;
 private boolean active,attempted,verified,finished;
 private long requestedAt;
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
   if(!active){active=true;attempted=false;verified=false;finished=false;router=null;cover=null;inner=null;events.clear();note("Fixed mapping hold started; native route waits for 98 degrees");}
   if(finished)return;
   if(!attempted&&angle>=98){
    attempted=true;requestedAt=SystemClock.elapsedRealtime();
    cover=panel(0);inner=panel(1);
    router=new TaskDisplayRouter();note(router.beginProbe());
   }
   if(attempted&&router!=null){
    if(!sameMapping())throw new IllegalStateException("Physical mapping changed during native task test");
    if(angle<=94){finish(interactive);return;}
    if(!verified){
     if(router.probeVerified()){verified=true;note("Task on display 1 and inner focus verified; cover mirror removed. Check native size, touch and navigation on phone.");}
     else if(SystemClock.elapsedRealtime()-requestedAt>=1500)throw new IllegalStateException("Native task/focus not verified within 1500 ms");
    }
   }
  }catch(Exception e){note("Native route failed: "+error(e));finish(interactive);}
  finally{Binder.restoreCallingIdentity(identity);}
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
