package org.duofold.live;
/** Wait only for the incoming physical panel and its destination dimensions, with a bounded recovery deadline. */
final class EndpointReturnPolicy {
 static boolean ready(String expected,String actual,int state,int expectedWidth,int expectedHeight,int width,int height){
  return expected!=null&&!expected.isEmpty()&&expected.equals(actual)&&state==2
    &&expectedWidth>0&&expectedHeight>0&&width>0&&height>0
    &&Math.min(expectedWidth,expectedHeight)==Math.min(width,height)
    &&Math.max(expectedWidth,expectedHeight)==Math.max(width,height);
 }
 static boolean shouldWait(long now,long deadline,boolean allowed,boolean mappedAndOn){return allowed&&!mappedAndOn&&now<deadline;}
}
