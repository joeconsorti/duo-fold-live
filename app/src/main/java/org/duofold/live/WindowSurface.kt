package org.duofold.live

import android.view.SurfaceControl
import android.view.View
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * The window SurfaceControl behind an attached overlay view, or null while its root is not ready.
 * Android 17 blocks plain reflection on ViewRootImpl.getSurfaceControl() for app code
 * ("api=blocked ... using reflection: denied", observed on One UI 9.0), so it is resolved through HiddenApiBypass.
 */
object WindowSurface {
 fun of(view:View):SurfaceControl?{
  val root=HiddenApiBypass.invoke(View::class.java,view,"getViewRootImpl") ?: return null
  return HiddenApiBypass.invoke(root.javaClass,root,"getSurfaceControl") as SurfaceControl?
 }
}
