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
    File file=new File(dir,key(folderUrl+"\n"+title)+".jpg");long now=System.currentTimeMillis();
    if(file.isFile() && now>=file.lastModified() && now-file.lastModified()<TTL){Bitmap hit=BitmapFactory.decodeFile(file.getPath());if(hit!=null)return hit;file.delete();}
    else file.delete();
    if(title.trim().isEmpty())return null;
    throttle();String query=URLEncoder.encode(title+" 1 manga","UTF-8");
    HttpURLConnection search=request("https://openlibrary.org/search.json?q="+query+"&limit=10&fields=title,cover_i");
    long coverId=0;
    try{
      int searchCode=search.getResponseCode();if(searchCode!=200)throw new IOException("検索 HTTP "+searchCode);
      JSONObject result=new JSONObject(new String(Dav.read(search.getInputStream(),512*1024),StandardCharsets.UTF_8));
      JSONArray docs=result.optJSONArray("docs");if(docs==null)return null;
      for(int i=0;i<docs.length();i++){JSONObject doc=docs.optJSONObject(i);if(doc==null)continue;long id=doc.optLong("cover_i",0);if(id>0&&CoverMatch.matchesFirstVolume(title,doc.optString("title",""))){coverId=id;break;}}
    }finally{search.disconnect();}
    if(coverId==0)return null;
    HttpURLConnection cover=request("https://covers.openlibrary.org/b/id/"+coverId+"-M.jpg?default=false");
    try{
      int coverCode=cover.getResponseCode();if(coverCode==404)return null;if(coverCode!=200)throw new IOException("画像 HTTP "+coverCode);
      String type=cover.getContentType();if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("image/"))return null;
      byte[] bytes=Dav.read(cover.getInputStream(),1024*1024);
      BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
      if(opts.outWidth<2||opts.outHeight<2||opts.outWidth>5000||opts.outHeight>5000)return null;
      opts.inSampleSize=1;while(opts.outWidth/opts.inSampleSize>160||opts.outHeight/opts.inSampleSize>220)opts.inSampleSize*=2;
      opts.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);if(bitmap==null)return null;
      String cc=cover.getHeaderField("Cache-Control");long expires=cover.getHeaderFieldDate("Expires",0);
      // Only retain when explicitly permitted for at least our seven-day limit.
      if((cc==null||!cc.toLowerCase(Locale.ROOT).matches(".*(no-store|no-cache|must-revalidate|max-age|s-maxage).*" ))&&expires>=now+TTL){
        File tmp=new File(dir,file.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){bitmap.compress(Bitmap.CompressFormat.JPEG,85,out);}if(!tmp.renameTo(file))tmp.delete();trim(dir);
      }
      return bitmap;
    }finally{cover.disconnect();}
  }
  private static void trim(File dir){
    File[] files=dir.listFiles((d,n)->n.endsWith(".jpg"));if(files==null)return;
    long now=System.currentTimeMillis();for(File f:files)if(f.lastModified()>now||now-f.lastModified()>=TTL)f.delete();
    Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File f:files)total+=f.length();
    for(File f:files){if(total<=MAX_CACHE)break;long size=f.length();if(f.delete())total-=size;}
  }
}
