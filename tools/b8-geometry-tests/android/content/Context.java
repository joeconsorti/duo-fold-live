package android.content;
import java.util.HashMap;
import android.hardware.display.DisplayManager;
public class Context {
 public final MemoryPreferences prefs=new MemoryPreferences();
 public Context getApplicationContext(){return this;}
 public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
 public <T> T getSystemService(Class<T> type){return type.cast(new DisplayManager());}
 public static class MemoryPreferences implements SharedPreferences,SharedPreferences.Editor {
  public final HashMap<String,String> values=new HashMap<>();
  public int writes;
  public String getString(String key,String fallback){return values.getOrDefault(key,fallback);}
  public SharedPreferences.Editor edit(){return this;}
  public SharedPreferences.Editor putString(String key,String value){values.put(key,value);writes++;return this;}
  public void apply(){}
 }
}
