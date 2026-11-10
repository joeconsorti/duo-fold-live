package org.duofold.live;
/** At most one recovery lease until physical base CLOSED has actually been left. */
final class FoldWakeEpisode {
 private int previous=-1;private boolean available;
 void observe(int base){
  if(base<0)return;
  if(base==0)available=false;
  else if(previous<=0)available=true;
  previous=base;
 }
 boolean take(){if(!available)return false;available=false;return true;}
}
