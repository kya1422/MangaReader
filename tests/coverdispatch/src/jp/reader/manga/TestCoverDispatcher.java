package jp.reader.manga;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
class TestCoverDispatcher {
 static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);System.out.println("PASS "+msg);}
 public static void main(String[] args)throws Exception{
  CoverDispatcher d=new CoverDispatcher();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),networkDone=new CountDownLatch(1);
  try{
   d.submit(()->false,()->null,()->{entered.countDown();release.await();return "network";},()->{},(v,e)->networkDone.countDown());
   check(entered.await(3,TimeUnit.SECONDS),"network request started");
   CountDownLatch cached=new CountDownLatch(100);AtomicInteger ok=new AtomicInteger();
   for(int i=0;i<100;i++)d.submit(()->false,()->"cached",()->{throw new AssertionError("cache miss");},()->{},(v,e)->{if("cached".equals(v)&&e==null)ok.incrementAndGet();cached.countDown();});
   check(cached.await(3,TimeUnit.SECONDS)&&ok.get()==100,"100 cached covers bypass blocked network");
   AtomicBoolean cancelled=new AtomicBoolean(true),ran=new AtomicBoolean();CountDownLatch cancelledDone=new CountDownLatch(1);
   d.submit(cancelled::get,()->{ran.set(true);return "bad";},()->{ran.set(true);return "bad";},()->{},(v,e)->cancelledDone.countDown());
   check(cancelledDone.await(3,TimeUnit.SECONDS)&&!ran.get(),"cancelled request does not read cache or network");
   release.countDown();check(networkDone.await(3,TimeUnit.SECONDS),"network request finishes after release");
   CountDownLatch errorDone=new CountDownLatch(1);AtomicBoolean reported=new AtomicBoolean();
   d.submit(()->false,()->null,()->{throw new java.io.IOException("failed");},()->{},(v,e)->{reported.set(e!=null);errorDone.countDown();});
   check(errorDone.await(3,TimeUnit.SECONDS)&&reported.get(),"network errors reported");
  }finally{release.countDown();d.shutdownNow();}
 }
}
