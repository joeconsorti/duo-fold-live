package org.duofold.live;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
/** Lifecycle/connection events only, no app content or screenshots. */
public final class RecoveryLog {
 private static SharedPreferences prefs;
 public static synchronized void init(Context c){if(prefs==null)prefs=c.getSharedPreferences("recovery_log",0);}
 public static synchronized void add(String event){if(prefs==null)return;String prior=prefs.getString("events","");String[] lines=(prior+System.currentTimeMillis()+" (uptime "+SystemClock.elapsedRealtime()+") "+event+"\n").split("\n");StringBuilder out=new StringBuilder();for(int i=Math.max(0,lines.length-45);i<lines.length;i++)out.append(lines[i]).append('\n');prefs.edit().putString("events",out.toString()).apply();}
 public static synchronized String report(){return prefs==null?"No lifecycle events":prefs.getString("events","");}
}
