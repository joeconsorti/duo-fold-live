package android.hardware.display;
import android.os.IBinder;import java.util.*;
public class IDisplayManager {
 public static class Call {public IBinder token;public int state,id,timeout;Call(IBinder t,int s,int i,int m){token=t;state=s;id=i;timeout=m;}}
 public static List<Call> calls=new ArrayList<>();public static boolean rejectClear;
 public void setDisplayStateOverrideWithDisplayId(IBinder token,int state,int id,int timeout){calls.add(new Call(token,state,id,timeout));if(rejectClear&&state==0)throw new IllegalStateException("cleanup pending");}
 public static class Info {public String uniqueId;public int logicalWidth,logicalHeight,state=2,committedState=2;Info(int id){uniqueId=id==0?"inner":"cover";logicalWidth=id==0?2448:1248;logicalHeight=id==0?1848:1972;}}
 public Info getDisplayInfo(int id){return new Info(id);}
}
