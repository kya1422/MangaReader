package jp.reader.manga;
import android.content.Context;import java.nio.file.*;
class TestManualCover{
 static void check(boolean c,String name){if(!c)throw new AssertionError(name);System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception{
  Context ctx=new Context(Files.createTempDirectory("cover-test").toFile());String folder="https://example.com/title/",book="https://example.com/title/vol2.rar";Dav d=new Dav();
  FolderCovers.load(ctx,folder,()->d,()->false).recycle();check(RemoteBooks.lastBook.endsWith("auto.zip")&&RemoteZip.lastPage==0,"automatic first page");
  FolderCovers.setManual(ctx,folder,book,"vol2.rar",2,10000,new RemoteZip());check(RemoteZip.lastPage==2,"manual page chosen");
  check(android.graphics.Canvas.lastSource.left==1000&&android.graphics.Canvas.lastSource.right==1600,"manual crop takes rightmost frame");
  RemoteBooks.lastBook=null;android.graphics.Bitmap cached=FolderCovers.cached(ctx,folder);check(cached!=null&&RemoteBooks.lastBook==null,"manual choice available from cache without network");cached.recycle();
  FolderCovers.load(ctx,folder,()->d,()->false).recycle();check(RemoteBooks.lastBook==null,"manual cache used");
  FolderCovers.clear(ctx);check(FolderCovers.cached(ctx,folder)==null,"cleared cache reports a miss");RemoteZip.lastPage=-1;FolderCovers.load(ctx,folder,()->d,()->false).recycle();check(book.equals(RemoteBooks.lastBook)&&RemoteZip.lastPage==2,"clear retains manual choice and refetches selected page");
  check(android.graphics.Canvas.lastSource.left==1000&&android.graphics.Canvas.lastSource.right==1600,"manual crop position restored on refetch");
  FolderCovers.clearManual(ctx,folder);FolderCovers.load(ctx,folder,()->d,()->false).recycle();check(RemoteBooks.lastBook.endsWith("auto.zip")&&RemoteZip.lastPage==0,"manual reset restores auto");
  String old="https://example.com/old/",key=DownloadCache.id(old);ctx.getSharedPreferences("cover-choices",0).edit().putString(key+".book",book).putString(key+".name","vol2.rar").putInt(key+".page",1).commit();
  FolderCovers.load(ctx,old,()->d,()->false).recycle();check(android.graphics.Canvas.lastSource.left==0&&android.graphics.Canvas.lastSource.right==800,"legacy manual choice retains old crop");
  FolderCovers.clear(ctx);try{FolderCovers.load(ctx,folder,()->d,()->true);throw new AssertionError("cancel");}catch(java.io.IOException expected){System.out.println("PASS cancelled cover load");}
 }
}
