package org.duofold.live;
import android.app.*;
import android.content.*;
import android.net.Uri;
/** Daily, optional support prompts. Never alters animation or requests an exact alarm. */
final class SupportPrompts {
 static final String URL="https://ko-fi.com/joeconsorti";
 private static final int ID=220;
 private static final String CHANNEL="duo_support";
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("duo_support",0);}
 private static boolean working(Context c){return c.getSharedPreferences("standalone",0).getBoolean("enabled",false)&&LiveAngles.fresh()&&StandaloneService.Companion.getInstance()!=null;}
 static void recordUse(Context c){
  if(!working(c)||c.getSharedPreferences("first_run",0).getInt("state",0)==1)return;
  SharedPreferences p=prefs(c);if(p.getLong("first_use",0)==0)p.edit().putLong("first_use",System.currentTimeMillis()).apply();
 }
 static boolean enabled(Context c){return prefs(c).getBoolean("reminders",true);}
 static void enabled(Context c,boolean v){prefs(c).edit().putBoolean("reminders",v).apply();if(!v)c.getSystemService(NotificationManager.class).cancel(ID);}
 static boolean inAppDue(Context c){SharedPreferences p=prefs(c);return SupportPromptPolicy.due(System.currentTimeMillis(),p.getLong("first_use",0),p.getLong("in_app",0),enabled(c),working(c));}
 static void seenInApp(Context c){prefs(c).edit().putLong("in_app",System.currentTimeMillis()).apply();}
 static void open(Context c){seenInApp(c);c.getSystemService(NotificationManager.class).cancel(ID);try{c.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(ActivityNotFoundException e){android.widget.Toast.makeText(c,"Open ko-fi.com/joeconsorti in your browser",android.widget.Toast.LENGTH_LONG).show();}}
 static void tick(Context c){
  SharedPreferences p=prefs(c);long now=System.currentTimeMillis();
  if(!SupportPromptPolicy.due(now,p.getLong("first_use",0),p.getLong("notification",0),enabled(c),working(c)))return;
  NotificationManager nm=c.getSystemService(NotificationManager.class);
  if(!nm.areNotificationsEnabled())return;
  nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Optional support reminders",NotificationManager.IMPORTANCE_DEFAULT));
  if(nm.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)return;
  PendingIntent donate=PendingIntent.getActivity(c,220,new Intent(Intent.ACTION_VIEW,Uri.parse(URL)),PendingIntent.FLAG_IMMUTABLE);
  PendingIntent open=PendingIntent.getActivity(c,221,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  try{nm.notify(ID,new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Enjoying Duo Fold Live?").setContentText("Help support future updates. Donations are always optional.").setContentIntent(open).setAutoCancel(true).addAction(new Notification.Action.Builder(null,"Support on Ko-fi",donate).build()).build());p.edit().putLong("notification",now).apply();}catch(SecurityException ignored){}
 }
}
