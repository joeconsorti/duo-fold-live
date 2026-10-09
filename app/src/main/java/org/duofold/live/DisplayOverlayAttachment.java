package org.duofold.live;

import android.app.UiAutomation;
import android.os.*;
import android.view.SurfaceControl;
import java.lang.reflect.*;
import java.util.function.IntConsumer;

/**
 * Attaches a SurfaceControl to a display through the registered host's own accessibility connection
 * (IAccessibilityServiceConnection.attachAccessibilityOverlayToDisplay). The window manager then parents the layer
 * inside that display's overlay hierarchy, which a free-standing layer stack placement does not guarantee on the
 * second panel. Same pattern as wallpaperlayer.WindowAttachment, but for a display instead of a window.
 */
final class DisplayOverlayAttachment {
    private static final String CALLBACK = "android.view.accessibility.IAccessibilityInteractionConnectionCallback";
    private final UiAutomation automation;
    private final Method getConnection, connectionId, attach;
    private final Class<?> callbackType;
    private final int resultTransaction;
    private int sequence;

    DisplayOverlayAttachment(UiAutomation a) throws Exception {
        automation = a;
        Class<?> client = Class.forName("android.view.accessibility.AccessibilityInteractionClient");
        getConnection = client.getMethod("getConnection", int.class);
        connectionId = UiAutomation.class.getMethod("getConnectionId");
        callbackType = Class.forName(CALLBACK);
        Class<?> service = Class.forName("android.accessibilityservice.IAccessibilityServiceConnection");
        Method found = null;
        StringBuilder names = new StringBuilder();
        for (Method m : service.getMethods()) {
            if (m.getName().startsWith("attachAccessibilityOverlay")) names.append(m).append("; ");
            if (m.getName().equals("attachAccessibilityOverlayToDisplay")) found = m;
        }
        if (found == null) throw new NoSuchMethodException("attachAccessibilityOverlayToDisplay missing; have: " + names);
        Class<?>[] t = found.getParameterTypes();
        if (t.length != 4 || t[0] != int.class || t[1] != int.class || t[2] != SurfaceControl.class)
            throw new NoSuchMethodException("Unexpected signature: " + found);
        attach = found;
        Field transaction = Class.forName(CALLBACK + "$Stub").getDeclaredField("TRANSACTION_sendAttachOverlayResult");
        transaction.setAccessible(true);
        resultTransaction = transaction.getInt(null);
    }

    String request(int displayId, SurfaceControl layer, Handler main, IntConsumer result) throws Exception {
        int id = (Integer) connectionId.invoke(automation);
        if (id < 0) throw new IllegalStateException("Accessibility connection not ready");
        Object connection = getConnection.invoke(null, id);
        if (!(connection instanceof IInterface)) throw new IllegalStateException("No accessibility service connection");
        IBinder remote = ((IInterface) connection).asBinder();
        if (remote == null || !remote.pingBinder()) throw new IllegalStateException("Accessibility connection dead");
        final int request = ++sequence;
        Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(CALLBACK); return true; }
                if (code == resultTransaction) {
                    data.enforceInterface(CALLBACK);
                    int response = data.readInt(), interaction = data.readInt();
                    data.enforceNoDataAvail();
                    if (interaction == request) main.post(() -> result.accept(response));
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        Object proxy = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType}, (self, method, args) -> {
            switch (method.getName()) {
                case "asBinder": return callback;
                case "sendAttachOverlayResult": if ((Integer) args[1] == request) main.post(() -> result.accept((Integer) args[0])); return null;
                case "toString": return "Duo display overlay callback " + request;
                case "hashCode": return System.identityHashCode(self);
                case "equals": return self == args[0];
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
        attach.invoke(connection, request, displayId, layer, proxy);
        return "connection=" + id + " request=" + request + " display=" + displayId;
    }
}
