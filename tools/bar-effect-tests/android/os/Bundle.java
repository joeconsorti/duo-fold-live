package android.os;
import java.util.*;
public class Bundle {
 private final Map<String,Object> values=new HashMap<>();
 public void putIntArray(String k,int[] v){values.put(k,v);}public int[] getIntArray(String k){return (int[])values.get(k);}
 public void putString(String k,String v){values.put(k,v);}public String getString(String k){return (String)values.get(k);}
}
