package jp.reader.manga;
import java.nio.file.*;
import java.util.*;
class TestRar {
 public static void main(String[] args)throws Exception{

  for(String path:args){
   if(path.contains("solid")||path.contains("password")){
    try{RemoteBooks.open(new Dav(Files.readAllBytes(Paths.get(path))),"https://example.com/test.rar","test.rar");throw new AssertionError("unsupported RAR was accepted");}
    catch(RemoteRar.UnsupportedRar expected){System.out.println("PASS unsupported archive rejected: "+expected.getMessage());}
    continue;
   }
   byte[] input=Files.readAllBytes(Paths.get(path));
   java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1), unblock=new java.util.concurrent.CountDownLatch(1);
   java.util.concurrent.atomic.AtomicBoolean cancel=new java.util.concurrent.atomic.AtomicBoolean();
   Dav slow=new Dav(input){int calls;@Override Range range(String url,long start,long end)throws Exception{if(++calls==2){entered.countDown();if(!unblock.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new java.io.IOException("timeout");}return super.range(url,start,end);}};
   java.util.concurrent.ExecutorService opening=java.util.concurrent.Executors.newFixedThreadPool(2);
   try{
     java.util.concurrent.Future<RemoteZip> cover=opening.submit(()->RemoteBooks.open(slow,"https://example.com/cover.rar","cover.rar",cancel::get));
     if(!entered.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("cover did not enter header scan");
     java.util.concurrent.Future<RemoteZip> reader=opening.submit(()->RemoteBooks.open(new Dav(input),"https://example.com/book.rar","book.rar"));
     RemoteZip independent=reader.get(5,java.util.concurrent.TimeUnit.SECONDS);independent.close();
     cancel.set(true);unblock.countDown();
     try{RemoteZip unexpected=cover.get(5,java.util.concurrent.TimeUnit.SECONDS);unexpected.close();throw new AssertionError("cancelled scan succeeded");}catch(java.util.concurrent.ExecutionException expected){}
     System.out.println("PASS slow cover does not block reader; cancelled cover scan stops");
   }finally{unblock.countDown();opening.shutdownNow();}
   Dav d=new Dav(input);
   RemoteZip z=RemoteBooks.open(d,"https://example.com/test.rar","test.rar");
   if(z.pages.isEmpty())throw new AssertionError("no pages");
   List<byte[]> images=new ArrayList<>();
   for(int i=0;i<z.pages.size();i++){
    long before=d.transferred;boolean wide=z.isWide(i);long orientationBytes=d.transferred-before;
    if(wide!=z.isWide(i))throw new AssertionError("orientation inconsistent");
    System.out.println("orientation range bytes="+orientationBytes);
    byte[] img=z.imageData(i);images.add(img);if(img.length==0)throw new AssertionError("image");
    android.graphics.Bitmap b=z.bitmap(i,500,500);
    System.out.println(path+" "+z.pages.get(i).name+" bytes="+img.length+" wide="+z.isWide(i)+" bitmap="+b.getWidth());
   }
   java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
   try{java.util.concurrent.Future<byte[]> first=pool.submit(()->z.imageData(0));int other=Math.min(1,images.size()-1);java.util.concurrent.Future<byte[]> second=pool.submit(()->z.imageData(other));if(!Arrays.equals(first.get(),images.get(0))||!Arrays.equals(second.get(),images.get(other)))throw new AssertionError("concurrent archive reuse");System.out.println("PASS concurrent reads serialize correctly");}finally{pool.shutdownNow();}
   z.close();try{z.imageData(0);throw new AssertionError("closed RAR still readable");}catch(java.io.IOException expected){System.out.println("PASS close prevents further access");}
   byte[] padded=Arrays.copyOf(input,input.length+2*1024*1024);Dav partial=new Dav(padded);
   RemoteZip large=RemoteBooks.open(partial,"https://example.com/padded.rar","padded.rar");
   if(!Arrays.equals(large.imageData(0),images.get(0)))throw new AssertionError("partial extraction");

   System.out.println("PASS RAR range extraction before full download: "+partial.transferred+" / "+padded.length);
   large.close();
  }
 }
}
