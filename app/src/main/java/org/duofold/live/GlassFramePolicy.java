package org.duofold.live;
final class GlassFramePolicy {
 /** Render age limit for a live glass frame. */
 static final long MAX_AGE_MS=350;
 /** Retry delay after a failed capture while the previous frame is still kept. */
 static final long RETRY_WITH_FRAME_MS=32;
 static boolean usable(long stamp,long now,int width,int height,int currentWidth,int currentHeight){
  long age=now-stamp;
  return age>=0 && age<=MAX_AGE_MS && width>0 && height>0 && width==currentWidth && height==currentHeight;
 }
 /**
  * On a failed capture ("Panel changed during capture", screen off, a capture timeout) keep the last good frame
  * while it is still renderable instead of clearing it at once, and retry quickly. Never extends the render age:
  * a kept frame stops drawing at MAX_AGE_MS exactly like before, and panel switches still clear frames explicitly.
  */
 static boolean keepOnFailure(boolean hasFrame,long ageMs){return hasFrame&&ageMs>=0&&ageMs<=MAX_AGE_MS;}
}
