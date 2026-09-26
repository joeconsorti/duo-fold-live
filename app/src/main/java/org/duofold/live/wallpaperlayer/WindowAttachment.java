package org.duofold.live.wallpaperlayer;

import android.app.UiAutomation;
import android.os.*;
import android.view.SurfaceControl;
import java.lang.reflect.*;
import java.util.function.IntConsumer;

/** Calls the same authorized service connection directly, with an owned Binder callback. */
final class WindowAttachment {
 private static final String CALLBACK="android.view.accessibility.IAccessibilityInteractionConnectionCallback";
 final UiAutomation automation;final Method getConnection,connectionId,attach;
 final Class<?> callbackType;final int resultTransaction;
 int sequence;
 WindowAttachment(UiAutomation a)throws Exception{
  automation=a;
  Class<?> client=Class.forName("android.view.accessibility.AccessibilityInteractionClient");
  getConnection=client.getMethod("getConnection",int.class);
  connectionId=UiAutomation.class.getMethod("getConnectionId");
  callbackType=Class.forName(CALLBACK);
  Class<?> service=Class.forName("android.accessibilityservice.IAccessibilityServiceConnection");
  attach=service.getMethod("attachAccessibilityOverlayToWindow",int.class,int.class,SurfaceControl.class,callbackType);
  Field transaction=Class.forName(CALLBACK+"$Stub").getDeclaredField("TRANSACTION_sendAttachOverlayResult");transaction.setAccessible(true);
  resultTransaction=transaction.getInt(null);
 }
 String request(int window,SurfaceControl anchor,Handler main,IntConsumer result)throws Exception{
  int id=(Integer)connectionId.invoke(automation);if(id<0)throw new IllegalStateException("Accessibility connection not ready");
  Object connection=getConnection.invoke(null,id);if(!(connection instanceof IInterface))throw new IllegalStateException("No accessibility service connection");
  IBinder remote=((IInterface)connection).asBinder();if(remote==null||!remote.pingBinder())throw new IllegalStateException("Accessibility connection dead");
  final int request=++sequence;
  Binder callback=new Binder(){
   @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
    if(code==INTERFACE_TRANSACTION){if(reply!=null)reply.writeString(CALLBACK);return true;}
    if(code==resultTransaction){data.enforceInterface(CALLBACK);int response=data.readInt(),interaction=data.readInt();data.enforceNoDataAvail();if(interaction==request)main.post(()->result.accept(response));return true;}
    return super.onTransact(code,data,reply,flags);
   }
  };
  Object proxy=Proxy.newProxyInstance(callbackType.getClassLoader(),new Class<?>[]{callbackType},(self,method,args)->{
   if(method.getName().equals("asBinder"))return callback;
   if(method.getName().equals("sendAttachOverlayResult")){if((Integer)args[1]==request)main.post(()->result.accept((Integer)args[0]));return null;}
   if(method.getName().equals("toString"))return "Duo wallpaper attachment callback "+request;
   if(method.getName().equals("hashCode"))return System.identityHashCode(self);
   if(method.getName().equals("equals"))return self==args[0];
   throw new UnsupportedOperationException(method.getName());
  });
  attach.invoke(connection,request,window,anchor,proxy);
  return "connection="+id+" request="+request+" window="+window+" binderAlive=true";
 }
}
