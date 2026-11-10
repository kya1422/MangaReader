package jp.reader.manga;
import android.content.Context;import java.nio.file.*;
class TestManualCover{
 static void check(boolean c,String name){if(!c)throw new AssertionError(name);System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception{
  Context ctx=new Context(Files.createTempDirectory("cover-test").toFile());String folder="https://example.com/title/",book="https://example.com/title/vol2.rar";Dav d=new Dav();
  FolderCovers.load(ctx,folder,()->d,()->false).recycle();check(RemoteBooks.lastBook.endsWith("auto.zip")&&RemoteZip.lastPage==0,"automatic first page");
  FolderCovers.setManual(ctx,folder,book,"vol2.rar",2,new RemoteZip());check(RemoteZip.lastPage==2,"manual page chosen");
  RemoteBooks.lastBook=null;android.graphics.Bitmap cached=FolderCovers.cached(ctx,folder);check(cached!=null&&RemoteBooks.lastBook==null,"manual choice available from cache without network");cached.recycle();
  FolderCovers.load(ctx,folder,()->d,()->false).recycle();check(RemoteBooks.lastBook==null,"manual cache used");
  FolderCovers.clear(ctx);check(FolderCovers.cached(ctx,folder)==null,"cleared cache reports a miss");RemoteZip.lastPage=-1;FolderCovers.load(ctx,folder,()->d,()->false).recycle();check(book.equals(RemoteBooks.lastBook)&&RemoteZip.lastPage==2,"clear retains manual choice and refetches selected page");
  FolderCovers.clearManual(ctx,folder);FolderCovers.load(ctx,folder,()->d,()->false).recycle();check(RemoteBooks.lastBook.endsWith("auto.zip")&&RemoteZip.lastPage==0,"manual reset restores auto");
  FolderCovers.clear(ctx);try{FolderCovers.load(ctx,folder,()->d,()->true);throw new AssertionError("cancel");}catch(java.io.IOException expected){System.out.println("PASS cancelled cover load");}
 }
}
