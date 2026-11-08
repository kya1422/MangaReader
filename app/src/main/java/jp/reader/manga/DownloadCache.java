package jp.reader.manga;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Best-effort cache; no Android background-service guarantees. */
final class DownloadCache {
  interface Callback { void done(String message); }
  private static final long TTL = 30L*24*60*60*1000;
  private static final ExecutorService pool=Executors.newSingleThreadExecutor();
  private static final ConcurrentHashMap<String,Job> jobs=new ConcurrentHashMap<>();
  private static class Job { volatile boolean cancelled; volatile HttpURLConnection connection; }
  static File dir(Context c){File f=new File(c.getFilesDir(),"downloads");f.mkdirs();return f;}
  static String id(String url) throws Exception {byte[] h=MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8"));StringBuilder s=new StringBuilder();for(byte b:h)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
  static File file(Context c,String url) throws Exception {return new File(dir(c),id(url)+".zipcache");}
  static File available(Context c,String url) throws Exception {File f=file(c,url);if(!f.isFile())return null;if(System.currentTimeMillis()-f.lastModified()>TTL){f.delete();return null;}return f;}
  static void cleanup(Context c){File[] files=dir(c).listFiles();if(files==null)return;long now=System.currentTimeMillis();for(File f:files){if(f.getName().endsWith(".part")){boolean active=false;for(String k:jobs.keySet())if(f.getName().equals(k+".part")){active=true;break;}if(!active)f.delete();}else if(f.getName().endsWith(".zipcache")&&now-f.lastModified()>TTL)f.delete();}}
  static void delete(Context c,String url){try{String k=id(url);Job j=jobs.get(k);if(j!=null){j.cancelled=true;HttpURLConnection conn=j.connection;if(conn!=null)conn.disconnect();}file(c,url).delete();}catch(Exception ignored){}}
  static void start(Context ctx,Dav dav,String url,Callback cb){Context c=ctx.getApplicationContext();try{if(available(c,url)!=null){cb.done("保存済み");return;}String k=id(url);Job job=new Job();if(jobs.putIfAbsent(k,job)!=null){cb.done("保存中");return;}pool.execute(()->{File temp=new File(dir(c),k+".part");String result="保存できませんでした";try{if(job.cancelled)return;HttpURLConnection conn=dav.open(url,"GET");job.connection=conn;if(job.cancelled)return;int code=conn.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);long expected=conn.getContentLengthLong(),count=0;try(InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];int n;while(!job.cancelled&&(n=in.read(b))!=-1){out.write(b,0,n);count+=n;}out.getFD().sync();}if(job.cancelled)return;if(expected>=0 && count!=expected)throw new IOException("ダウンロードが途中で切れました");if(count==0)throw new IOException("ファイルが空です");File target=file(c,url);if(!temp.renameTo(target))throw new IOException("ファイルを保存できません");target.setLastModified(System.currentTimeMillis());if(job.cancelled){target.delete();return;}result="保存済み";}catch(Exception e){if(!job.cancelled)result="保存失敗: "+e.getMessage();}finally{if(job.connection!=null)job.connection.disconnect();temp.delete();jobs.remove(k,job);if(!job.cancelled)cb.done(result);}});}catch(Exception e){cb.done("保存失敗: "+e.getMessage());}}
}
