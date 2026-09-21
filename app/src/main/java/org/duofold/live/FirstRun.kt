package org.duofold.live
import android.content.Context
import java.io.File
object FirstRun {
 fun required(c:Context):Boolean {
  val p=c.getSharedPreferences("first_run",0)
  val marker=p.getInt("state",0)
  val old=c.getSharedPreferences("standalone",0).all.isNotEmpty() ||
   listOf("photo.jpg","chosen-home-background.jpg","layer-report.txt").any { File(c.filesDir,it).exists() }
  val info=c.packageManager.getPackageInfo(c.packageName,0)
  val needed=OnboardingPolicy.needsSetup(marker,old,info.lastUpdateTime>info.firstInstallTime)
  if(marker==0)p.edit().putInt("state",if(needed)1 else 3).commit()
  return needed
 }
}
