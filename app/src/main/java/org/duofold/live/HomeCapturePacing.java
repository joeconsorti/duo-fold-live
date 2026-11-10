package org.duofold.live;

import java.util.HashMap;

/** Home screenshot pacing, charged only when the public API is called.
 * Android limits window screenshots per accessibility window, not per panel.
 * Use uptime (the API's clock) and keep margin above its 333 ms interval. */
final class HomeCapturePacing {
 static final long INTERVAL_MS=350;
 private final HashMap<Integer,Long> deadlines=new HashMap<>();
 long delayMs(int window,long now){return Math.max(0,deadlines.getOrDefault(window,0L)-now);}
 void submitted(int window,long now){
  deadlines.entrySet().removeIf(e->e.getValue()<=now);
  deadlines.put(window,now+INTERVAL_MS);
 }
 void rateLimited(int window,long now){submitted(window,now);}
}
