package org.duofold.live;

/** DisplayCaptureArgs uses explicit output size, unlike layer capture's frame scale. */
final class PhysicalCaptureSize {
 static final int MAX=320;
 static void configure(Object builder,int width,int height)throws Exception{
  if(width<=0||height<=0)throw new IllegalArgumentException("Invalid physical dimensions");
  float scale=Math.min(1f,MAX/(float)Math.max(width,height));
  builder.getClass().getMethod("setSize",int.class,int.class).invoke(builder,
    Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)));
 }
 static void requireBounded(int width,int height){
  // Reject an OEM that ignores sizing before CPU readback or Binder transfer.
  if(width<=0||height<=0||width>MAX||height>MAX)
   throw new IllegalStateException("Physical capture ignored bounded output: "+width+"x"+height);
 }
}
