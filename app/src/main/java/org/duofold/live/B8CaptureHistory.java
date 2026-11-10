package org.duofold.live;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded metadata only; no screenshots, dumps, timers or additional capture work. */
final class B8CaptureHistory {
 private final LinkedHashMap<Long,ArrayDeque<String>> sessions=new LinkedHashMap<>();
 synchronized void add(long session,long at,String event){
  if(session<=0)return;
  ArrayDeque<String> entries=sessions.get(session);
  if(entries==null){
   entries=new ArrayDeque<>();sessions.put(session,entries);
   while(sessions.size()>8)sessions.remove(sessions.keySet().iterator().next());
  }
  String bounded=event.replace('\n',' ');
  if(bounded.length()>1200)bounded=bounded.substring(0,1200)+" [truncated]";
  entries.addLast("+"+Math.max(0,at-session)+"ms "+bounded);
  while(entries.size()>12)entries.removeFirst();
 }
 synchronized String report(){
  StringBuilder out=new StringBuilder("Recent app captures (metadata only; not pixel/optical proof):");
  for(Map.Entry<Long,ArrayDeque<String>> entry:sessions.entrySet()){
   out.append("\nsession=").append(entry.getKey());
   for(String event:entry.getValue())out.append("\n ").append(event);
  }
  return out.toString();
 }
}
