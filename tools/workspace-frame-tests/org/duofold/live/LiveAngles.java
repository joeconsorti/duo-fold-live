package org.duofold.live;
import android.os.*;
public class LiveAngles{
 public static int guards;public static boolean rejectBefore,rejectAfter;
 public static IBinder captureBinder(){return (code,p,r,flags)->{if(code!=13)throw new AssertionError("isolated workspace guard only");boolean after=++guards==2;Bundle b=new Bundle();b.putBoolean("ok",!(after?rejectAfter:rejectBefore));b.putString("error","guard rejected");b.putString("workspace","stable tasks");b.putInt("width",1248);b.putInt("height",1972);b.putInt("rotation",0);r.bundle=b;return true;};}
}
