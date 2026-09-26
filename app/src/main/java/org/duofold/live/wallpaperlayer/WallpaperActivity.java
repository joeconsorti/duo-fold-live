package org.duofold.live.wallpaperlayer;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.widget.*;import rikka.shizuku.Shizuku;import java.io.*;
public class WallpaperActivity extends Activity {
 ImageView coverPreview,innerPreview; Bitmap previewBitmap; long previewStamp=-1;
 TextView status,backgroundStatus; Handler h=new Handler(); IBinder remote; boolean connecting=false,startOnConnect=false; File photo; Shizuku.UserServiceArgs args;
 final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){remote=b;connecting=false;if(startOnConnect){startOnConnect=false;startRemote();}else{h.removeCallbacks(poll);h.post(poll);}}public void onServiceDisconnected(ComponentName n){remote=null;status.setText("Shizuku connection ended. Reopen Shizuku if needed; Disable wallpaper still removes the photo layer.");}};
 final Shizuku.OnRequestPermissionResultListener permission=(a,b)->runOnUiThread(()->{String message=b==0?"Authorized. Choose a photo and enable wallpaper.":"Shizuku permission denied.";status.setText(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();});
 public void onCreate(Bundle s){super.onCreate(s);photo=new File(getFilesDir(),"photo.jpg");if(!photo.exists()){File staged=new File(getFilesDir(),"chosen-home-background.jpg");if(staged.exists())try{java.nio.file.Files.copy(staged.toPath(),photo.toPath());}catch(Exception ignored){}}LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(18),dp(20),dp(24));root.setBackgroundColor(0xff080a0e);
 button(root,"‹ Back",this::finish);
 text(root,"Custom wallpaper",28,0xffffffff);
 text(root,"Your photo. Both screens. One seamless setup.",16,0xffb8c7d9);
 LinearLayout steps=card(root);
 text(steps,"Make it yours",22,0xffffffff);
 text(steps,"1. Choose a photo\n2. Enable your wallpaper\n3. Fold and unfold to enjoy it",16,0xffccd1e0);
 status=new TextView(this);status.setTextColor(0xffb8d4ff);status.setTextSize(14);status.setPadding(0,dp(12),0,dp(12));
 button(steps,"Choose / change photo",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),2));
 button(steps,"Enable custom wallpaper",()->{try{if(!photo.exists()){status.setText("Choose a photo first.");return;}if(!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=0){status.setText("Authorize Shizuku first.");return;}if(remote!=null&&remote.isBinderAlive()){startRemote();return;}if(connecting)return;startOnConnect=true;connecting=true;args=WallpaperRestore.args(this);Shizuku.bindUserService(args,connection);status.setText("Connecting…");}catch(Exception e){connecting=false;status.setText(e.toString());}});
 button(steps,"Disable custom wallpaper",()->{try{WallpaperRestore.setEnabled(this,false);status.setText("Stop requested; removing photo layer.");h.removeCallbacks(poll);h.postDelayed(poll,200);}catch(Exception e){status.setText(e.toString());}});
 steps.addView(status);
 text(steps,"Your selection stays saved across updates. Choose a new photo any time. Disabling shows Samsung’s wallpaper again.",14,0xffb8c7d9);
 LinearLayout previews=card(root);text(previews,"On your screens",22,0xffffffff);
 text(previews,"Approximate centered crops · Home icons are not shown. The same photo fills each screen.",14,0xffb8c7d9);
 LinearLayout pair=new LinearLayout(this);pair.setOrientation(LinearLayout.HORIZONTAL);previews.addView(pair);
 coverPreview=preview(pair,"Folded",1248f/1972f);innerPreview=preview(pair,"Unfolded",2448f/1848f);
 backgroundStatus=new TextView(this);backgroundStatus.setTextColor(0xffb8c7d9);root.addView(backgroundStatus);
 LinearLayout connectionCard=card(root);text(connectionCard,"Connection",22,0xffffffff);
 text(connectionCard,"Shizuku keeps your custom background and fold animation working together. Authorize it here if needed.",14,0xffb8c7d9);
 button(connectionCard,"Authorize Shizuku",()->{String result=org.duofold.live.ShizukuAccess.INSTANCE.request(this,1);org.duofold.live.ShizukuAccess.INSTANCE.show(this,result);});
 LinearLayout lockCard=card(root);text(lockCard,"Lock screens",22,0xffffffff);
 text(lockCard,"Optional: apply this photo to both lock screens too. This remains until you change your lock-screen wallpaper in Samsung settings.",14,0xffb8c7d9);
 button(lockCard,"Apply this photo to both lock screens",()->{if(!photo.exists()){status.setText("Choose a photo first.");return;}h.removeCallbacks(poll);status.setText("Applying lock-screen photos…");new Thread(()->{String result;try{result=LockPhotoApplier.apply(this,photo);java.nio.file.Files.write(new File(getFilesDir(),"lock-photo-status.txt").toPath(),result.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){result="Lock photo action failed: "+e;}final String message=result;runOnUiThread(()->{status.setText(brief(message));Toast.makeText(this,"Lock-screen action finished; see status",Toast.LENGTH_LONG).show();h.postDelayed(poll,10000);});},"Apply lock photos").start();});
 LinearLayout help=card(root);text(help,"Need help?",22,0xffffffff);
 text(help,"Copy your background report to include with an issue report.",14,0xffb8c7d9);
 button(help,"Copy background report",()->{String report=currentReport();((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Duo Fold Live background",report));Toast.makeText(this,"Background report copied",Toast.LENGTH_SHORT).show();});
 text(help,"Unlock flash test: start capture, wait for Capturing, go Home, lock and unlock once. Wait 35 seconds, return here and share the ZIP. Metadata can include app/window names; no screen images or audio are recorded.",14,0xffb8c7d9);
 button(help,"Start 30-second unlock capture",()->status.setText(UnlockTrace.start()));
 button(help,"Share unlock trace",()->{try{if(UnlockTrace.capturing()){status.setText("Wait until capture finishes.");return;}startActivity(UnlockTrace.share(this));}catch(Exception e){status.setText(e.getMessage());}});
 if(getIntent().getBooleanExtra("developer",false)){
 LinearLayout developer=card(root);text(developer,"Wallpaper developer settings",22,0xffffffff);
 CheckBox renderer=new CheckBox(this);renderer.setText("Native compositor photo · apply on next enable");renderer.setTextColor(-1);renderer.setChecked(!new File(getFilesDir(),"legacy-renderer").exists());renderer.setOnCheckedChangeListener((v,checked)->{try{File marker=new File(getFilesDir(),"legacy-renderer");if(checked)marker.delete();else marker.createNewFile();status.setText("Renderer saved. Disable, then enable the wallpaper to apply.");}catch(Exception e){status.setText(e.toString());}});developer.addView(renderer);
 CheckBox wake=new CheckBox(this);wake.setText("Independent wake photo · apply on next enable");wake.setTextColor(-1);wake.setChecked(!new File(getFilesDir(),"disable-wake-layer").exists());wake.setOnCheckedChangeListener((v,checked)->{try{File marker=new File(getFilesDir(),"disable-wake-layer");if(checked)marker.delete();else marker.createNewFile();status.setText("Wake renderer saved. Disable, then enable wallpaper to apply.");}catch(Exception e){status.setText(e.toString());}});developer.addView(wake);
 }
 ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
 scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
 Shizuku.addRequestPermissionResultListener(permission);refreshPreviews();
 if(getIntent().getBooleanExtra("auto_enable",false)){
  getIntent().removeExtra("auto_enable");
  h.post(()->{try{
   if(!photo.exists()||!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=0){status.setText("Choose a photo and authorize Shizuku, then enable wallpaper.");return;}
   startOnConnect=true;connecting=true;args=WallpaperRestore.args(this);Shizuku.bindUserService(args,connection);
  }catch(Exception e){connecting=false;status.setText("Could not start custom wallpaper: "+e);}});
 }

 }
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 void text(LinearLayout parent,String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(6),0,dp(10));parent.addView(t);}
 LinearLayout card(LinearLayout parent){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(16),dp(18),dp(16));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xff171a20);bg.setCornerRadius(dp(24));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(16);parent.addView(c,lp);return c;}
 void button(LinearLayout l,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(0xff101c2b);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff9dc4ff));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);l.addView(b,lp);}
 ImageView preview(LinearLayout pair,String label,float aspect){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(4),0,dp(4),0);pair.addView(box,new LinearLayout.LayoutParams(0,-2,1));text(box,label,15,0xffffffff);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(0xff242830);image.setContentDescription(label+" wallpaper crop preview");int width=dp(120);box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);box.addView(image,new LinearLayout.LayoutParams(width,Math.round(width/aspect)));return image;}
 void refreshPreviews(){if(photo==null||!photo.exists()||photo.lastModified()==previewStamp)return;previewStamp=photo.lastModified();new Thread(()->{BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;Bitmap bitmap=BitmapFactory.decodeFile(photo.getPath(),options);runOnUiThread(()->{if(isDestroyed()){if(bitmap!=null)bitmap.recycle();return;}Bitmap old=previewBitmap;previewBitmap=bitmap;coverPreview.setImageBitmap(bitmap);innerPreview.setImageBitmap(bitmap);if(old!=null&&old!=bitmap)old.recycle();});},"wallpaper-preview").start();}
 String brief(String message){if(message==null)return "Wallpaper status unavailable";String line=message.split("\\n",2)[0];return line.length()>240?line.substring(0,240)+"…":line;}
 void startRemote(){startOnConnect=false;try{WallpaperRestore.setEnabled(this,true);WallpaperRestore.resume(this);}catch(Exception e){status.setText(e.toString());return;}try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(photo,ParcelFileDescriptor.MODE_READ_ONLY)){status.setText(brief(call(1,fd)));h.removeCallbacks(poll);h.postDelayed(poll,500);}catch(Exception e){status.setText(e.toString());}}
 String call(int code,ParcelFileDescriptor fd)throws Exception{if(remote==null)throw new IllegalStateException("Not connected");Parcel p=Parcel.obtain(),r=Parcel.obtain();try{p.writeInterfaceToken(HostLauncher.TOKEN);if(code==1)p.writeTypedObject(fd,0);remote.transact(code,p,r,0);r.readException();return r.readString();}finally{p.recycle();r.recycle();}}
 final Runnable poll=new Runnable(){public void run(){try{if(UnlockTrace.capturing()||UnlockTrace.status().startsWith("Capture saved"))status.setText(UnlockTrace.status());backgroundStatus.setText((WallpaperRestore.enabled(WallpaperActivity.this)?"Enabled":"Disabled")+" · "+WallpaperRestore.status+"\n"+UnlockTrace.status());}catch(Exception e){backgroundStatus.setText("Background status unavailable. Copy the background report for details.");}h.postDelayed(this,1000);}};
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==2&&result==RESULT_OK&&data!=null){status.setText("Loading photo…");new Thread(()->{try{Bitmap b=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),data.getData()),(d,i,s)->{d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);int m=Math.max(i.getSize().getWidth(),i.getSize().getHeight());if(m>1600)d.setTargetSampleSize((m+1599)/1600);});File tmp=new File(getFilesDir(),"photo-next.jpg");try(FileOutputStream out=new FileOutputStream(tmp)){if(!b.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("Photo encoding failed");}java.nio.file.Files.move(tmp.toPath(),photo.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);b.recycle();runOnUiThread(()->{status.setText("Photo saved. If enabled, it updates automatically; otherwise tap Enable custom wallpaper.");refreshPreviews();});}catch(Exception e){runOnUiThread(()->status.setText("Photo failed: "+e));}}).start();}}
 protected void onResume(){super.onResume();refreshPreviews();h.removeCallbacks(poll);h.post(poll);h.postDelayed(()->reconnectForReport(),500);}
 void reconnectForReport(){try{if(remote!=null||connecting||!new File(getFilesDir(),"layer-report.txt").exists()||!Shizuku.pingBinder()||Shizuku.checkSelfPermission()!=0)return;connecting=true;startOnConnect=false;args=WallpaperRestore.args(this);Shizuku.bindUserService(args,connection);}catch(Exception e){connecting=false;}}
 String currentReport(){String lock="";try{lock=new String(java.nio.file.Files.readAllBytes(new File(getFilesDir(),"lock-photo-status.txt").toPath()),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception ignored){}return "Duo Fold Live "+org.duofold.live.BuildConfig.VERSION_NAME+"\nSaved wallpaper enabled: "+WallpaperRestore.enabled(this)+"\nRecovery: "+WallpaperRestore.status+"\nKeep-awake saved choice: "+getSharedPreferences("standalone",0).getBoolean("auto_keep_cover_awake",true)+"\n"+org.duofold.live.FoldAwakeDefault.status+"\n"+lock+"\n\n"+readSavedReport()+"\nObserver connection: "+(remote!=null&&remote.isBinderAlive()?"connected":"disconnected; host trace is saved independently");}
 String readSavedReport(){try{
  File f=new File(getFilesDir(),"layer-report.txt");String report=new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8);
  long age=Math.max(0,System.currentTimeMillis()-f.lastModified());
  return "Duo Fold Live background diagnostics\nReport source: wallpaper host file\nRead elapsed ms: "+SystemClock.elapsedRealtime()+"\nSaved report age: "+age+" ms"+(age>15000?" — STALE; host activity unconfirmed":"")+"\n\n"+report;
 }catch(Exception e){return "No saved host report available: "+e+"\nEnable custom wallpaper to start a fresh session.";}}
 protected void onPause(){super.onPause();h.removeCallbacks(poll);}
 protected void onDestroy(){h.removeCallbacksAndMessages(null);Shizuku.removeRequestPermissionResultListener(permission);super.onDestroy();}
}
