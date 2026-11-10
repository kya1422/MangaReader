package jp.reader.manga;
import java.nio.file.*;
import java.util.*;
class TestRar {
 public static void main(String[] args)throws Exception{
  List<byte[]> previous=null;
  for(String path:args){
   byte[] input=Files.readAllBytes(Paths.get(path));Dav d=new Dav(input);
   RemoteZip z=RemoteBooks.open(d,"https://example.com/test.rar","test.rar");
   if(z.pages.size()!=2)throw new AssertionError("pages "+z.pages.size());
   List<byte[]> images=new ArrayList<>();
   for(int i=0;i<z.pages.size();i++){
    byte[] img=z.imageData(i);images.add(img);if(img.length<1000)throw new AssertionError("image");
    android.graphics.Bitmap b=z.bitmap(i,500,500);
    System.out.println(path+" "+z.pages.get(i).name+" bytes="+img.length+" wide="+z.isWide(i)+" bitmap="+b.getWidth());
   }
   if(previous!=null)for(int i=0;i<images.size();i++)if(!Arrays.equals(previous.get(i),images.get(i)))throw new AssertionError("RAR4/RAR5 content differs");
   previous=images;
   byte[] padded=Arrays.copyOf(input,input.length+2*1024*1024);Dav partial=new Dav(padded);
   RemoteZip large=RemoteBooks.open(partial,"https://example.com/padded.rar","padded.rar");
   if(!Arrays.equals(large.imageData(0),images.get(0)))throw new AssertionError("partial extraction");
   if(partial.transferred>=padded.length/2)throw new AssertionError("read too much "+partial.transferred);
   System.out.println("PASS RAR range extraction before full download: "+partial.transferred+" / "+padded.length);
  }
 }
}
