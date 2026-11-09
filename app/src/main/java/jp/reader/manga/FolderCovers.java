package jp.reader.manga;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Small, bounded temporary cover cache. Never uses WebDAV credentials for public requests. */
final class FolderCovers {
  private static final long TTL=7L*24*60*60*1000, MAX_CACHE=10L*1024*1024;
  private static long lastSearch;
  private static String key(String value)throws Exception{
    byte[] hash=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:hash)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();
  }
  private static HttpURLConnection request(String u)throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","MangaReader/0.1 (Android)");return c;
  }
  private static void throttle()throws InterruptedException{
    long wait=1200-(System.currentTimeMillis()-lastSearch);if(wait>0)Thread.sleep(wait);lastSearch=System.currentTimeMillis();
  }
  static Bitmap load(Context ctx,String folderUrl,String title)throws Exception{
    File dir=new File(ctx.getCacheDir(),"folder-covers");if(!dir.exists()&&!dir.mkdirs())return null;
    String prefix=key(folderUrl+"\n"+title)+"-";long now=System.currentTimeMillis();
    File[] hits=dir.listFiles((d,n)->n.startsWith(prefix)&&n.endsWith(".jpg"));
    if(hits!=null)for(File hit:hits){
      try{long expiry=Long.parseLong(hit.getName().substring(prefix.length(),hit.getName().length()-4));
        if(now<expiry && now>=hit.lastModified() && now-hit.lastModified()<TTL){Bitmap bitmap=BitmapFactory.decodeFile(hit.getPath());if(bitmap!=null)return bitmap;}
      }catch(NumberFormatException ignored){}hit.delete();
    }
    if(title.trim().isEmpty())return null;
    long coverId=0;
    for(int attempt=0;attempt<2 && coverId==0;attempt++){
      throttle();String query=URLEncoder.encode(title+(attempt==0?" 1 manga":" 1"),"UTF-8");
      HttpURLConnection search=request("https://openlibrary.org/search.json?q="+query+"&limit=10&fields=title,cover_i");
      try{
        int searchCode=search.getResponseCode();if(searchCode!=200)throw new IOException("検索 HTTP "+searchCode);
        JSONObject result=new JSONObject(new String(Dav.read(search.getInputStream(),512*1024),StandardCharsets.UTF_8));
        JSONArray docs=result.optJSONArray("docs");if(docs==null)continue;
        for(int i=0;i<docs.length();i++){JSONObject doc=docs.optJSONObject(i);if(doc==null)continue;long id=doc.optLong("cover_i",0);if(id>0&&CoverMatch.matchesFirstVolume(title,doc.optString("title",""))){coverId=id;break;}}
      }finally{search.disconnect();}
    }
    if(coverId==0)return null;
    URL target=new URL("https://covers.openlibrary.org/b/id/"+coverId+"-M.jpg?default=false");
    boolean storable=true;
    for(int redirects=0;redirects<=5;redirects++){
      if(!CoverHttpPolicy.allowed(target))throw new IOException("許可されていない転送先");
      HttpURLConnection cover=request(target.toExternalForm());
      try{
        int code=cover.getResponseCode();String cc=cover.getHeaderField("Cache-Control");
        if(cc!=null&&cc.toLowerCase(Locale.ROOT).matches(".*(?:no-store|no-cache|private).*"))storable=false;
        if(code==301||code==302||code==303||code==307||code==308){
          String location=cover.getHeaderField("Location");if(location==null)throw new IOException("画像の転送先がありません");
          target=new URL(target,location);continue;
        }
        if(code==404)return null;if(code!=200)throw new IOException("画像 HTTP "+code);
        String type=cover.getContentType();if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("image/"))return null;
        byte[] bytes=Dav.read(cover.getInputStream(),1024*1024);
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
        if(opts.outWidth<2||opts.outHeight<2||opts.outWidth>5000||opts.outHeight>5000)return null;
        opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>160||opts.outHeight/opts.inSampleSize>220)opts.inSampleSize*=2;
        opts.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);if(bitmap==null)return null;
        long fetched=System.currentTimeMillis();long fresh=CoverHttpPolicy.freshnessMillis(cc,cover.getHeaderField("Age"),cover.getHeaderFieldDate("Expires",0),cover.getHeaderFieldDate("Date",0),fetched,TTL);
        if(storable && fresh>0){
          File file=new File(dir,prefix+(fetched+fresh)+".jpg"),tmp=new File(dir,prefix+"tmp");
          try(FileOutputStream out=new FileOutputStream(tmp)){bitmap.compress(Bitmap.CompressFormat.JPEG,85,out);}if(!tmp.renameTo(file))tmp.delete();trim(dir);
        }
        return bitmap;
      }finally{cover.disconnect();}
    }
    throw new IOException("画像の転送回数が多すぎます");
  }
  private static void trim(File dir){
    File[] files=dir.listFiles((d,n)->n.endsWith(".jpg"));if(files==null)return;
    long now=System.currentTimeMillis();for(File f:files)if(f.lastModified()>now||now-f.lastModified()>=TTL)f.delete();
    Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File f:files)total+=f.length();
    for(File f:files){if(total<=MAX_CACHE)break;long size=f.length();if(f.delete())total-=size;}
  }
}
