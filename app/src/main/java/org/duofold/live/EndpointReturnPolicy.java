package org.duofold.live;
/** Wait only for the incoming physical panel, with a bounded recovery deadline. */
final class EndpointReturnPolicy {
 static boolean shouldWait(long now,long deadline,boolean allowed,boolean mappedAndOn){return allowed&&!mappedAndOn&&now<deadline;}
}
