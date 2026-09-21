package com.mccal.folio
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
internal object WallpaperFiles {
 fun photoFile(c:Context)=File(c.filesDir,"chosen-home-background.jpg")
 fun savePhoto(c:Context,uri:Uri):Bitmap{
  val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver,uri)){decoder,info,_->
   val largest=maxOf(info.size.width,info.size.height)
   if(largest>1920)decoder.setTargetSampleSize((largest+1919)/1920)
   decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
  }
  val tmp=File(c.filesDir,"chosen-home-background.tmp")
  tmp.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,92,it)){"Could not save image"}}
  check(tmp.renameTo(photoFile(c))){"Could not store selected image"}
  return bitmap
 }
 fun exportResources(c:Context):Intent{
  val pkg="com.samsung.android.wallpaper.res"
  val app=c.packageManager.getApplicationInfo(pkg,0)
  val sources=listOf(app.sourceDir)+(app.splitSourceDirs?.toList()?:emptyList())
  val folder=File(c.cacheDir,"exports").apply{mkdirs()}
  val zip=File(folder,"Samsung-wallpaper-resources.zip")
  val tmp=File(folder,"Samsung-wallpaper-resources.tmp")
  try{
   ZipOutputStream(tmp.outputStream().buffered()).use{out->
    out.putNextEntry(ZipEntry("device.txt"))
    out.write("${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE}\nPackage $pkg\nInstalled wallpaper resources only; no app data or selected photos.\n".toByteArray());out.closeEntry()
    sources.forEachIndexed{i,path->
     out.putNextEntry(ZipEntry(if(i==0)"base.apk" else "split-$i.apk"))
     File(path).inputStream().use{it.copyTo(out)};out.closeEntry()
    }
   }
   check(tmp.renameTo(zip)){"Could not finish resource export"}
  }finally{tmp.delete()}
  val uri=FileProvider.getUriForFile(c,c.packageName+".files",zip)
  return Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
   type="application/zip";putExtra(Intent.EXTRA_STREAM,uri)
   clipData=android.content.ClipData.newRawUri("Wallpaper resources",uri)
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  },"Share wallpaper resources")
 }
 fun exportEngine(c:Context):Intent{
  val info=runCatching{WallpaperManager.getInstance(c).wallpaperInfo}.getOrNull()
  val pkg=info?.packageName?:"com.samsung.android.wallpaper.live"
  val app=c.packageManager.getApplicationInfo(pkg,0)
  val sources=listOf(app.sourceDir)+(app.splitSourceDirs?.toList()?:emptyList())
  val folder=File(c.cacheDir,"exports").apply{mkdirs()}
  val zip=File(folder,"Samsung-wallpaper-engine.zip")
  ZipOutputStream(zip.outputStream().buffered()).use{out->
   out.putNextEntry(ZipEntry("device.txt"));out.write("${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE}\nPackage $pkg\nService ${info?.serviceName}\nInstalled application code only; no app data.\n".toByteArray());out.closeEntry()
   sources.forEachIndexed{i,path->out.putNextEntry(ZipEntry(if(i==0)"base.apk" else "split-$i.apk"));File(path).inputStream().use{it.copyTo(out)};out.closeEntry()}
  }
  val uri=FileProvider.getUriForFile(c,c.packageName+".files",zip)
  return Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/zip";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share wallpaper engine")
 }
}
