package org.duofold.live;
import java.util.*;
/** One initial image and one settling refresh per window/geometry revision.
 * Later discovery checks need metadata only until native geometry changes. */
final class SystemBarPixelRefresh {
 private final Map<String,String> signatures=new HashMap<>();
 private final Map<String,Integer> counts=new HashMap<>();
 private final int images;
 SystemBarPixelRefresh(){this(2);}
 SystemBarPixelRefresh(int images){this.images=images;}
 String known(String physical){return counts.getOrDefault(physical,0)>=images?signatures.get(physical):"";}
 void pixels(String physical,String signature){
  if(signature==null||signature.isEmpty())return;
  int n=signature.equals(signatures.get(physical))?counts.getOrDefault(physical,0)+1:1;
  signatures.put(physical,signature);counts.put(physical,n);
 }
}
