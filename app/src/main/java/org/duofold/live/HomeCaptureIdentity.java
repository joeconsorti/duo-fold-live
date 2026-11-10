package org.duofold.live;

/** Read-only focused Home/Recents identity. No task moves, launches or focus changes. */
final class HomeCaptureIdentity {
 static String focused(boolean allowRecents)throws Exception{
  Object wm=MovingSceneCapture.service("window","android.view.IWindowManager");
  if((boolean)Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked");
  Object atm=MovingSceneCapture.service("activity_task","android.app.IActivityTaskManager");
  Object root=Class.forName("android.app.IActivityTaskManager").getMethod("getFocusedRootTaskInfo").invoke(atm);
  return identity(root,allowRecents);
 }
 static String identity(Object root,boolean allowRecents)throws Exception{
  if(root==null)throw new IllegalStateException("No focused launcher");
  Object configuration=root.getClass().getField("configuration").get(root);
  Object window=configuration.getClass().getField("windowConfiguration").get(configuration);
  int type=((Number)window.getClass().getMethod("getActivityType").invoke(window)).intValue();
  int mode=((Number)window.getClass().getMethod("getWindowingMode").invoke(window)).intValue();
  int display=root.getClass().getField("displayId").getInt(root);
  if((type!=2&&!(allowRecents&&type==3))||display!=0||mode!=1)throw new IllegalStateException("Home/Recents no longer focused");
  Object component=root.getClass().getField("topActivity").get(root);
  if(component==null)throw new IllegalStateException("Launcher component unavailable");
  return root.getClass().getField("userId").getInt(root)+":"+type+":"+component.toString();
 }
}
