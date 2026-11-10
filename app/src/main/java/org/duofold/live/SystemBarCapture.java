package org.duofold.live;

import android.graphics.*;
import android.os.*;
import android.view.SurfaceControl;
import java.util.*;

/** Bounded bar-only delivery. Never changes SystemUI, captures secure layers or
 * substitutes new app/wallpaper pixels in the retained presentation. */
final class SystemBarCapture {
 static Bundle capture(Parcel data)throws Exception{
  int n=data.readInt();if(n<1||n>64)throw new IllegalArgumentException("Invalid bar exclusions");
  SurfaceControl[] excluded=new SurfaceControl[n];Bitmap bitmap=null;
  try{
   for(int i=0;i<n;i++){excluded[i]=data.readTypedObject(SurfaceControl.CREATOR);if(excluded[i]==null||!excluded[i].isValid())throw new IllegalStateException("Bar exclusion changed");}
   int display=data.readInt();String physical=data.readString();boolean metadataOnly=data.readBoolean();String known=data.readString();
   int expectedW=data.readInt(),expectedH=data.readInt(),expectedRotation=data.readInt(),maxSide=data.readInt();
   long token=WindowWorkPriority.INSTANCE.optionalToken();
   if(token<0)throw new IllegalStateException("App work active; optional bars skipped");
   if(maxSide<1||maxSide>1440)throw new IllegalArgumentException("Invalid bar raster limit");
   if(display<0||display>1||physical==null||!physical.matches("local:[0-9]+"))throw new IllegalArgumentException("Invalid bar panel");
   Object wm=MovingSceneCapture.service("window","android.view.IWindowManager");Class<?> wa=Class.forName("android.view.IWindowManager");
   if((boolean)wa.getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked");
   Object dm=MovingSceneCapture.service("display","android.hardware.display.IDisplayManager");
   java.lang.reflect.Method info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class);
   Object before=info.invoke(dm,display);
   if(!MovingSceneCapture.matches(before,physical))throw new IllegalStateException("Bar panel mapping pending");
   int w=MovingSceneCapture.number(before,"logicalWidth"),h=MovingSceneCapture.number(before,"logicalHeight"),rotation=MovingSceneCapture.number(before,"rotation");
   if(!SystemBarWorkPolicy.geometry(expectedW,expectedH,expectedRotation,w,h,rotation))throw new IllegalStateException("Waiting for known physical-panel dimensions");
   String windowBefore=EndpointWindowTrace.dumpOptional(token);
   List<SystemBarRegions.Region> regions=SystemBarRegions.parse(windowBefore,display,w,h);
   if(regions.isEmpty())throw new IllegalStateException("No bounded drawn system-bar windows on D"+display+"; "+SystemBarRegions.evidence(windowBefore,display,w,h));
   String signature=physical+":"+w+"x"+h+":"+rotation+":"+SystemBarRegions.signature(regions);
   boolean unchanged=!metadataOnly&&signature.equals(known);
   long started=SystemClock.elapsedRealtime();
   if(!WindowWorkPriority.INSTANCE.accepts(token))throw new IllegalStateException("App work superseded bar capture");
   if(!metadataOnly&&!unchanged)bitmap=GlassCapture.captureBitmap(wm,wa,display,w,h,excluded,maxSide);
   Object after=info.invoke(dm,display);
   if(!MovingSceneCapture.matches(after,physical)||w!=MovingSceneCapture.number(after,"logicalWidth")||h!=MovingSceneCapture.number(after,"logicalHeight")||rotation!=MovingSceneCapture.number(after,"rotation")||(boolean)wa.getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Bar mapping/lock changed during capture");
   String windowAfter=EndpointWindowTrace.dumpOptional(token);
   regions=SystemBarRegions.stable(regions,SystemBarRegions.parse(windowAfter,display,w,h));
   if(regions.isEmpty())throw new IllegalStateException("System-bar windows changed during capture");
   Bundle out=new Bundle();out.putBoolean("ok",true);out.putInt("width",w);out.putInt("height",h);out.putInt("rotation",rotation);out.putString("physical",physical);out.putInt("display",display);out.putLong("stamp",started);
   out.putBoolean("unchanged",unchanged);out.putString("evidence",SystemBarRegions.evidence(windowAfter,display,w,h));
   // A partial stable subset is a different revision: it must not suppress a
   // later capture of windows that appeared or changed while this check ran.
   out.putString("signature",physical+":"+w+"x"+h+":"+rotation+":"+SystemBarRegions.signature(regions));
   ArrayList<Bundle> tiles=new ArrayList<>();
   try{for(SystemBarRegions.Region region:regions){
    Bundle tile=new Bundle();tile.putString("kind",region.kind);tile.putIntArray("bounds",new int[]{region.left,region.top,region.right,region.bottom});
    if(metadataOnly||unchanged){tiles.add(tile);continue;}
    int l=region.left*bitmap.getWidth()/w,t=region.top*bitmap.getHeight()/h;
    int r=(int)Math.ceil(region.right*(double)bitmap.getWidth()/w),b=(int)Math.ceil(region.bottom*(double)bitmap.getHeight()/h);
    Bitmap piece=Bitmap.createBitmap(bitmap,l,t,r-l,b-t);if(piece==bitmap)throw new IllegalStateException("Unbounded bar crop");
    tile.putParcelable("bitmap",piece);tiles.add(tile);
   }}catch(Exception e){for(Bundle tile:tiles){Bitmap piece=tile.getParcelable("bitmap",Bitmap.class);if(piece!=null)piece.recycle();}throw e;}
   if(!WindowWorkPriority.INSTANCE.accepts(token)){for(Bundle tile:tiles){Bitmap piece=tile.getParcelable("bitmap",Bitmap.class);if(piece!=null)piece.recycle();}throw new IllegalStateException("App work superseded bar delivery");}
   out.putParcelableArrayList("tiles",tiles);return out;
  }finally{if(bitmap!=null)bitmap.recycle();for(SurfaceControl sc:excluded)if(sc!=null)sc.release();}
 }
}
