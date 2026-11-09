package jp.reader.manga;
import java.io.*;import java.nio.file.*;
class TestPolicy {
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s);System.out.println("PASS "+s);}
 static File create(File dir,String name,int n,long time)throws Exception {File f=new File(dir,name);try(RandomAccessFile r=new RandomAccessFile(f,"rw")){r.setLength(n);}f.setLastModified(time);return f;}
 public static void main(String[]args)throws Exception{File dir=Files.createTempDirectory("policy").toFile();File a=create(dir,"a.zipcache",200,10),b=create(dir,"b.zipcache",200,20),c=create(dir,"c.zipcache",200,30);CachePolicy.touch(b);CachePolicy.ensure(dir.listFiles(),200,600,"a.zipcache");ok(a.exists()&&b.exists()&&!c.exists(),"容量超過時に開いている本と最近使った本を残す");try{CachePolicy.ensure(dir.listFiles(),700,600,"a.zipcache");throw new AssertionError("limit");}catch(IOException expected){ok(true,"大きすぎるダウンロードを拒否");}for(File f:dir.listFiles())f.delete();dir.delete();}
}
