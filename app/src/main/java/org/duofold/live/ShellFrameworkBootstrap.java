package org.duofold.live;
import java.lang.reflect.InvocationTargetException;

/** Process-local setup for a standalone app_process UI host. */
public final class ShellFrameworkBootstrap {
 private ShellFrameworkBootstrap() {}
 public static String initialize() throws Exception {
  // Private memory has no system-server cache invalidations: never cache queries against it.
  Class.forName("android.app.PropertyInvalidatedCache").getMethod("disableForTestMode").invoke(null);
  Class<?> memory;
  try { memory=Class.forName("com.android.internal.os.ApplicationSharedMemory"); }
  catch(ClassNotFoundException olderPlatform) { return "Shared-memory API absent; caches disabled"; }
  return initializeMemory(memory);
 }
 static String initializeMemory(Class<?> memory) throws Exception {
  synchronized(ShellFrameworkBootstrap.class) {
   try {
    Object existing=memory.getMethod("getInstance").invoke(null);
    if(existing!=null)return "Existing framework shared memory retained; caches disabled";
   } catch(InvocationTargetException failure) {
    Throwable cause=failure.getCause();
    if(!(cause instanceof IllegalStateException) || !"ApplicationSharedMemory not initialized".equals(cause.getMessage()))throw failure;
   }
   Object instance=memory.getMethod("create").invoke(null);
   try { memory.getMethod("setInstance",memory).invoke(null,instance); }
   catch(Exception failure) {
    try { memory.getMethod("close").invoke(instance); } catch(Exception cleanup) { failure.addSuppressed(cleanup); }
    throw failure;
   }
   return "Standalone framework shared memory initialized; caches disabled";
  }
 }
}
