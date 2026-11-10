package org.duofold.live;
import android.os.*;
import android.view.Display;
import android.graphics.Point;
/** Shell observations are independent of capture content; never infer identity from dimensions. */
public final class PanelIdentity {
 private static volatile Bundle latest;
 public static void accept(Bundle snapshot){latest=snapshot;}
 public static String identity(Display display){
  if(display==null)return "unknown";
  Bundle all=latest;
  if(all!=null){Bundle row=all.getBundle("d"+display.getDisplayId());
   if(row!=null){Point size=new Point();display.getRealSize(size);
    String id=row.getString("id");
    if(PanelIdentityPolicy.usable(id,SystemClock.elapsedRealtime()-all.getLong("stamp"),row.getInt("w"),row.getInt("h"),row.getInt("r"),size.x,size.y,display.getRotation())&&row.getInt("state")==display.getState())return id;
   }
   // A received but invalid mapping must not revive an older identity.
   return "unknown";
  }
  try{String id=String.valueOf(Display.class.getMethod("getUniqueId").invoke(display));return PanelIdentityPolicy.known(id)?id:"unknown";}catch(Exception e){return "unknown";}
 }
}
