package org.duofold.live;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

/** Preserve the composed Home image once, using launcher pixels only as evidence.
 * Owner filtering is not task isolation. The captures need not share a frame;
 * alpha/content agreement is a rejection heuristic, not optical visibility. */
final class HomeNativeComposition {
 static final class Result {
  final Bitmap bitmap;final HomeContentPolicy.Sample content;final String agreement;
  Result(Bitmap bitmap,HomeContentPolicy.Sample content,String agreement){this.bitmap=bitmap;this.content=content;this.agreement=agreement;}
 }
 static final class LauncherProbe {
  final int width, height;
  final int[] pixels;
  final HomeContentPolicy.Sample content;

  LauncherProbe(Bitmap launcher, int[] pixels, HomeContentPolicy.Sample content) {
   this.width = launcher.getWidth();
   this.height = launcher.getHeight();
   this.pixels = pixels;
   this.content = content;
  }
 }

 static LauncherProbe validateLauncher(Bitmap launcher, HomeContentPolicy.Sample reference) {
  Bitmap small=Bitmap.createScaledBitmap(launcher,64,64,true);
  HomeContentPolicy.Sample content;int[] pixels=new int[4096];
  try{small.getPixels(pixels,0,64,0,0,64,64);content=HomeContentPolicy.measure(pixels,64,64);}
  finally{if(small!=launcher)small.recycle();}
  String rejected=HomeContentPolicy.rejection(content,reference);
  if(!rejected.isEmpty())throw new IllegalStateException(rejected+"; "+content);
  return new LauncherProbe(launcher, pixels, content);
 }

 static Result validateScene(Bitmap scene, LauncherProbe launcher) {
  if (scene.getWidth() != launcher.width || scene.getHeight() != launcher.height) {
   throw new IllegalStateException("HOME_NATIVE_BUFFER_MISMATCH");
  }
  // Check the actual composed body too: a bright background cannot substitute
  // for visible launcher content, nor may an unrelated veil overpaint it.
  // Near-opaque probe pixels avoid comparing legitimate wallpaper transparency.
  // A small changed widget/clock is allowed because these are separate captures.
  Bitmap composed=Bitmap.createScaledBitmap(scene,64,64,true);
  int anchors=0,mismatched=0,opaque=0,count=0;
  try{int[] rendered=new int[4096];composed.getPixels(rendered,0,64,0,0,64,64);
   for(int y=6;y<58;y++)for(int x=3;x<61;x++){
    int i=y*64+x,front=launcher.pixels[i],actual=rendered[i];count++;
    if(Color.alpha(actual)>=250)opaque++;
    if(Color.alpha(front)<250)continue;
    anchors++;boolean changed=Color.alpha(actual)<250;
    for(int shift=0;shift<=16;shift+=8)if(Math.abs(((front>>>shift)&255)-((actual>>>shift)&255))>32)changed=true;
    if(changed)mismatched++;
   }
  }finally{if(composed!=scene)composed.recycle();}
  if(opaque<count*.95)throw new IllegalStateException("HOME_SCENE_INCOMPLETE; opaque="+opaque+"/"+count);
  String agreement="launcherAgreement mismatched="+mismatched+"/"+anchors;
  if(anchors>=16&&mismatched>anchors*.25)throw new IllegalStateException("HOME_SCENE_CONTENT_MISMATCH; "+agreement);
  Bitmap result=Bitmap.createBitmap(launcher.width,launcher.height,Bitmap.Config.ARGB_8888);
  try{
   Canvas canvas=new Canvas(result);canvas.drawColor(Color.BLACK);
   // The scene already contains launcher, wallpaper and replacement photo in
   // their native order. Drawing the probe again doubles translucent edges.
   canvas.drawBitmap(scene,0f,0f,null);
   return new Result(result,launcher.content,agreement+(anchors<16?"; sparse evidence":""));
  }catch(Throwable error){result.recycle();throw error;}
 }
}
