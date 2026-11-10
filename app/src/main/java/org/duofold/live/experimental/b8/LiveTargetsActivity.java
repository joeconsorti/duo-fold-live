package org.duofold.live.experimental.b8;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.view.*;
import android.widget.*;

/** App-owned content routing control, deliberately no primary-state requests or input injection. */
public class LiveTargetsActivity extends Activity {
 private final Handler h=new Handler(Looper.getMainLooper());
 private Presentation secondary;
 private long opened,recordingSeen,shown;
 private boolean started,closed,everActive;
 private int destination=-1,lastDestination=-1;
 private TextView status;
 private Target primaryTarget,secondaryTarget;
 public void onCreate(Bundle b){super.onCreate(b);opened=SystemClock.elapsedRealtime();
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
  status=new TextView(this);status.setTextSize(19);status.setText("Preparing fixed-mapping live targets. Keep the hinge still near 90°. Stay in this app.");box.addView(status);
  Button stop=new Button(this);stop.setText("Stop test and return");stop.setOnClickListener(v->end(true));box.addView(stop);
  primaryTarget=new Target(this,0);box.addView(primaryTarget,new LinearLayout.LayoutParams(-1,0,1));setContentView(box);
  h.post(tick);
 }
 private final Runnable tick=new Runnable(){public void run(){
  if(closed)return;long now=SystemClock.elapsedRealtime();
  everActive|=RecorderService.active;
  String s=RecorderService.status;
  if(s.startsWith("RECORDING")&&recordingSeen==0)recordingSeen=now;
  if((everActive&&!RecorderService.active)||s.startsWith("Failed")||now-opened>110000){end(true);return;}
  if(recordingSeen>0&&!started&&now-recordingSeen>=3000){
   started=true;shown=now;Display host=getDisplay();int id=host==null?0:host.getDisplayId();primaryTarget.id=id;
   Display chosen=null;for(Display d:getSystemService(DisplayManager.class).getDisplays())if(d.getDisplayId()!=id&&d.getDisplayId()==1)chosen=d;
   RecorderService.targetEvent("START host D"+id+"; fixed mapping; app-owned content; no task migration; drawing is not optical proof");
   if(chosen!=null)try{
    secondary=new Presentation(LiveTargetsActivity.this,chosen);secondaryTarget=new Target(secondary.getContext(),chosen.getDisplayId());secondary.setContentView(secondaryTarget);
    secondary.setOnCancelListener(d->end(true));secondary.show();secondary.getWindow().setLayout(-1,-1);
    RecorderService.targetEvent("SECONDARY show accepted D"+chosen.getDisplayId()+"; not visible-pixel proof");
   }catch(Exception e){RecorderService.targetEvent("SECONDARY rejected "+e);status.setText("Secondary window rejected; report will contain the reason.");}
   else RecorderService.targetEvent("SECONDARY unavailable; no display 1 different from host");
  }
  if(started){
   long age=now-shown;
   if(now-recordingSeen>=17000||!s.startsWith("RECORDING")){end(false);return;}
   destination=(age/3000)%2==0?primaryTarget.id:1;
   if(destination!=lastDestination){lastDestination=destination;RecorderService.targetEvent("CONTENT_ROUTE D"+destination+" elapsed="+age+"; same counter, app redraw only, no primary request");}
   status.setText("Fixed mapping · live card → D"+destination+"\nTap both screens. Both heartbeat bars should move. Auto-closes before cleanup.");
   primaryTarget.invalidate();if(secondaryTarget!=null)secondaryTarget.invalidate();
  }
  h.postDelayed(this,33);
 }};
 private void end(boolean stop){if(closed)return;closed=true;h.removeCallbacksAndMessages(null);
  if(secondary!=null){try{secondary.dismiss();}catch(Exception ignored){}secondary=null;}
  RecorderService.targetEvent("END requestedStop="+stop);
  if(stop&&RecorderService.active)startService(new Intent(this,RecorderService.class).setAction("stop"));finish();
 }
 @Override public void onBackPressed(){end(true);}
 @Override public void onDestroy(){if(!closed)end(true);super.onDestroy();}
 private final class Target extends View {
  int id,taps,frames;long lastLog;float x=-1,y=-1;final Paint p=new Paint(3);
  Target(Context c,int displayId){super(c);id=displayId;setContentDescription("Live display test. Tap to increment this display's counter.");}
  protected void onDraw(Canvas c){
   long now=SystemClock.elapsedRealtime();float w=getWidth(),ht=getHeight();c.drawColor(id==0?Color.rgb(12,45,75):Color.rgb(65,20,65));
   p.setColor(Color.WHITE);p.setTextSize(Math.max(22,w/23));
   c.drawText("DISPLAY "+id+" · taps "+taps,20,60,p);
   c.drawText(started?"LIVE heartbeat":"Waiting for setup",20,110,p);
   p.setColor(Color.CYAN);float pos=(now%2000)/2000f*Math.max(1,w-60);c.drawRect(pos,135,pos+60,160,p);
   if(started&&destination==id){p.setColor(Color.rgb(255,170,35));c.drawRoundRect(20,190,w-20,Math.min(ht-30,400),20,20,p);
    p.setColor(Color.BLACK);c.drawText("LIVE CARD #"+((now-shown)/100),35,255,p);
    c.drawText("Tap here",35,315,p);
   }
   if(x>=0){p.setColor(Color.GREEN);c.drawCircle(x,y,24,p);}
   if(started){frames++;if(now-lastLog>=1000){lastLog=now;RecorderService.targetEvent("DRAW D"+id+" view="+(int)w+"x"+(int)ht+" totalFrames="+frames+" taps="+taps+" destination="+destination+"; CPU draw callback only");}}
  }
  public boolean onTouchEvent(android.view.MotionEvent e){if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP){taps++;x=e.getX();y=e.getY();
   RecorderService.targetEvent("TOUCH viewD"+id+" eventDisplay="+eventDisplay(e)+" x="+x+" y="+y+" taps="+taps+" eventUptime="+e.getEventTime());performClick();invalidate();}return true;}
  private String eventDisplay(android.view.MotionEvent e){try{return String.valueOf(e.getClass().getMethod("getDisplayId").invoke(e));}catch(Exception unavailable){return "unavailable";}}
  public boolean performClick(){super.performClick();return true;}
 }
}
