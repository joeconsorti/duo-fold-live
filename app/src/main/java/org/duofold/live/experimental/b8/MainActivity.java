package org.duofold.live.experimental.b8;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import android.graphics.Color;
import java.io.*;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
 private boolean screenshotOnly=false,brightnessHold=false;
 private TextView state;private final Handler h=new Handler(Looper.getMainLooper());
 private final Runnable update=new Runnable(){public void run(){state.setText(Bridge.state+"\n\n"+RecorderService.status);h.postDelayed(this,1000);}};
 public void onCreate(Bundle b){super.onCreate(b);
  if(!getSharedPreferences("standalone",0).getBoolean("b8_lab_enabled",false)){finish();return;}
  ScrollView scroll=new ScrollView(this);LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);int p=(int)(20*getResources().getDisplayMetrics().density);col.setPadding(p,p,p,p);scroll.addView(col);setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{col.setPadding(p,p+insets.getSystemWindowInsetTop(),p,p+insets.getSystemWindowInsetBottom());return insets;});
  text(col,"B8 Display Lab",28);text(col,"Duo experimental lab • engine 0.5.11",15);
  text(col,"1. Turn Duo Fold Live OFF. Keep Shizuku running.\n2. Authorize and connect below.\n3. Start a test. Go Home or open your usual app. Wait until the notification says RECORDING, then perform the selected gesture. For shade tests, pull down slowly, hold fully open for 4 seconds, then dismiss.\n4. Return after 10 seconds and save the report ZIP.",17);
  text(col,"Baseline is read-only. Legacy tests run 10 seconds; recipe tests run 10–30 seconds with a separate cleanup watchdog. Stop releases owned tokens. Disable Duo before override tests. Detailed blur tracing streams data to preserve the full gesture; it adds overhead and may be restricted by Samsung. Reports contain app/window names and system logs. B6/B7 capture temporary screen buffers; no network uploads.",14);
  button(col,"Authorize Shizuku",()->{try{if(!Shizuku.pingBinder())throw new IllegalStateException("Start Shizuku and reopen this app");if(Shizuku.checkSelfPermission()!=0)Shizuku.requestPermission(7);else Bridge.state="Authorized — tap Connect";}catch(Exception e){message(e.toString());}});
  button(col,"Connect",()->{try{if(Shizuku.checkSelfPermission()!=0)throw new IllegalStateException("Authorize first");Bridge.connect(this);}catch(Exception e){message(e.toString());}});
  text(col,"Mid-fold tests: hold the hinge near 90°. Keep it still while the app automatically switches primary panels. Disable Duo. Wait for RECORDING. Each test starts with a settling period and verifies both displays report ON before switching.",16);
  text(col,"Mirror-backed B7 is restored. Its earlier intermittent compositor crash remains unresolved. Screenshot-only comparison and recovery are retained below.",14);
  button(col,"Test B8 — automatic brightness preparation + mirror hold",()->{brightnessHold=true;screenshotOnly=false;fastOptions(true);});
  button(col,"B7 comparison — screenshot only",()->{brightnessHold=false;screenshotOnly=true;fastOptions(true);});
  button(col,"Collect recovery evidence",()->start(19));
  button(col,"Test B6 — frozen shield + geometry gate",()->{brightnessHold=false;screenshotOnly=false;fastOptions(false);});
  button(col,"Test B7 — MIRROR + frozen shield + verified native return",()->{brightnessHold=false;screenshotOnly=false;fastOptions(true);});
  button(col,"Test B4 — staged mirror removal",()->contentOptions(false));
  button(col,"Test B5 — staged removal + delayed focus",()->contentOptions(true));
  button(col,"Test E — live bridge only (run first)",()->bridgeOptions(false));
  button(col,"Test B3 — live bridge + native primary switch",()->bridgeOptions(true));
  button(col,"Test D — A2 with navigation / ColorFade audit",()->new AlertDialog.Builder(this).setTitle("D: diagnostic run of proven A2").setMessage("Same A2 power and task sequence, with expanded read-only diagnostics. This does not enable native navigation or suppress ColorFade. Disable Duo animation AND custom wallpaper; hold near 90 degrees. Choose the built-in task. Move at 6s; unused panel OFF 8–14s; return 16s; other panel OFF 18–24s; cleanup 30s. Stay in the selected app. Save the ZIP afterward. Additional system dumps can affect timing; visible continuity is your observation.").setPositiveButton("Start INNER",(d,w)->taskRoute(1,6)).setNeutralButton("Start COVER",(d,w)->taskRoute(2,6)).setNegativeButton("Cancel",null).show());
  text(col,"Research alternative retained: native primary remap with actual ColorFade suppression. B2 only sets animator duration to zero and still flashed; it is not a ColorFade-disable test.",14);
  button(col,"Test C — native navigation policy",()->desktopOptions(false));
  button(col,"Test C2 — navigation policy + proven A2 power",()->desktopOptions(true));
  button(col,"Export Samsung navigation implementation",()->start(18));
  button(col,"Test A — navigation + outgoing panel OFF",this::routeOptions);
  button(col,"Test B — move app, THEN switch primary",this::promotionOptions);
  button(col,"Test B2 — primary switch with animator scale ZERO",()->new AlertDialog.Builder(this).setTitle("ColorFade duration experiment").setMessage("Temporarily sets the system animator duration scale to zero for this 30-second test, then restores it. An independent 45-second watchdog also attempts restoration. Other animations are affected during the test. This may shorten the fade; it does NOT guarantee removal of the forced-black layer. App moves at 6s; primary switches at 10s. Disable Duo and hold near 90 degrees. Compare the 10s flash with Test B.").setPositiveButton("Start INNER",(d,w)->taskRoute(1,5)).setNeutralButton("Start COVER",(d,w)->taskRoute(2,5)).setNegativeButton("Cancel",null).show());
  button(col,"Test A2 — direct unused-panel OFF",()->new AlertDialog.Builder(this).setTitle("Direct power experiment").setMessage("Fixed primary mapping. App moves at 6s, returns at 16s. At 8s/18s release the ON tokens and issue one direct OFF request for the unused panel. Restore power at 14s/24s. This tests the separate direct-power API, not the rejected token priority combination. Disable Duo; hold near 90 degrees. Observe both panels and save the report.").setPositiveButton("Start INNER",(d,w)->taskRoute(1,6)).setNeutralButton("Start COVER",(d,w)->taskRoute(2,6)).setNegativeButton("Cancel",null).show());
  button(col,"Move app task — fixed INNER · 30 seconds",()->taskRoute(1));
  button(col,"Move app task — fixed COVER · 30 seconds",()->taskRoute(2));
  text(col,"Task route: start with Built-in task. Hold near 90°. A real fullscreen task is requested on D1 at 6 seconds and returned to D0 at 16 seconds, without primary swaps. Stay in the selected app; use notification Stop to abort. Do not use Home, Recents or split screen during the run.",15);
  button(col,"Live targets + touch — fixed INNER · 20 seconds",()->liveTargets(1));
  button(col,"Live targets + touch — fixed COVER · 20 seconds",()->liveTargets(2));
  text(col,"Live targets: disable Duo animation AND its custom wallpaper overlay first. Hold near 90°, stay in this app, and tap both screens. A numbered live card alternates between screens without requesting a primary swap. These are app-owned test windows, not another app moved between displays.",15);
  button(col,"Export Samsung ColorFade implementation · read-only",()->start(16));
  text(col,"Firmware export: export system display-service binaries and resource values for analysis. No screen switching or settings changes. Export may take a minute; share the resulting ZIP privately.",14);
  button(col,"ColorFade trace + renewed ON · 20 seconds",()->{ExperimentConfig c=ExperimentConfig.preset(true);c.renewal=500;startRecipe(c.encode(),true);});
  button(col,"ColorFade trace baseline · 20 seconds",()->startRecipe(ExperimentConfig.preset(false).encode(),true));
  text(col,"ColorFade tests record layer metadata, not screen images. Disable Duo and other experiments, hold near 90°, and let four switches run. Detailed tracing adds overhead.",14);
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
 private void fastOptions(boolean release){
  new AlertDialog.Builder(this).setTitle(brightnessHold?"B8: physical brightness hold":release?"B7: frozen shield + native return":"B6: frozen shield + geometry gate")
   .setMessage((brightnessHold?"B8 adds a 2-second physical brightness hold at the 10s switch and 24s native return. Leave brightness unchanged during the test. Before the switch, missing hardware samples trigger a tiny temporary brightness nudge and automatic clearing; saved brightness is unchanged. Missing samples or rejected API calls still cancel promotion. It may still flash: repeated writes cannot guarantee beating ColorFade. Save the ZIP. ":"")+"Disable Duo animation AND wallpaper. Hold near 90 degrees without rotating. Choose X or the built-in task. Same B4 sequence: app moves6s, mirrors8s, primary switch10s, staged mirror removal afterward. 0.5.11: freeze window content at8s to shield temporary resize. Motion pauses briefly by design. Primary switch requires committed shield; reveal waits for matching task/display geometry stable300ms plus750ms. Capture failure cancels promotion. No B5 delayed focus. "+(release?"At23s prepare fresh mirrors. At24s release the native display request while keeping both-ON overrides; wait for the actual OPENED/CLOSED physical primary, matching geometry and the selected app on D0. Revalidate until reveal; D1 may disappear normally. Other base states remain unverified. Then staged mirror removal. Final power release30s. Watch24s and30s separately. ":"Native cleanup30s is unchanged and may flash. ")+"Do not tap, press Home or Recents during the test. Run Start INNER first; then repeat Start COVER to test the opposite direction. Save each ZIP. Software readiness cannot guarantee uninterrupted pixels; compare with preserved B4.")
   .setPositiveButton("Start INNER",(d,w)->taskRoute(1,release?14:13)).setNeutralButton("Start COVER",(d,w)->taskRoute(2,release?14:13)).setNegativeButton("Cancel",null).show();
 }
 private void contentOptions(boolean focus){
  new AlertDialog.Builder(this).setTitle(focus?"B5: staged reveal + delayed focus":"B4: staged reveal only")
   .setMessage("Disable Duo animation AND custom wallpaper. Hold near 90 degrees without rotating. Choose the built-in task first. App moves at 6s; mirrors start at 8s; native primary switch at 10s. After verified placement plus 750ms, explicitly hide mirrors, then remove them 250ms after the hide transaction commits. This tests content recovery; it is not a confirmed fix. "+(focus?"At 13s, request focus again for the same verified task. No tap is injected. ":"No extra focus request after mirror removal. ")+"Do not touch until 18s. If content is missing, tap once at 18s and note whether it returns. Watch the moving orange block and native navigation. Cleanup at 30s may flash separately. Existing mirror watchdog remains; B3 baseline is retained. Save each report ZIP.")
   .setPositiveButton("Start INNER",(d,w)->taskRoute(1,focus?12:11)).setNeutralButton("Start COVER",(d,w)->taskRoute(2,focus?12:11)).setNegativeButton("Cancel",null).show();
 }
 private void bridgeOptions(boolean promote){
  new AlertDialog.Builder(this).setTitle(promote?"B3: live bridge during primary switch":"E: live bridge eligibility test")
   .setMessage("Disable Duo animation AND custom wallpaper. Hold near 90 degrees. Use the built-in task first. App moves at 6s; live window mirrors appear above the firmware ColorFade layer at 8s. "+(promote?"At 10s, request native primary promotion ONLY if the mirror transaction committed. Mirrors removed 750ms after verified promotion, or by 14s. No 16s return. Watch for flashes at 10s and at mirror removal.":"No primary promotion. Mirrors removed at 14s; app returns at 16s. Watch whether the counter stays live and whether either panel changes, freezes or distorts at 8s/14s.")+" Cleanup at 30s may flash separately. Mirrors have no input window; stay in the selected app. A transaction commit does not prove visible coverage. This attempts to cover ColorFade with live content, not disable ColorFade. Seven-second independent mirror-removal watchdog; no new persistent settings. Save ZIP. Run E before B3.")
   .setPositiveButton("Start INNER",(d,w)->taskRoute(1,promote?10:9)).setNeutralButton("Start COVER",(d,w)->taskRoute(2,promote?10:9)).setNegativeButton("Cancel",null).show();
 }
 private void desktopOptions(boolean power){
  new AlertDialog.Builder(this).setTitle(power?"C2: desktop policy + A2":"C: desktop policy only")
   .setMessage("Disable Duo animation and custom wallpaper. Hold near 90 degrees. Temporarily enables Force desktop mode on external displays, then restores its previous value. Other feature gates or cached policy may still block native bars. This does not draw replacement buttons. The app moves at 6s and returns at 16s. "+(power?"Unused-panel OFF at 8–14s / 18–24s, using A2's direct API. ":"Both displays remain on during app moves. ")+"Observe whether native navigation appears. First run: do not press Home or Recents, so the selected-app guard can complete. 30-second test; separate 45-second restoration watchdog. Cleanup may flash.")
   .setPositiveButton("Start INNER",(d,w)->taskRoute(1,power?8:7)).setNeutralButton("Start COVER",(d,w)->taskRoute(2,power?8:7)).setNegativeButton("Cancel",null).show();
 }
 private void promotionOptions(){
  new AlertDialog.Builder(this).setTitle("Test B: app first, primary second").setMessage("Disable Duo animation and wallpaper. Hold near 90°. At 6s the app moves to the other display. At 10s we request that physical display as primary while renewing both ON overrides. The same task is retargeted if Android moves its logical display. Observe navigation and any flash specifically at 10s. No automatic 16s return in this test. Normal control returns at 30s and may flash separately. This tests a hypothesis; flash suppression is not established.")
   .setPositiveButton("Start on COVER",(d,w)->taskRoute(2,4)).setNeutralButton("Start on INNER",(d,w)->taskRoute(1,4)).setNegativeButton("Cancel",null).show();
 }
 private void routeOptions(){
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,12,24,12);
  Spinner primary=new Spinner(this);primary.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Fixed INNER primary","Fixed COVER primary"}));box.addView(primary);
  CheckBox nav=new CheckBox(this);nav.setText("Reassert destination focus at 7s / 17s (navigation experiment)");box.addView(nav);
  CheckBox off=new CheckBox(this);off.setText("Turn off outgoing panel after verified app move");box.addView(off);
  text(box,"30 seconds. Disable Duo animation AND custom wallpaper. Hold near 90°. App moves at 6s, returns at 16s. Power option: D0 OFF at 8–14s; D1 OFF at 18–24s. Both ON before each move. Navigation diagnostics always included. A successful API call is not proof that bars appeared. No primary swap during app moves.",15);
  new AlertDialog.Builder(this).setTitle("Destination navigation / panel power").setView(box).setPositiveButton("Choose app",(d,w)->taskRoute(primary.getSelectedItemPosition()+1,(nav.isChecked()?1:0)|(off.isChecked()?2:0))).setNegativeButton("Cancel",null).show();
 }
 private void taskRoute(int primary){taskRoute(primary,0);}
 private void taskRoute(int primary,int options){
  if(!canStart())return;
  if(RecorderService.active){message("A recording is already running");return;}
  if(Bridge.remote==null){message("Connect Shizuku first");return;}
  if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},8);return;}
  java.util.ArrayList<String> names=new java.util.ArrayList<>();java.util.ArrayList<Intent> intents=new java.util.ArrayList<>();java.util.ArrayList<String> packages=new java.util.ArrayList<>();
  names.add("Built-in task — counter + touch (start here)");intents.add(new Intent(this,TaskProbeActivity.class));packages.add(getPackageName());
  java.util.List<android.content.pm.ResolveInfo> apps=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
  java.util.Collections.sort(apps,(a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
  java.util.HashSet<String> seen=new java.util.HashSet<>();seen.add(getPackageName());
  for(android.content.pm.ResolveInfo r:apps){String pkg=r.activityInfo.packageName;if(!seen.add(pkg))continue;Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);if(launch!=null){names.add(r.loadLabel(getPackageManager())+" · "+pkg);intents.add(launch);packages.add(pkg);}}
  new AlertDialog.Builder(this).setTitle("Select the exact app task to test").setItems(names.toArray(new String[0]),(d,index)->{
   if(RecorderService.active){message("A recording is already running");return;}
   ExperimentConfig c=ExperimentConfig.preset(true);c.duration=30;c.initial=primary;c.alternate=false;c.renewal=500;
   try{TaskGuard.validate(packages.get(index),c);
    startForegroundService(new Intent(this,RecorderService.class).putExtra("mode",17).putExtra("recipe",c.encode()).putExtra("taskPackage",packages.get(index)).putExtra("routeOptions",options).putExtra("screenshotOnly",screenshotOnly&&(options==13||options==14)).putExtra("brightnessHold",brightnessHold&&options==14));
    startActivity(intents.get(index).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle());
   }catch(Exception e){message("Could not start selected test: "+e);}
  }).setNegativeButton("Cancel",null).show();
 }
 private void liveTargets(int primary){
  if(!canStart())return;
  if(RecorderService.active){message("A recording is already running");return;}
  if(Bridge.remote==null){message("Connect Shizuku first");return;}
  if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},8);return;}
  ExperimentConfig c=ExperimentConfig.preset(true);c.initial=primary;c.alternate=false;c.renewal=500;
  startForegroundService(new Intent(this,RecorderService.class).putExtra("mode",15).putExtra("recipe",c.encode()));
  startActivity(new Intent(this,LiveTargetsActivity.class));
 }
 private void startRecipe(String recipe){startRecipe(recipe,false);}
 private void startRecipe(String recipe,boolean detailed){try{ExperimentConfig.parse(recipe);launch(detailed?15:14,recipe);}catch(Exception e){message(e.getMessage());}}
 private void recipeDialog(){
  ExperimentConfig saved;
  try{saved=ExperimentConfig.parse(getSharedPreferences("b8_recipes",0).getString("last",ExperimentConfig.preset(false).encode()));}catch(Exception e){saved=ExperimentConfig.preset(false);}
  final ExperimentConfig c=saved;
  ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,8,24,8);scroll.addView(box);
  text(box,"Recipes are saved locally and included in every ZIP. Use the baseline and both-ON presets first. Changing one option at a time makes comparisons useful.",15);
  EditText duration=number(box,"Recording seconds (10–30)",c.duration);
  Spinner initial=choice(box,"Initial primary",new String[]{"Unchanged","Inner","Cover"},c.initial);
  CheckBox alternate=check(box,"Automatically alternate primary displays",c.alternate);
  EditText settle=number(box,"Initial settling (1000–5000 ms)",c.settle),period=number(box,"Switch spacing (1000–5000 ms)",c.period),count=number(box,"Maximum switches (1–12; duration may limit)",c.switches);
  Spinner target=choice(box,"Samsung ON targets (logical IDs can swap panels)",new String[]{"None","D0","D1","Both"},c.target);
  Spinner api=choice(box,"Samsung API",new String[]{"Per-display token override","Global token override — experimental"},c.backend);
  text(box,"Global requires target Both. Hidden Samsung calls may fail or have no effect. Per-display logs on this phone identify 10000 as a 10-second timeout; 500 ms renewal keeps refreshing it. The global API integer remains unverified. App timers and token cleanup provide the bound, not an assumed OEM timeout.",14);
  Spinner order=choice(box,"Apply ON override",new String[]{"Before initial primary request","After initial settling"},c.order);
  EditText renewal=number(box,"Renew ON every ms (0=once; 250–1000)",c.renewal);
  Spinner wake=choice(box,"Wake-lock combination",new String[]{"None","Partial (CPU)","Dim screen","Bright screen","Full screen (legacy)"},c.wake);
  Spinner flags=choice(box,"Device-state request flags",new String[]{"0 — default","1 — cancel when base changes"},c.flags);
  Spinner cancel=choice(box,"Unexpected current-request cancellation",new String[]{"Stop and release","Retry after 500 ms (maximum 3)"},c.cancellation);
  EditText sample=number(box,"Power sampling interval (10–100 ms)",c.sample);
  CheckBox detailed=check(box,"Detailed ColorFade layer tracing",getSharedPreferences("b8_recipes",0).getBoolean("detailed",false));
  CheckBox snapshots=check(box,"Extra display/state dumps at switches (adds overhead)",c.snapshots);
  text(box,"Cancellation callbacks do not provide the cause. Superseded-request callbacks are labeled separately. Failed ON/wake options are logged; the remaining recipe continues. No persistent settings or raw Binder transaction guesses.",14);
  final java.util.concurrent.Callable<String> read=()->{ExperimentConfig n=new ExperimentConfig();n.duration=Integer.parseInt(duration.getText().toString());n.initial=initial.getSelectedItemPosition();n.alternate=alternate.isChecked();n.settle=Integer.parseInt(settle.getText().toString());n.period=Integer.parseInt(period.getText().toString());n.switches=Integer.parseInt(count.getText().toString());n.target=target.getSelectedItemPosition();n.backend=api.getSelectedItemPosition();n.order=order.getSelectedItemPosition();n.renewal=Integer.parseInt(renewal.getText().toString());n.wake=wake.getSelectedItemPosition();n.flags=flags.getSelectedItemPosition();n.cancellation=cancel.getSelectedItemPosition();n.sample=Integer.parseInt(sample.getText().toString());n.snapshots=snapshots.isChecked();n.validate();return n.encode();};
  button(box,"Save recipe + copy code",()->{try{String value=read.call();getSharedPreferences("b8_recipes",0).edit().putString("last",value).apply();((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Fold Power Lab recipe",value));message("Saved and copied. Paste this code to reproduce the test.");}catch(Exception e){message("Invalid recipe: "+e.getMessage());}});
  EditText imported=new EditText(this);imported.setHint("Paste recipe code here");box.addView(imported);
  button(box,"Import recipe (reopen editor to view)",()->{try{String value=ExperimentConfig.parse(imported.getText().toString().trim()).encode();getSharedPreferences("b8_recipes",0).edit().putString("last",value).apply();message("Imported. Close and reopen this editor.");}catch(Exception e){message("Invalid recipe: "+e.getMessage());}});
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Experiment recipe").setView(scroll).setNegativeButton("Close",null).setPositiveButton("Run",null).create();
  dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{String value=read.call();getSharedPreferences("b8_recipes",0).edit().putString("last",value).apply();getSharedPreferences("b8_recipes",0).edit().putBoolean("detailed",detailed.isChecked()).apply();startRecipe(value,detailed.isChecked());dialog.dismiss();}catch(Exception e){message("Invalid recipe: "+e.getMessage());}}));dialog.show();
 }
 private EditText number(LinearLayout box,String label,int value){text(box,label,15);EditText e=new EditText(this);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);e.setText(String.valueOf(value));box.addView(e);return e;}
 private Spinner choice(LinearLayout box,String label,String[] values,int value){text(box,label,15);Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(a);s.setSelection(value);box.addView(s);return s;}
 private CheckBox check(LinearLayout box,String label,boolean value){CheckBox c=new CheckBox(this);c.setText(label);c.setChecked(value);box.addView(c);return c;}
 private File latest(){String name=getSharedPreferences("b8_reports",0).getString("latest","");File f=new File(LabStorage.files(this),name);return f.isFile()?f:null;}
 private void start(int mode){launch(mode,null);}
 private boolean canStart(){
  android.content.SharedPreferences p=getSharedPreferences("standalone",0);
  if(!p.getBoolean("b8_lab_enabled",false)||p.getBoolean("enabled",false)){message("Turn Enable animation off and enable B8 Display Lab in Developer settings first.");return false;}
  return true;
 }
 private void launch(int mode,String recipe){if(!canStart())return;if(RecorderService.active){message("A recording is already running");return;}if(Bridge.remote==null){message("Connect Shizuku first");return;}if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},8);message("Allow notifications, then tap Record again");return;}startForegroundService(new Intent(this,RecorderService.class).putExtra("mode",mode).putExtra("recipe",recipe));}
 public void onResume(){super.onResume();if(state!=null)h.post(update);}public void onPause(){h.removeCallbacks(update);super.onPause();}
 protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==9&&result==RESULT_OK&&data!=null){File f=latest();if(f==null)return;new Thread(()->{try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(data.getData())){byte[] b=new byte[16384];int n;while((n=in.read(b))>=0)out.write(b,0,n);runOnUiThread(()->message("Saved. Attach the ZIP in our chat."));}catch(Exception e){runOnUiThread(()->message("Save failed: "+e));}}).start();}}
 private TextView text(LinearLayout l,String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(225,232,246));t.setPadding(0,12,0,16);l.addView(t);return t;}
 private void button(LinearLayout l,String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);l.addView(b);b.setOnClickListener(v->r.run());}
 private void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
