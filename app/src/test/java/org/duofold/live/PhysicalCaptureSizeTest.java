package org.duofold.live;
import org.junit.Test;
import static org.junit.Assert.*;
public class PhysicalCaptureSizeTest {
 public static class Builder {
  int width,height;
  public Builder setSize(int w,int h){width=w;height=h;return this;}
  public Builder setFrameScale(float scale){throw new AssertionError("Display sizing must use setSize");}
 }
 @Test public void boundsBothSamsungPanelsAndRotations()throws Exception{
  for(int[] size:new int[][]{{2448,1848},{1248,1972},{1848,2448},{1972,1248}}){
   Builder b=new Builder();PhysicalCaptureSize.configure(b,size[0],size[1]);
   assertEquals(320,Math.max(b.width,b.height));
   assertEquals(size[0]/(double)size[1],b.width/(double)b.height,.005);
   PhysicalCaptureSize.requireBounded(b.width,b.height);
  }
 }
 @Test public void doesNotEnlargeSmallCapture()throws Exception{
  Builder b=new Builder();PhysicalCaptureSize.configure(b,100,50);assertEquals(100,b.width);assertEquals(50,b.height);
 }
 @Test public void rejectsIgnoredOemSizingBeforeReadback(){
  for(int[] size:new int[][]{{2448,1848},{1248,1972},{321,200},{0,320}}){
   try{PhysicalCaptureSize.requireBounded(size[0],size[1]);fail("Unbounded pixels accepted");}catch(IllegalStateException expected){}
  }
 }
}
