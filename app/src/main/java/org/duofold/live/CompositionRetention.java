package org.duofold.live;
import java.io.File;
import java.util.*;
/** Elapsed-time folder names are not chronologically sortable across boots or digit widths. */
final class CompositionRetention {
 static File[] expired(File[] files,int keep){
  return Arrays.stream(files).filter(File::isDirectory)
   .sorted(Comparator.comparingLong(File::lastModified).reversed().thenComparing(File::getName))
   .skip(Math.max(0,keep)).toArray(File[]::new);
 }
}
