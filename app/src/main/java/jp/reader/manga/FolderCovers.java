package jp.reader.manga;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import java.io.*;
import java.util.*;

/** Extracts the first page of the first ZIP/CBZ and keeps a bounded local thumbnail. */
final class FolderCovers {
  interface Client {Dav get() throws Exception;}
  private static final long MAX_CACHE=10L*1024*1024;
  private static final Object CACHE_IO=new Object();
  private static android.content.SharedPreferences choices(Context ctx){return ctx.getSharedPreferences("cover-choices",Context.MODE_PRIVATE);}
  private static String key(String folder)throws Exception{return DownloadCache.id(folder);}
  private static File cacheFile(File dir,String folder,String book,int page,int crop)throws Exception{
    if(book==null)return new File(dir,"v2-"+key(folder)+".jpg");
    return new File(dir,(crop<0?"v3-":"v4-")+key(folder)+"-"+DownloadCache.id(book+"\n"+page+(crop<0?"":"\n"+crop))+".jpg");
  }
  static Bitmap load(Context ctx,String folderUrl,Client client,NestedBook.Cancel cancel)throws Exception{
    File dir=new File(ctx.getFilesDir(),"folder-covers-webdav");
    if(!dir.exists()&&!dir.mkdirs())throw new IOException("表紙の保存先を作成できません");
    String key=key(folderUrl);android.content.SharedPreferences prefs=choices(ctx);
    String book=prefs.getString(key+".book",null),name=prefs.getString(key+".name","");int page=prefs.getInt(key+".page",0),crop=prefs.getInt(key+".crop",-1);
    File file=cacheFile(dir,folderUrl,book,page,crop);
    Bitmap existing=cachedFile(file);if(existing!=null)return existing;
    if(cancel.cancelled())throw new IOException("表紙取得を中止しました");
    Bitmap decoded;boolean manual=book!=null;
    try(Dav dav=client.get()){
      if(book==null){Dav.Item first=NestedBook.find(folderUrl,dav::list,cancel);if(first==null)return null;book=first.url;name=first.name;page=0;}
      try{
        RemoteZip zip=RemoteBooks.open(dav,book,name,cancel::cancelled);
        try{if(page<0||page>=zip.pages.size())throw new IOException("指定した表紙ページが見つかりません");decoded=zip.bitmap(page,320,220);}finally{zip.cancel();zip.close();}
      }catch(Exception e){if(manual&&!cancel.cancelled())throw new IOException("手動表紙を取得できません。フォルダ長押し→手動の表紙設定を解除で自動表紙に戻せます",e);throw e;}
    }
    if(cancel.cancelled()){decoded.recycle();throw new IOException("表紙取得を中止しました");}
    Bitmap small=makeSmall(decoded,crop);try{synchronized(CACHE_IO){if(cancel.cancelled())throw new IOException("表紙取得を中止しました");write(file,small);trim(dir);}return small;}catch(Exception e){small.recycle();throw e;}
  }
  static Bitmap cached(Context ctx,String folderUrl)throws Exception{
    String key=key(folderUrl);android.content.SharedPreferences prefs=choices(ctx);
    String book=prefs.getString(key+".book",null);int page=prefs.getInt(key+".page",0),crop=prefs.getInt(key+".crop",-1);
    return cachedFile(cacheFile(new File(ctx.getFilesDir(),"folder-covers-webdav"),folderUrl,book,page,crop));
  }
  private static Bitmap cachedFile(File file){
    if(!file.isFile())return null;
    Bitmap bitmap=BitmapFactory.decodeFile(file.getPath());
    if(bitmap!=null){file.setLastModified(System.currentTimeMillis());return bitmap;}
    file.delete();return null;
  }
  static void setManual(Context ctx,String folder,String book,String name,int page,int crop,RemoteZip zip)throws Exception{
    File dir=new File(ctx.getFilesDir(),"folder-covers-webdav");if(!dir.exists()&&!dir.mkdirs())throw new IOException("表紙の保存先を作成できません");
    if(crop<0||crop>10000)throw new IllegalArgumentException("表紙位置が不正です");
    Bitmap small=makeSmall(zip.bitmap(page,320,220),crop);
    try{
      File file=cacheFile(dir,folder,book,page,crop);
      synchronized(CACHE_IO){
        write(file,small);String key=key(folder);
        if(!choices(ctx).edit().putString(key+".book",book).putString(key+".name",name).putInt(key+".page",page).putInt(key+".crop",crop).commit())throw new IOException("表紙設定を保存できません");
        trim(dir);
      }
    }finally{small.recycle();}
  }
  static void clearManual(Context ctx,String folder)throws Exception{
    String key=key(folder);if(!choices(ctx).edit().remove(key+".book").remove(key+".name").remove(key+".page").remove(key+".crop").commit())throw new IOException("表紙設定を解除できません");
  }
  private static Bitmap makeSmall(Bitmap decoded,int crop){
    try{
      int cropWidth=crop<0?CoverCrop.leftWidth(decoded.getWidth(),decoded.getHeight()):CoverCrop.manualWidth(decoded.getWidth(),decoded.getHeight());
      int left=crop<0?0:CoverCrop.manualLeft(decoded.getWidth(),decoded.getHeight(),crop);
      float scale=Math.min(1f,Math.min(160f/cropWidth,220f/decoded.getHeight()));
      int w=Math.max(1,Math.round(cropWidth*scale)),h=Math.max(1,Math.round(decoded.getHeight()*scale));
      Bitmap small=Bitmap.createBitmap(w,h,Bitmap.Config.RGB_565);
      Canvas canvas=new Canvas(small);canvas.drawColor(android.graphics.Color.WHITE);
      canvas.drawBitmap(decoded,new Rect(left,0,left+cropWidth,decoded.getHeight()),new Rect(0,0,w,h),new Paint(Paint.FILTER_BITMAP_FLAG));
      return small;
    }finally{decoded.recycle();}
  }
  private static void write(File file,Bitmap image)throws Exception{
    File temp=File.createTempFile("cover-",".tmp",file.getParentFile());
    try{try(FileOutputStream out=new FileOutputStream(temp)){if(!image.compress(Bitmap.CompressFormat.JPEG,85,out))throw new IOException("表紙画像を保存できません");}
      if(!temp.renameTo(file))throw new IOException("表紙画像を保存できません");
    }finally{temp.delete();}
  }
  private static void trim(File dir){
    File[] files=dir.listFiles((d,n)->n.endsWith(".jpg"));if(files==null)return;
    Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File f:files)total+=f.length();
    for(File f:files){if(total<=MAX_CACHE)break;long size=f.length();if(f.delete())total-=size;}
  }
  static void clear(Context ctx){synchronized(CACHE_IO){File dir=new File(ctx.getFilesDir(),"folder-covers-webdav");File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();}}
}
