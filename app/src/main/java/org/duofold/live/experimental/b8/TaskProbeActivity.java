package org.duofold.live.experimental.b8;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;

/** One actual activity task: identity and counter survive display configuration changes. */
public class TaskProbeActivity extends Activity {
 private long born;private int taps;private ProbeView view;
 public void onCreate(Bundle b){super.onCreate(b);born=b==null?SystemClock.elapsedRealtime():b.getLong("born");taps=b==null?0:b.getInt("taps");
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
  Button stop=new Button(this);stop.setText("Stop / return to Lab");stop.setOnClickListener(v->{if(RecorderService.active)startService(new Intent(this,RecorderService.class).setAction("stop"));startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));});box.addView(stop);
  view=new ProbeView();box.addView(view,new LinearLayout.LayoutParams(-1,0,1));setContentView(box);log("CREATE");
 }
 private void log(String event){RecorderService.targetEvent("TASK_PROBE "+event+" task="+getTaskId()+" D"+(getDisplay()==null?-1:getDisplay().getDisplayId())+" taps="+taps+" born="+born);}
 protected void onSaveInstanceState(Bundle b){b.putLong("born",born);b.putInt("taps",taps);super.onSaveInstanceState(b);}
 public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);log("CONFIG "+c);}
 protected void onResume(){super.onResume();log("RESUME");if(view!=null)view.invalidate();}
 public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);log("WINDOW_FOCUS "+focused);}
 protected void onPause(){log("PAUSE");super.onPause();}
 private final class ProbeView extends View {
  final Paint paint=new Paint(3);long lastLog;int frames;
  ProbeView(){super(TaskProbeActivity.this);setContentDescription("Tap to increment this task's counter");}
  protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);log("WINDOW_VISIBILITY "+visibility);}
  protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);log("SIZE "+oldw+"x"+oldh+" -> "+w+"x"+h);}
  protected void onDraw(Canvas canvas){long now=SystemClock.elapsedRealtime();int id=getDisplay()==null?-1:getDisplay().getDisplayId();float w=getWidth();
   canvas.drawColor(id==0?Color.rgb(15,50,80):Color.rgb(70,20,65));paint.setColor(Color.WHITE);paint.setTextSize(Math.max(24,w/25));
   String[] lines={"REAL TASK #"+getTaskId(),"Currently on DISPLAY "+id,"TAPS: "+taps,"Age: "+((now-born)/100)+" ticks","Tap only when test instructions say","Keep hinge still near 90 degrees",RecorderService.active?"Recording: follow selected test timing":"Waiting / recording finished"};
   int y=60;for(String line:lines){canvas.drawText(line,20,y,paint);y+=60;}
   paint.setColor(Color.rgb(255,175,30));float x=(now%2000)/2000f*Math.max(1,w-80);canvas.drawRect(x,y+10,x+80,y+50,paint);
   frames++;if(now-lastLog>=1000){lastLog=now;final int submittedFrame=frames;final int submittedDisplay=id;final long submittedAt=now;
    if(isHardwareAccelerated())getViewTreeObserver().registerFrameCommitCallback(()->log("FRAME_COMMIT submittedFrame="+submittedFrame+" submittedDisplay="+submittedDisplay+" submittedAt="+submittedAt+" callbackDelayMs="+(SystemClock.elapsedRealtime()-submittedAt)+"; not pixel proof"));
    log("DRAW size="+getWidth()+"x"+getHeight()+" frames="+frames+"; CPU callback, not pixels");}
   postInvalidateDelayed(33);
  }
  public boolean onTouchEvent(android.view.MotionEvent e){if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP){taps++;log("TOUCH x="+e.getX()+" y="+e.getY());performClick();invalidate();}return true;}
  public boolean performClick(){super.performClick();return true;}
 }
}
