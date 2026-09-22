package org.duofold.live;
import android.content.*;
/** Restart only saved user-enabled work after update or normal boot. */
public final class RestoreReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  String action=intent.getAction();
  if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)){
   FoldAwakeDefault.reconnect();org.duofold.live.wallpaperlayer.WallpaperRestore.resume(context);
  }
 }
}
