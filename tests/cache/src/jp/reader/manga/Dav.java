package jp.reader.manga;
import java.io.*;import java.util.function.Consumer;import java.util.concurrent.CountDownLatch;
final class Dav implements Closeable {
 byte[] data; int owners=1;CountDownLatch entered,release,closeEntered,closeRelease,onZero;
 Dav(byte[] x){data=x;}
 synchronized Dav retain()throws IOException{if(owners==0)throw new IOException("closed");owners++;return this;}
 public synchronized void close(){if(owners>0)owners--;if(owners==0&&onZero!=null)onZero.countDown();}
 synchronized int ownerCount(){return owners;}
 static final class Download implements Closeable {final InputStream stream;final long length;Download(InputStream s,long n){stream=s;length=n;}public void close()throws IOException{stream.close();}}
 Download openDownload(String u,Consumer<Closeable> register){
  InputStream in=new ByteArrayInputStream(data);
  if(entered!=null)in=new FilterInputStream(in){boolean waited;
   public int read(byte[] b,int off,int len)throws IOException{if(!waited){waited=true;entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}return super.read(b,off,len);}
   public void close()throws IOException{if(closeEntered!=null){closeEntered.countDown();try{closeRelease.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}super.close();}};
  register.accept(in);return new Download(in,data.length);
 }
}
