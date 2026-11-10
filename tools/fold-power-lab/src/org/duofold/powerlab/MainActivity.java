package org.duofold.powerlab;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import android.graphics.Color;
import java.io.*;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
 private TextView state;private final Handler h=new Handler(Looper.getMainLooper());
 private final Runnable update=new Runnable(){public void run(){state.setText(Bridge.state+"\n\n"+RecorderService.status);h.postDelayed(this,1000);}};
 public void onCreate(Bundle b){super.onCreate(b);
  ScrollView scroll=new ScrollView(this);LinearLayout col=new LinearLayout(this);col.setOrientation(1);int p=(int)(20*getResources().getDisplayMetrics().density);col.setPadding(p,p,p,p);scroll.addView(col);setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{col.setPadding(p,p+insets.getSystemWindowInsetTop(),p,p+insets.getSystemWindowInsetBottom());return insets;});
  text(col,"Fold Power Lab",28);text(col,"Display power investigation • 0.3.0",15);
  text(col,"1. Turn Duo Fold Live OFF. Keep Shizuku running.\n2. Authorize and connect below.\n3. Start a test. Go Home or open your usual app. Wait until the notification says RECORDING, then perform the selected gesture. For shade tests, pull down slowly, hold fully open for 4 seconds, then dismiss.\n4. Return after 10 seconds and save the report ZIP.",17);
  text(col,"Baseline is read-only. Legacy tests run 10 seconds; recipe tests run 10–30 seconds with a separate cleanup watchdog. Stop releases owned tokens. Disable Duo before override tests. Detailed blur tracing streams data to preserve the full gesture; it adds overhead and may be restricted by Samsung. Reports contain app/window names and system logs; no screenshots or network uploads.",14);
  button(col,"Authorize Shizuku",()->{try{if(!Shizuku.pingBinder())throw new IllegalStateException("Start Shizuku and reopen this app");if(Shizuku.checkSelfPermission()!=0)Shizuku.requestPermission(7);else Bridge.state="Authorized — tap Connect";}catch(Exception e){message(e.toString());}});
  button(col,"Connect",()->{try{if(Shizuku.checkSelfPermission()!=0)throw new IllegalStateException("Authorize first");Bridge.connect(this);}catch(Exception e){message(e.toString());}});
  text(col,"Mid-fold tests: hold the hinge near 90°. Keep it still while the app automatically switches primary panels. Disable Duo. Wait for RECORDING. Each test starts with a settling period and verifies both displays report ON before switching.",16);
  button(col,"Mid-fold switching — baseline · 20 seconds",()->startRecipe(ExperimentConfig.preset(false).encode()));
  button(col,"Mid-fold switching — both ON · 20 seconds",()->startRecipe(ExperimentConfig.preset(true).encode()));
  button(col,"Configure / run custom recipe",this::recipeDialog);
  button(col,"Record handoff · 10 seconds",()->start(0));button(col,"Record Recents blur · 10 seconds",()->start(1));
  button(col,"Record notification shade blur · 10 seconds",()->start(2));
  button(col,"Record Quick Settings blur · 10 seconds",()->start(3));
  button(col,"Record Duo compositor blur · 10 seconds",()->start(4));
  button(col,"Power baseline + API inventory · 10 seconds",()->start(5));
  button(col,"Samsung ON override · D0 · 10 seconds",()->start(6));
  button(col,"Samsung ON override · D1 · 10 seconds",()->start(7));
  button(col,"Samsung ON override · both · 10 seconds",()->start(8));
  button(col,"Bright wake lock · 10 seconds",()->start(9));
  button(col,"Concurrent inner primary · 10 seconds",()->start(10));
  button(col,"Concurrent cover primary · 10 seconds",()->start(11));
  button(col,"Concurrent inner + both ON · 10 seconds",()->start(12));
  button(col,"Concurrent cover + both ON · 10 seconds",()->start(13));
  button(col,"Stop recording",()->{if(RecorderService.active)startService(new Intent(this,RecorderService.class).setAction("stop"));});
  button(col,"Save report ZIP",()->{if(RecorderService.active){message("Wait for recording to finish");return;}File f=latest();if(f==null){message("No completed report yet");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,f.getName());startActivityForResult(i,9);});
  state=text(col,"Idle",16);
  text(col,"Start with Power baseline and Duo disabled. Save that ZIP. Repeat baseline with Duo enabled. Disable Duo before every override test; select one test at a time. D0 and D1 can change physical panels during a fold. The report tracks physical IDs and actual sample gaps. Accepted API calls do not prove the screen stayed lit. Rejected and unsupported calls remain in the report. If cleanup fails, the shell worker exits and you must reconnect.",14);
 }
 private void startRecipe(String recipe){try{ExperimentConfig.parse(recipe);launch(14,recipe);}catch(Exception e){message(e.getMessage());}}
 private void recipeDialog(){
  ExperimentConfig saved;
  try{saved=ExperimentConfig.parse(getSharedPreferences("recipes",0).getString("last",ExperimentConfig.preset(false).encode()));}catch(Exception e){saved=ExperimentConfig.preset(false);}
  final ExperimentConfig c=saved;
  ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(24,8,24,8);scroll.addView(box);
  text(box,"Recipes are saved locally and included in every ZIP. Use the baseline and both-ON presets first. Changing one option at a time makes comparisons useful.",15);
  EditText duration=number(box,"Recording seconds (10–30)",c.duration);
  Spinner initial=choice(box,"Initial primary",new String[]{"Unchanged","Inner","Cover"},c.initial);
  CheckBox alternate=check(box,"Automatically alternate primary displays",c.alternate);
  EditText settle=number(box,"Initial settling (1000–5000 ms)",c.settle),period=number(box,"Switch spacing (1000–5000 ms)",c.period),count=number(box,"Maximum switches (1–12; duration may limit)",c.switches);
  Spinner target=choice(box,"Samsung ON targets (logical IDs can swap panels)",new String[]{"None","D0","D1","Both"},c.target);
  Spinner api=choice(box,"Samsung API",new String[]{"Per-display token override","Global token override — experimental"},c.backend);
  text(box,"Global requires target Both. Hidden Samsung calls may fail or have no effect. Final OEM integer stays at the previously accepted value 10000; its meaning is unverified. App timers and token cleanup provide the bound, not an assumed OEM timeout.",14);
  Spinner order=choice(box,"Apply ON override",new String[]{"Before initial primary request","After initial settling"},c.order);
  EditText renewal=number(box,"Renew ON every ms (0=once; 250–1000)",c.renewal);
  Spinner wake=choice(box,"Wake-lock combination",new String[]{"None","Partial (CPU)","Dim screen","Bright screen","Full screen (legacy)"},c.wake);
  Spinner flags=choice(box,"Device-state request flags",new String[]{"0 — default","1 — cancel when base changes"},c.flags);
  Spinner cancel=choice(box,"Unexpected current-request cancellation",new String[]{"Stop and release","Retry after 500 ms (maximum 3)"},c.cancellation);
  EditText sample=number(box,"Power sampling interval (10–100 ms)",c.sample);
  CheckBox snapshots=check(box,"Extra display/state dumps at switches (adds overhead)",c.snapshots);
  text(box,"Cancellation callbacks do not provide the cause. Superseded-request callbacks are labeled separately. Failed ON/wake options are logged; the remaining recipe continues. No persistent settings or raw Binder transaction guesses.",14);
  final java.util.concurrent.Callable<String> read=()->{ExperimentConfig n=new ExperimentConfig();n.duration=Integer.parseInt(duration.getText().toString());n.initial=initial.getSelectedItemPosition();n.alternate=alternate.isChecked();n.settle=Integer.parseInt(settle.getText().toString());n.period=Integer.parseInt(period.getText().toString());n.switches=Integer.parseInt(count.getText().toString());n.target=target.getSelectedItemPosition();n.backend=api.getSelectedItemPosition();n.order=order.getSelectedItemPosition();n.renewal=Integer.parseInt(renewal.getText().toString());n.wake=wake.getSelectedItemPosition();n.flags=flags.getSelectedItemPosition();n.cancellation=cancel.getSelectedItemPosition();n.sample=Integer.parseInt(sample.getText().toString());n.snapshots=snapshots.isChecked();n.validate();return n.encode();};
  button(box,"Save recipe + copy code",()->{try{String value=read.call();getSharedPreferences("recipes",0).edit().putString("last",value).apply();((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Fold Power Lab recipe",value));message("Saved and copied. Paste this code to reproduce the test.");}catch(Exception e){message("Invalid recipe: "+e.getMessage());}});
  EditText imported=new EditText(this);imported.setHint("Paste recipe code here");box.addView(imported);
  button(box,"Import recipe (reopen editor to view)",()->{try{String value=ExperimentConfig.parse(imported.getText().toString().trim()).encode();getSharedPreferences("recipes",0).edit().putString("last",value).apply();message("Imported. Close and reopen this editor.");}catch(Exception e){message("Invalid recipe: "+e.getMessage());}});
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Experiment recipe").setView(scroll).setNegativeButton("Close",null).setPositiveButton("Run",null).create();
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{String value=read.call();getSharedPreferences("recipes",0).edit().putString("last",value).apply();startRecipe(value);dialog.dismiss();}catch(Exception e){message("Invalid recipe: "+e.getMessage());}}));dialog.show();
 }
 private EditText number(LinearLayout box,String label,int value){text(box,label,15);EditText e=new EditText(this);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);e.setText(String.valueOf(value));box.addView(e);return e;}
 private Spinner choice(LinearLayout box,String label,String[] values,int value){text(box,label,15);Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(a);s.setSelection(value);box.addView(s);return s;}
 private CheckBox check(LinearLayout box,String label,boolean value){CheckBox c=new CheckBox(this);c.setText(label);c.setChecked(value);box.addView(c);return c;}
 private File latest(){String name=getSharedPreferences("reports",0).getString("latest","");File f=new File(getFilesDir(),name);return f.isFile()?f:null;}
 private void start(int mode){launch(mode,null);}
 private void launch(int mode,String recipe){if(RecorderService.active){message("A recording is already running");return;}if(Bridge.remote==null){message("Connect Shizuku first");return;}if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},8);message("Allow notifications, then tap Record again");return;}startForegroundService(new Intent(this,RecorderService.class).putExtra("mode",mode).putExtra("recipe",recipe));}
 public void onResume(){super.onResume();h.post(update);}public void onPause(){h.removeCallbacks(update);super.onPause();}
 protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==9&&result==RESULT_OK&&data!=null){File f=latest();if(f==null)return;new Thread(()->{try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(data.getData())){byte[] b=new byte[16384];int n;while((n=in.read(b))>=0)out.write(b,0,n);runOnUiThread(()->message("Saved. Attach the ZIP in our chat."));}catch(Exception e){runOnUiThread(()->message("Save failed: "+e));}}).start();}}
 private TextView text(LinearLayout l,String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(225,232,246));t.setPadding(0,12,0,16);l.addView(t);return t;}
 private void button(LinearLayout l,String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);l.addView(b);b.setOnClickListener(v->r.run());}
 private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
