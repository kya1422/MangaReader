package jp.reader.manga;
import java.nio.file.*;
import java.util.*;
class TestRar {
 public static void main(String[] args)throws Exception{
  List<byte[]> previous=null;
  for(String path:args){
   if(path.contains("solid")||path.contains("password")){
    try{RemoteBooks.open(new Dav(Files.readAllBytes(Paths.get(path))),"https://example.com/test.rar","test.rar");throw new AssertionError("unsupported RAR was accepted");}
    catch(java.io.IOException expected){System.out.println("PASS unsupported archive rejected: "+expected.getMessage());}
    continue;
   }
   byte[] input=Files.readAllBytes(Paths.get(path));Dav d=new Dav(input);
   RemoteZip z=RemoteBooks.open(d,"https://example.com/test.rar","test.rar");
   if(z.pages.size()!=2)throw new AssertionError("pages "+z.pages.size());
   List<byte[]> images=new ArrayList<>();
   for(int i=0;i<z.pages.size();i++){
    long before=d.transferred;boolean wide=z.isWide(i);long orientationBytes=d.transferred-before;
    if(wide)throw new AssertionError("test images expected portrait");
    System.out.println("orientation range bytes="+orientationBytes);
    byte[] img=z.imageData(i);images.add(img);if(img.length<1000)throw new AssertionError("image");
    android.graphics.Bitmap b=z.bitmap(i,500,500);
    System.out.println(path+" "+z.pages.get(i).name+" bytes="+img.length+" wide="+z.isWide(i)+" bitmap="+b.getWidth());
   }
   if(previous!=null)for(int i=0;i<images.size();i++)if(!Arrays.equals(previous.get(i),images.get(i)))throw new AssertionError("RAR4/RAR5 content differs");
   java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
   try{java.util.concurrent.Future<byte[]> first=pool.submit(()->z.imageData(0));java.util.concurrent.Future<byte[]> second=pool.submit(()->z.imageData(1));if(!Arrays.equals(first.get(),images.get(0))||!Arrays.equals(second.get(),images.get(1)))throw new AssertionError("concurrent archive reuse");System.out.println("PASS concurrent reads serialize correctly");}finally{pool.shutdownNow();}
   previous=images;
   z.close();try{z.imageData(0);throw new AssertionError("closed RAR still readable");}catch(java.io.IOException expected){System.out.println("PASS close prevents further access");}
   byte[] padded=Arrays.copyOf(input,input.length+2*1024*1024);Dav partial=new Dav(padded);
   RemoteZip large=RemoteBooks.open(partial,"https://example.com/padded.rar","padded.rar");
   if(!Arrays.equals(large.imageData(0),images.get(0)))throw new AssertionError("partial extraction");
   if(partial.transferred>=padded.length/2)throw new AssertionError("read too much "+partial.transferred);
   System.out.println("PASS RAR range extraction before full download: "+partial.transferred+" / "+padded.length);
   large.close();
  }
 }
}
