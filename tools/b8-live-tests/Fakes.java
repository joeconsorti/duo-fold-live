package org.duofold.live.experimental.b8;
import java.io.File;
final class PowerExperiment {static Object service(String n,String c){return n.equals("window")?new android.view.IWindowManager():new android.os.IPowerManager();}}
final class LiveB8Power {
 static int panel=1,base=3,current=3,requests,releases,cleanupFailures,reportedState=2,reportedCommittedState=2;static boolean deferRemap,allowReady=true;boolean canceled;static Runnable onClose;
 static void reset(){panel=1;base=current=3;requests=releases=cleanupFailures=0;reportedState=reportedCommittedState=2;allowReady=true;deferRemap=false;onClose=null;}
 static final class Info {String id;int w,h;Info(int p){id=p==1?"inner":"cover";w=p==1?2448:1248;h=p==1?1848:1972;}}
 Object info(int id){return new Info(id==0?panel:3-panel);}static String physical(Object o){return ((Info)o).id;}
 static int number(Object o,String f){Info i=(Info)o;return f.equals("logicalWidth")?i.w:f.equals("logicalHeight")?i.h:f.equals("rotation")?0:f.equals("state")?reportedState:reportedCommittedState;}
 int primary(){return panel;}void on(){}void park(){}void request(int p){panel=p;current=p==1?4:5;requests++;}int state(boolean b){return b?base:current;}boolean ready(){return allowReady;}
 void nativeRelease(){releases++;current=base;if(!deferRemap)panel=base==3?1:2;}
 boolean close(){if(onClose!=null)onClose.run();if(cleanupFailures>0){cleanupFailures--;return false;}nativeRelease();return true;}
}
final class LiveB8Brightness {static int begun;static boolean failPrepared,pending;static Runnable onBegin,onPrepare;LiveB8Brightness(File f){}String preparationStatus(){return failPrepared?"failed":"completed";}boolean preparing(){return pending;}void prepareAsync(){prepare();}void prepare(){if(onPrepare!=null)onPrepare.run();}void requirePrepared(){if(onPrepare!=null)onPrepare.run();if(failPrepared)throw new IllegalStateException("test brightness unavailable");}void begin(String p){begun++;if(onBegin!=null)onBegin.run();}void close(){}}
final class LiveB8Bridge {
 static boolean allowCommit=true,failArm,allowRemoval=true;static LiveB8Bridge last;static int arms;boolean closed;
 LiveB8Bridge(File f,boolean freeze){last=this;}void arm(){arms++;if(failArm)throw new IllegalStateException("test arm failure");}boolean ready(){return allowCommit;}boolean isClosed(){return closed;}void followPhysicalStacks(){}String resources(){return "removeCommitted="+closed;}void reveal(){closed=allowRemoval;}void close(){closed=allowRemoval;}
}
final class LiveB8Task {
 String configurationReport(){return "fake density";}
 static int moves,retargets,focuses;static boolean workspace;boolean isWorkspace(){return workspace;}int[] workspaceTasks(){return workspace?new int[]{42,43,44}:new int[0];}static boolean home;boolean isHome(){return home;}String verifyHomePrimary(String p){return verifyNativePrimary(p);}static boolean primaryUnavailable;static String selected="test.app";static Runnable onRestore,onVerify;
 static String focusedPackage(){return selected;}LiveB8Task(File d,String p){}LiveB8Task(File d,String p,LiveB8Task remembered){}int taskId(){return 42;}void selectSourceOnly(){}void selectOnly(){}void moveSelected(){moves++;}void selectAndMove(){moves++;}
 String destinationGeometry(){return "destination";}String verifyPrimary(String p){retargets++;if(onVerify!=null)onVerify.run();if(primaryUnavailable)throw new IllegalStateException("mapping pending");return p;}String verifyNativePrimary(String p){if(onVerify!=null)onVerify.run();if(primaryUnavailable)throw new IllegalStateException("native pending");return p;}void focus(){focuses++;}void restore(){if(onRestore!=null)onRestore.run();}
}

final class LiveB8Evidence {LiveB8Evidence(File f,boolean c){}String report(){return "test evidence";}void record(File f,String s){}}

final class LiveB8Scene {
 static boolean mapping=true,removal=true;static int starts,refreshes,closes;static LiveB8Scene last;
 boolean closed;LiveB8Scene(File f,LiveB8Power p){starts++;last=this;}static boolean supported(){return true;}
 void refresh(LiveB8Power p){refreshes++;}boolean mapped(){return mapping;}Object handles(){return null;}
 float alpha=1;void opacity(float a){alpha=a;}
 boolean closed(){return closed;}void close(){closes++;closed=removal;}
}
