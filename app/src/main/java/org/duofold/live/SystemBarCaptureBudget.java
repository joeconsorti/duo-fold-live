package org.duofold.live;
import java.util.*;
/** Optional work cannot accumulate or repeatedly delay primary handoff checks. */
final class SystemBarCaptureBudget {
 private final Map<String,Integer> attempts=new HashMap<>();
 private final Map<String,Long> retryAt=new HashMap<>();
 private boolean closed,busy;
 private final int maximum;private final long interval;
 SystemBarCaptureBudget(){this(2,150);}
 SystemBarCaptureBudget(int maximum,long interval){this.maximum=maximum;this.interval=interval;}
 boolean available(String key,long now){return !closed&&!busy&&attempts.getOrDefault(key,0)<maximum&&now>=retryAt.getOrDefault(key,0L);}
 boolean start(String key,long now){if(!available(key,now))return false;busy=true;attempts.put(key,count(key)+1);return true;}
 boolean finish(String key,long now,boolean accepted){busy=false;retryAt.put(key,now+interval);return !closed;}
 int count(String key){return attempts.getOrDefault(key,0);}
 void close(){closed=true;}
}
