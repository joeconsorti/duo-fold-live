package android.os;
import java.io.*;
import java.util.concurrent.*;
public class ParcelFileDescriptor implements AutoCloseable {
 private static final ConcurrentHashMap<FileDescriptor,OutputStream> outputs=new ConcurrentHashMap<>();
 private final FileDescriptor fd=new FileDescriptor();private InputStream in;private OutputStream out;
 public static ParcelFileDescriptor[] createPipe()throws IOException{
  var r=new ParcelFileDescriptor();var w=new ParcelFileDescriptor();var pipe=new PipedInputStream(8192);r.in=pipe;w.out=new PipedOutputStream(pipe);outputs.put(w.fd,w.out);return new ParcelFileDescriptor[]{r,w};
 }
 public FileDescriptor getFileDescriptor(){return fd;}
 public static OutputStream output(FileDescriptor fd){return outputs.get(fd);}
 public void close()throws IOException{if(in!=null)in.close();if(out!=null){outputs.remove(fd);out.close();}}
 public static class AutoCloseInputStream extends FilterInputStream {public AutoCloseInputStream(ParcelFileDescriptor p){super(p.in);}}
}
