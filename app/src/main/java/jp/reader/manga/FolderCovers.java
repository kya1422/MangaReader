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
  static Bitmap load(Context ctx,String folderUrl,Client client,NestedBook.Cancel cancel)throws Exception{
    File dir=new File(ctx.getFilesDir(),"folder-covers-webdav");
    if(!dir.exists()&&!dir.mkdirs())throw new IOException("表紙の保存先を作成できません");
    File file=new File(dir,"v2-"+DownloadCache.id(folderUrl)+".jpg");
    if(file.isFile()){
      Bitmap bitmap=BitmapFactory.decodeFile(file.getPath());
      if(bitmap!=null){file.setLastModified(System.currentTimeMillis());return bitmap;}
      file.delete();
    }
    Dav dav=client.get();Dav.Item first=NestedBook.find(folderUrl,dav::list,cancel);
    if(first==null)return null;
    RemoteZip zip=RemoteBooks.open(dav,first.url,first.name,cancel::cancelled);
    Bitmap decoded;
    try{decoded=zip.bitmap(0,320,220);}finally{zip.cancel();zip.close();}
    Bitmap small=null;
    try{
      int cropWidth=CoverCrop.leftWidth(decoded.getWidth(),decoded.getHeight());
      float scale=Math.min(1f,Math.min(160f/cropWidth,220f/decoded.getHeight()));
      int w=Math.max(1,Math.round(cropWidth*scale)),h=Math.max(1,Math.round(decoded.getHeight()*scale));
      small=Bitmap.createBitmap(w,h,Bitmap.Config.RGB_565);
      Canvas canvas=new Canvas(small);canvas.drawColor(android.graphics.Color.WHITE);
      canvas.drawBitmap(decoded,new Rect(0,0,cropWidth,decoded.getHeight()),new Rect(0,0,w,h),new Paint(Paint.FILTER_BITMAP_FLAG));
      File temp=new File(dir,file.getName()+".tmp");
      try{
        try(FileOutputStream out=new FileOutputStream(temp)){if(!small.compress(Bitmap.CompressFormat.JPEG,85,out))throw new IOException("表紙画像を保存できません");}
        if(!temp.renameTo(file))throw new IOException("表紙画像を保存できません");
        trim(dir);
      }finally{temp.delete();}
      return small;
    }catch(Exception e){if(small!=null)small.recycle();throw e;}
    finally{decoded.recycle();}
  }
  private static void trim(File dir){
    File[] files=dir.listFiles((d,n)->n.endsWith(".jpg"));if(files==null)return;
    Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File f:files)total+=f.length();
    for(File f:files){if(total<=MAX_CACHE)break;long size=f.length();if(f.delete())total-=size;}
  }
  static void clear(Context ctx){File dir=new File(ctx.getFilesDir(),"folder-covers-webdav");File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();}
}
