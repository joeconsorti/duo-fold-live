package org.duofold.live;
import android.app.*;
import android.content.*;
import android.net.Uri;
/** Up to three weekly invitations after successful use. No new alarms or animation work. */
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
 static boolean enabled(Context c){return !prefs(c).getBoolean("retired_weekly",false);}
 static void stop(Context c){prefs(c).edit().putBoolean("retired_weekly",true).apply();c.getSystemService(NotificationManager.class).cancel(ID);}
 static void snooze(Context c){prefs(c).edit().putLong("snooze_until",System.currentTimeMillis()+3*SupportPromptPolicy.WEEK).apply();c.getSystemService(NotificationManager.class).cancel(ID);}
 private static boolean due(Context c,String channel){
  SharedPreferences p=prefs(c);long now=System.currentTimeMillis(),cycle=p.getLong("weekly_cycle",0);
  if(!SupportPromptPolicy.cycleAllowed(now,p.getLong("first_use",0),cycle,p.getInt("weekly_count",0),p.getLong("snooze_until",0),!enabled(c),working(c)))return false;
  return cycle==0||now-cycle>=SupportPromptPolicy.WEEK||p.getLong(channel,0)!=cycle;
 }
 private static void seen(Context c,String channel){
  SharedPreferences p=prefs(c);long now=System.currentTimeMillis(),cycle=p.getLong("weekly_cycle",0);SharedPreferences.Editor e=p.edit();
  if(cycle==0||now-cycle>=SupportPromptPolicy.WEEK){cycle=now;e.putLong("weekly_cycle",cycle).putInt("weekly_count",p.getInt("weekly_count",0)+1);}
  e.putLong(channel,cycle).apply();
 }
 static boolean inAppDue(Context c){return due(c,"weekly_in_app");}
 static void seenInApp(Context c){seen(c,"weekly_in_app");}
 static void open(Context c){stop(c);try{c.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(ActivityNotFoundException e){android.widget.Toast.makeText(c,"Open ko-fi.com/joeconsorti in your browser",android.widget.Toast.LENGTH_LONG).show();}}
 private static PendingIntent action(Context c,String action,int request){return PendingIntent.getBroadcast(c,request,new Intent(c,SupportReminderReceiver.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static void tick(Context c){if(due(c,"weekly_notification")&&notify(c))seen(c,"weekly_notification");}
 static boolean notify(Context c){
  NotificationManager nm=c.getSystemService(NotificationManager.class);
  if(!nm.areNotificationsEnabled())return false;
  nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Support Duo Fold Live",NotificationManager.IMPORTANCE_DEFAULT));
  if(nm.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
  PendingIntent donate=PendingIntent.getActivity(c,220,new Intent(c,MainActivity.class).setAction("org.duofold.live.SUPPORT"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  try{nm.notify(ID,new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_compass).setContentTitle("Enjoying Duo Fold Live?").setContentText("Support future updates on Ko-fi. Always optional.").setContentIntent(donate).setAutoCancel(true)
   .addAction(new Notification.Action.Builder(null,"Support",donate).build())
   .addAction(new Notification.Action.Builder(null,"Snooze 3 weeks",action(c,"snooze",222)).build())
   .addAction(new Notification.Action.Builder(null,"Don't ask again",action(c,"stop",223)).build()).build());return true;
  }catch(SecurityException ignored){return false;}
 }
}
