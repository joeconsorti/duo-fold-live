package org.duofold.live

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Publish only a complete ZIP. Failed copies remove their pending Downloads entry. */
internal object DiagnosticDownloads {
 fun save(context:Context,source:File):String {
  check(source.isFile&&source.length()>0){"Diagnostic archive is missing or empty"}
  val resolver=context.contentResolver
  val values=ContentValues().apply{
   put(MediaStore.Downloads.DISPLAY_NAME,source.name)
   put(MediaStore.Downloads.MIME_TYPE,"application/zip")
   put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/DuoFoldLive")
   put(MediaStore.Downloads.IS_PENDING,1)
  }
  val uri=checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)){"Unable to create download"}
  try{
   source.inputStream().buffered().use{input->
    checkNotNull(resolver.openOutputStream(uri,"w")){"Unable to open download"}.use{output->
     check(input.copyTo(output)==source.length()){"Incomplete ZIP copy"}
    }
   }
   check(resolver.update(uri,ContentValues().apply{put(MediaStore.Downloads.IS_PENDING,0)},null,null)==1){"Unable to finish download"}
   return source.name
  }catch(e:Exception){runCatching{resolver.delete(uri,null,null)};throw e}
 }
}
