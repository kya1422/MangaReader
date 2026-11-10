package jp.reader.manga;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Best-effort cache; no Android background-service guarantees. */
final class DownloadCache {
  interface Callback { void done(String message); }
  private static final long TTL = 30L*24*60*60*1000;
  static final long MAX_BYTES=2L*1024*1024*1024;
  private static volatile String pinnedName;
  private static final ExecutorService pool=Executors.newSingleThreadExecutor();
  private static final ConcurrentHashMap<String,Job> jobs=new ConcurrentHashMap<>();
  private static class Job { volatile boolean cancelled; volatile Closeable active; }
  static File dir(Context c){File f=new File(c.getFilesDir(),"downloads");f.mkdirs();return f;}
  static String id(String url) throws Exception {byte[] h=MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8"));StringBuilder s=new StringBuilder();for(byte b:h)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
  static File file(Context c,String url) throws Exception {return new File(dir(c),id(url)+".zipcache");}
  static File available(Context c,String url) throws Exception {File f=file(c,url);if(!f.isFile())return null;if(System.currentTimeMillis()-f.lastModified()>TTL&&!f.getName().equals(pinnedName)){if(f.delete())CachePolicy.accessFile(f).delete();return null;}return f;}
  static void cleanup(Context c){File[] files=dir(c).listFiles();if(files==null)return;long now=System.currentTimeMillis();for(File f:files){if(f.getName().endsWith(".part")){boolean active=false;for(String k:jobs.keySet())if(f.getName().equals(k+".part")){active=true;break;}if(!active)f.delete();}else if(f.getName().endsWith(".zipcache")&&now-f.lastModified()>TTL&&!f.getName().equals(pinnedName)){if(f.delete())CachePolicy.accessFile(f).delete();}else if(f.getName().endsWith(".zipcache.access")&&!new File(dir(c),f.getName().substring(0,f.getName().length()-7)).exists())f.delete();}}
  static void touch(Context c,String url){try{File f=available(c,url);if(f!=null)CachePolicy.touch(f);}catch(Exception ignored){}}
  static void pin(String url){try{pinnedName=url==null?null:id(url)+".zipcache";}catch(Exception e){pinnedName=null;}}
  static void reserve(Context c,long incoming)throws IOException{CachePolicy.ensure(dir(c).listFiles(),incoming,MAX_BYTES,pinnedName);}
  static boolean delete(Context c,String url){try{String k=id(url);Job j=jobs.get(k);if(j!=null){j.cancelled=true;Closeable active=j.active;if(active!=null)try{active.close();}catch(IOException ignored){}}File saved=file(c,url);boolean removed=!saved.exists()||saved.delete()||!saved.exists();if(removed)CachePolicy.accessFile(saved).delete();return removed;}catch(Exception e){return false;}}
  static void start(Context ctx,Dav dav,String url,Callback cb){Context c=ctx.getApplicationContext();try{if(available(c,url)!=null){cb.done("保存済み");return;}String k=id(url);Job job=new Job();if(jobs.putIfAbsent(k,job)!=null){cb.done("保存中");return;}pool.execute(()->{File temp=new File(dir(c),k+".part");String result="保存できませんでした";try{if(job.cancelled)return;Dav.Download download=dav.openDownload(url,active->{job.active=active;if(job.cancelled)try{active.close();}catch(IOException ignored){}});if(job.cancelled){download.close();return;}long expected=download.length,count=0;if(expected>MAX_BYTES)throw new IOException("この本は保存容量の上限（2 GB）を超えています");if(expected>=0)reserve(c,expected);long nextCheck=0;try(Dav.Download owned=download;InputStream in=download.stream;FileOutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];int n;while(!job.cancelled&&(n=in.read(b))!=-1){if(count+n>MAX_BYTES)throw new IOException("この本は保存容量の上限（2 GB）を超えています");
      if(count+n>=nextCheck){if(dir(c).getUsableSpace()<100L*1024*1024+n)throw new IOException("端末の空き容量が不足しています");reserve(c,count+n);nextCheck=count+n+1024*1024;}
      out.write(b,0,n);count+=n;}out.getFD().sync();}if(job.cancelled)return;if(expected>=0 && count!=expected)throw new IOException("ダウンロードが途中で切れました");if(count==0)throw new IOException("ファイルが空です");reserve(c,count);File target=file(c,url);if(!temp.renameTo(target))throw new IOException("ファイルを保存できません");target.setLastModified(System.currentTimeMillis());CachePolicy.touch(target);if(job.cancelled){target.delete();return;}result="保存済み";}catch(Exception e){if(!job.cancelled)result="保存失敗: "+e.getMessage();}finally{if(job.active!=null)try{job.active.close();}catch(IOException ignored){}temp.delete();jobs.remove(k,job);if(!job.cancelled)cb.done(result);}});}catch(Exception e){cb.done("保存失敗: "+e.getMessage());}}
}
