package org.duofold.live.experimental.b8;
import java.io.*;
/** Counts protobuf packet fields without loading large traces or claiming pixel visibility. */
final class TraceCoverage {
 static String inspect(File file){
  long[] counts=new long[4];String error="";
  try(RandomAccessFile f=new RandomAccessFile(file,"r")){
   while(f.getFilePointer()<f.length()){
    long tag=varint(f);if(tag==10){long n=varint(f),end=end(f,n,f.length());
     while(f.getFilePointer()<end){long t=varint(f);int field=(int)(t>>>3);if(field==93)counts[0]++;if(field==94)counts[1]++;if(field==50||field==133)counts[2]++;skip(f,t,end);}counts[3]++;
    }else skip(f,tag,f.length());
   }
  }catch(Exception e){error="Incomplete/unsupported parse: "+e.toString();}
  return "Layer snapshot fields="+counts[0]+"\nTransaction fields="+counts[1]+"\nCompressed packet fields (not expanded)="+counts[2]+"\nPackets inspected="+counts[3]+"\n"+error+"\nNonzero counts establish payload presence only, not complete coverage, opacity, GPU completion or visible pixels. Zero counts with compression/parse errors are inconclusive. Buffer loss and actual ColorFade layers require trace analysis.\n";
 }
 private static long end(RandomAccessFile f,long n,long limit)throws IOException{long p=f.getFilePointer();if(n<0||n>limit-p)throw new IOException("Truncated length");return p+n;}
 private static void skip(RandomAccessFile f,long tag,long limit)throws IOException{if(tag>>>3==0)throw new IOException("Invalid field");switch((int)(tag&7)){case 0:varint(f);break;case 1:f.seek(end(f,8,limit));break;case 2:long n=varint(f);f.seek(end(f,n,limit));break;case 5:f.seek(end(f,4,limit));break;default:throw new IOException("Unsupported wire type");}if(f.getFilePointer()>limit)throw new IOException("Field exceeds packet");}
 private static long varint(RandomAccessFile f)throws IOException{long v=0;for(int i=0;i<10;i++){int b=f.readUnsignedByte();if(i==9&&(b&254)!=0)throw new IOException("Invalid varint");v|=(long)(b&127)<<(7*i);if((b&128)==0)return v;}throw new IOException("Invalid varint");}
}
