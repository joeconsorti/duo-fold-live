package com.consorti.wallpaperlayer;
import android.app.WallpaperManager;import android.content.Context;import android.graphics.Bitmap;import android.graphics.BitmapFactory;import java.io.File;
/** Explicit user action: writes only Samsung inner/cover lock slots, never home slots. */
public final class LockPhotoApplier {
 public static String apply(Context context,File photo){
  Bitmap image=BitmapFactory.decodeFile(photo.getAbsolutePath());
  if(image==null)return "Lock photo: selected image could not be decoded";
  StringBuilder result=new StringBuilder("Lock-screen photo action:\n");
  try{
   WallpaperManager wm=WallpaperManager.getInstance(context);
   for(int mode:new int[]{4,16}){
    int which=mode|WallpaperManager.FLAG_LOCK;
    String name=mode==4?"Inner lock screen":"Cover lock screen";
    try{int id=wm.setBitmap(image,null,false,which);result.append(name).append(id>0?": accepted, wallpaper ID "+id:": no wallpaper ID returned; not confirmed").append('\n');}
    catch(Exception e){result.append(name).append(": failed — ").append(e).append('\n');}
   }
  }finally{image.recycle();}
  result.append("Requested lock slots 6 and 18 only; home slots 5 and 17 were not written. Lock photos remain after Disable; change them in Samsung wallpaper settings to restore your previous choice.");
  return result.toString();
 }
}
