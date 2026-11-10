package org.duofold.live.wallpaperlayer;
import java.util.*;
/** Empty teardown samples must not erase recent Home evidence or certify readiness. */
final class WakeEligibilityPolicy {
 static void remember(Map<Integer,Long> recent,Set<Integer> home,Set<Integer> other,long now){
  for(int id:other)if(!home.contains(id))recent.remove(id);
  for(int id:home)recent.put(id,now);
 }
 static Set<Integer> sleeping(Map<Integer,Long> recent,Set<Integer> interrupted,long now){
  Set<Integer> result=new HashSet<>(interrupted);
  for(Map.Entry<Integer,Long> e:recent.entrySet())if(now>=e.getValue()&&now-e.getValue()<1500)result.add(e.getKey());
  return result;
 }
}
