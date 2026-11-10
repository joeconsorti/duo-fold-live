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
 static void requirePanelExtent(int panelWidth,int panelHeight,int captureWidth,int captureHeight){
  // The logical slot can keep the other panel's extent across both capture guard reads.
  // Compare with the independently established physical renderer, allowing a quarter turn.
  if(panelWidth<=0||panelHeight<=0||Math.min(panelWidth,panelHeight)!=Math.min(captureWidth,captureHeight)
      ||Math.max(panelWidth,panelHeight)!=Math.max(captureWidth,captureHeight))
   throw new IllegalStateException("Panel geometry unsettled: expected "+panelWidth+"x"+panelHeight+", observed "+captureWidth+"x"+captureHeight);
 }
 static void requireBounded(int width,int height){
  // Reject an OEM that ignores sizing before CPU readback or Binder transfer.
  if(width<=0||height<=0||width>MAX||height>MAX)
   throw new IllegalStateException("Physical capture ignored bounded output: "+width+"x"+height);
 }
}
