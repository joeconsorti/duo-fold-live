package org.duofold.live.experimental.b8;

import android.content.Context;
import java.io.File;

/** Keep lab reports and inventories separate from Duo's wallpaper/user files. */
final class LabStorage {
 static File files(Context context) {
  File folder=new File(context.getFilesDir(),"b8-lab");
  if(!folder.isDirectory()&&!folder.mkdirs())throw new IllegalStateException("Cannot create B8 report folder");
  return folder;
 }
}
