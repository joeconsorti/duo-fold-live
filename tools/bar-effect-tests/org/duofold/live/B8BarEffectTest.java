package org.duofold.live;
import android.graphics.*;import android.os.*;import java.util.*;import java.util.concurrent.*;import kotlin.Unit;
public class B8BarEffectTest {
 static int checks;static int changed;
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static GlassFrame frame(String panel,int color,long stamp,boolean bars){
  Bitmap image=new Bitmap(120,200);Arrays.fill(image.pixels,color);
  if(bars)Arrays.fill(image.pixels,120*10,image.pixels.length,0xff7f7f7f);
  Bundle status=new Bundle();status.putString("kind","status");status.putIntArray("bounds",new int[]{0,0,1200,100});
  return new GlassFrame(image,1200,2000,stamp,List.of(),panel,0,0,"test",0,null,null,bars?List.of(status):List.of(),0L,true,1f);
 }
 static B8SystemBars manager(){var bars=new B8SystemBars(1,"local:1",2,()->{changed++;return Unit.INSTANCE;});bars.setWorkAllowed(true);return bars;}
 static void finish()throws Exception{Runnable r=Handler.queue.poll(2,TimeUnit.SECONDS);check(r!=null,"bounded worker delivery");r.run();}
 static GlassFrame effect(B8SystemBars bars,GlassFrame base){return bars.effectFrame(base,"local:2",1200,2000,0);}
 public static void main(String[] args)throws Exception{
  long now=SystemClock.elapsedRealtime();var bars=manager();var base=frame("local:2",0xff00ff00,now,false);var capture=frame("local:2",0xff0000ff,now+1,true);
  int acceptAllocations=Bitmap.all.size();bars.accept(capture);check(Bitmap.all.size()==acceptAllocations,"guarded frame callback does not crop/copy pixels on main");int before=Bitmap.all.size();for(int n=0;n<100;n++)check(effect(bars,base)==base,"pending composition preserves original readiness frame");finish();
  var baked=effect(bars,base);check(baked!=base,"fresh destination bars produce cosmetic texture");check(baked.getStamp()==base.getStamp()&&baked.getPhysicalId().equals(base.getPhysicalId()),"original capture provenance retained");
  check(baked.getBitmap().width==120&&baked.getBitmap().height==200,"no native-resolution expansion");check(baked.getBitmap().pixels[2*120+60]==0xff0000ff,"top crop maps physical coordinates to capture raster");check(baked.getBitmap().pixels[100*120+60]==0xff00ff00,"new capture never replaces app center");check(base.getBitmap().pixels[2*120+60]==0xff00ff00,"original retained frame stays untouched");
  check(baked.getLevels().size()==7&&baked.getLevels().get(0).pixels[60+30]==0xff0000ff,"glass pyramid contains composed bars");
  int allocations=Bitmap.all.size();for(int n=0;n<1000;n++)check(effect(bars,base)==baked,"repeated animation frames reuse same texture");check(Bitmap.all.size()==allocations&&allocations-before==8,"one image and seven mips, no per-frame allocations");
  var cover=frame("local:1",0xffff0000,now+2,true);bars.accept(cover);check(effect(bars,cover)==cover,"cross-panel source cannot receive destination bars");
  check(bars.effectFrame(base,"local:2",1200,2000,1)==base,"rotation mismatch cannot use snapshots");check(bars.effectFrame(base,"local:2",2000,1200,0)==base,"shape mismatch cannot use snapshots");bars.close();
  var close=manager();close.accept(capture);before=Bitmap.all.size();effect(close,base);close.close();finish();check(Bitmap.all.subList(before,Bitmap.all.size()).stream().allMatch(b->b.recycled),"unpublished closed-session images recycled");check(!base.getBitmap().recycled&&!capture.getBitmap().recycled,"borrowed app images survive close");
  var stale=manager();stale.accept(capture);before=Bitmap.all.size();effect(stale,base);var newer=frame("local:2",0xffff0000,now+3,true);stale.accept(newer);finish();
  var obsolete=Bitmap.all.subList(before,Bitmap.all.size()).stream().filter(b->b!=newer.getBitmap()&&b.width==120&&b.height==200).toList();
  check(obsolete.size()==1&&obsolete.get(0).recycled,"obsolete composite discarded without recycling replacement capture");check(!newer.getBitmap().recycled,"newer snapshot remains usable");
  effect(stale,base);finish();check(effect(stale,base).getBitmap().pixels[2*120+60]==0xffff0000,"latest snapshot wins after obsolete job");stale.close();
  var reused=manager();reused.accept(capture);before=Bitmap.all.size();check(effect(reused,capture)==capture,"already-baked guarded capture needs no composition");check(Bitmap.all.size()==before,"reuse allocates nothing");reused.close();
  var priority=manager();priority.accept(capture);priority.setWorkAllowed(false);before=Bitmap.all.size();check(effect(priority,base)==base&&Bitmap.all.size()==before,"critical app work cannot start cosmetic composition");
  priority.setWorkAllowed(true);effect(priority,base);priority.setWorkAllowed(false);finish();check(Bitmap.all.subList(before,Bitmap.all.size()).stream().allMatch(b->b.recycled),"in-flight optional composition revoked by handoff");
  priority.setWorkAllowed(true);effect(priority,base);finish();var kept=effect(priority,base);priority.setWorkAllowed(false);check(effect(priority,base)==kept,"critical work retains previously published texture without a visual reset");priority.close();
  var scaled=manager();scaled.registerPanel("local:2",1200,2000,0,50);scaled.accept(capture);effect(scaled,base);finish();var small=effect(scaled,base);
  check(small.getBitmap().width==30&&small.getBitmap().height==50,"optional raster capped to actual session output");
  check(small.getBitmap().pixels[15]==0xff0000ff&&small.getBitmap().pixels[25*30+15]==0xff00ff00,"scaled composition preserves bar placement and app center");scaled.close();
  System.out.println("B8BarEffectTest: "+checks+" production composition/reuse/identity/cleanup checks passed with CPU image oracle; no handset/GPU proof");System.exit(0);
 }
}
