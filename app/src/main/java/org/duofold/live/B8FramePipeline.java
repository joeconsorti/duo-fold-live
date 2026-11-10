package org.duofold.live;

import java.util.LinkedHashMap;

/** Main-thread pacing only. Latch permits the next draw; completion supplies
 * presentation evidence; neither event authorizes reuse of a GPU buffer. */
final class B8FramePipeline {
 private final LinkedHashMap<Long,Long> outstanding=new LinkedHashMap<>();
 private long serial,drawing,lastCompleted;
 private int peak;
 boolean canBegin(){return drawing==0&&outstanding.size()<2;}
 long begin(long now){
  if(!canBegin())throw new IllegalStateException("Frame pipeline full");
  drawing=++serial;outstanding.put(drawing,now);peak=Math.max(peak,outstanding.size());return drawing;
 }
 void latched(long ticket){if(drawing==ticket)drawing=0;}
 boolean complete(long ticket){
  if(outstanding.remove(ticket)==null)return false;
  latched(ticket);
  if(ticket<=lastCompleted)return false;
  lastCompleted=ticket;return true;
 }
 void discard(long ticket){outstanding.remove(ticket);latched(ticket);}
 boolean contains(long ticket){return outstanding.containsKey(ticket);}
 boolean pending(){return !outstanding.isEmpty();}
 long oldestAge(long now){return outstanding.isEmpty()?0:Math.max(0,now-outstanding.values().iterator().next());}
 String report(){return "inFlight="+outstanding.size()+" peakInFlight="+peak+" limit=2; latch-paced, release-fence buffer reuse";}
}
