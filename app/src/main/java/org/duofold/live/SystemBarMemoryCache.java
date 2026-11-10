package org.duofold.live;
import java.util.*;
/** Small process-only cache. Values are immutable and may still be in GPU work. */
final class SystemBarMemoryCache<T> {
 private static final class Entry<T>{final T value;final long stamp,bytes;Entry(T v,long s,long b){value=v;stamp=s;bytes=b;}}
 private final LinkedHashMap<String,Entry<T>> entries=new LinkedHashMap<>();
 private final int maximum;private final long byteLimit,ttl;
 SystemBarMemoryCache(int maximum,long byteLimit,long ttl){this.maximum=maximum;this.byteLimit=byteLimit;this.ttl=ttl;}
 void prune(long now){entries.entrySet().removeIf(e->!SystemBarExperimentPolicy.fresh(now,e.getValue().stamp,ttl));}
 void put(String key,T value,long stamp,long bytes,long now){
  prune(now);if(bytes<0||bytes>byteLimit||!SystemBarExperimentPolicy.fresh(now,stamp,ttl))return;
  entries.remove(key);entries.put(key,new Entry<>(value,stamp,bytes));
  while(entries.size()>maximum||bytes()>byteLimit)entries.remove(entries.keySet().iterator().next());
 }
 T get(String key,long now){prune(now);Entry<T> entry=entries.get(key);return entry==null?null:entry.value;}
 long bytes(){long result=0;for(Entry<T> e:entries.values())result+=e.bytes;return result;}
 void clear(){entries.clear();}
}
