package org.duofold.live;
final class GlassFramePolicy {
 /** Render age limit for a live glass frame. */
 static final long MAX_AGE_MS=350;
 /** Fold 7: one failed capture (panel step, 250 ms timeout) must not drop the glass to its empty fallback. */
 static final long KEEP_ON_FAILURE_MS=1500;
 /** Retry delay after a failed capture while the previous frame is still kept. */
 static final long RETRY_WITH_FRAME_MS=32;
 static boolean usable(long stamp,long now,int width,int height,int currentWidth,int currentHeight){
  return usable(stamp,now,width,height,currentWidth,currentHeight,MAX_AGE_MS);
 }
 static boolean usable(long stamp,long now,int width,int height,int currentWidth,int currentHeight,long maxAge){
  long age=now-stamp;
  return age>=0 && age<=maxAge && width>0 && height>0 && width==currentWidth && height==currentHeight;
 }
 /** Age a rendered (not readiness-gating) glass frame may reach. Panel switches clear frames explicitly. */
 static long maxAgeFor(String model){return DeviceCompatibility.isFold7(model)?KEEP_ON_FAILURE_MS:MAX_AGE_MS;}
 /**
  * On a failed capture ("Panel changed during capture", screen off, a capture timeout) keep the last good frame
  * while it is still renderable instead of clearing it at once, and retry quickly. Never extends the render age:
  * a kept frame stops drawing at that model's maxAgeFor() exactly like before; panel switches still clear frames.
  */
 static boolean keepOnFailure(boolean hasFrame,long ageMs){return keepOnFailure(hasFrame,ageMs,MAX_AGE_MS);}
 static boolean keepOnFailure(boolean hasFrame,long ageMs,long maxAge){return hasFrame&&ageMs>=0&&ageMs<=maxAge;}
}
