package org.duofold.live;

/** Independent moving/post-cleanup budgets keep long motion from eating the tail. */
final class FoldImageBudget {
 static final long PERIOD=125, TAIL_PERIOD=500, TAIL=10000, MAX_DURATION=42000;
 static final long MOVING_BYTES=12*1024*1024, TAIL_BYTES=3*1024*1024;
 private int moving,tail;private long movingBytes,tailBytes;
 boolean available(boolean ended){return ended?tail<20&&tailBytes<TAIL_BYTES:moving<240&&movingBytes<MOVING_BYTES;}
 void attempt(boolean ended){if(ended)tail++;else moving++;}
 void saved(boolean ended,long bytes){if(ended)tailBytes+=Math.max(0,bytes);else movingBytes+=Math.max(0,bytes);}
 static boolean inner(int w,int h){return w>0&&h>0&&Math.min(w,h)/(float)Math.max(w,h)>.7f;}
}
