package org.duofold.live;
import android.content.*;
public final class SupportReminderReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){if("snooze".equals(intent.getAction()))SupportPrompts.snooze(context);else if("stop".equals(intent.getAction()))SupportPrompts.stop(context);}
}
