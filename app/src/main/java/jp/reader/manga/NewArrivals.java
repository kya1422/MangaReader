package jp.reader.manga;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

/** Recursively scans creation dates only. Missing dates are never replaced with mtime. */
final class NewArrivals {
  static final int MAX_FOLDERS=10000, MAX_DEPTH=64, MAX_RESULTS=5000;
  interface Source {List<Dav.Item> list(String url)throws Exception;}
  interface Cancel {boolean cancelled();}
  interface Progress {void update(Result result);}
  static final class Book {
    final String url,name,folder,folderName;final long date;final List<FolderRef> ancestry;
    Book(Dav.Item item,String parent,String name){this(item,parent,name,Collections.singletonList(new FolderRef(parent,name)));}
    Book(Dav.Item item,String parent,String name,List<FolderRef> ancestry){url=item.url;this.name=item.name;folder=parent;folderName=name;date=item.createdAt;this.ancestry=ancestry;}
  }
  static final class FolderRef {
    final String url,name;
    FolderRef(String url,String name){this.url=url;this.name=name;}
  }
  static final class Result {
    final List<Book> books;final int scanned,errors,unknown;final boolean limited,complete;
    Result(List<Book>b,int s,int e,int u,boolean l,boolean c){books=b;scanned=s;errors=e;unknown=u;limited=l;complete=c;}
  }
  private static final class Folder {final String url,name;final int depth;final List<FolderRef> ancestry;Folder(String u,String n,int d,List<FolderRef> a){url=u;name=n;depth=d;ancestry=a;}}
  static long parseCreation(String raw){try{return raw==null?0:OffsetDateTime.parse(raw.trim()).toInstant().toEpochMilli();}catch(Exception e){return 0;}}
  private static final Comparator<Book> ORDER=(a,b)->{int c=Long.compare(b.date,a.date);if(c==0)c=RemoteZip.natural(a.name,b.name);return c==0?a.url.compareTo(b.url):c;};
  static Result scan(String root,Source source,Set<String> read,Set<String> hidden,long now,long cutoff,Cancel cancel,Progress progress)throws Exception{
    HiddenFolders.Index hiddenIndex=new HiddenFolders.Index(hidden);
    ArrayDeque<Folder> queue=new ArrayDeque<>();queue.add(new Folder(root,"フォルダ",0,Collections.singletonList(new FolderRef(root,"フォルダ"))));Set<String> seen=new HashSet<>(),booksSeen=new HashSet<>();
    PriorityQueue<Book> newest=new PriorityQueue<>(ORDER.reversed());int scanned=0,errors=0,unknown=0;boolean limited=false;long lastProgress=System.nanoTime();
    while(!queue.isEmpty()){
      if(cancel.cancelled())throw new IOException("新着の取得を中止しました");
      Folder f=queue.removeFirst();if(!seen.add(f.url)||hiddenIndex.hidden(f.url))continue;
      if(scanned>=MAX_FOLDERS){limited=true;break;}scanned++;
      List<Dav.Item> children;
      try{children=source.list(f.url);}catch(Exception e){if(cancel.cancelled())throw e;errors++;if(scanned%16==0&&System.nanoTime()-lastProgress>=750_000_000L){progress.update(snapshot(newest,scanned,errors,unknown,limited,false));lastProgress=System.nanoTime();}continue;}
      for(Dav.Item item:children){
        if(cancel.cancelled())throw new IOException("新着の取得を中止しました");
        if(!NestedBook.isDirectChild(f.url,item.url)||hiddenIndex.hidden(item.url))continue;
        if(item.dir){if(f.depth>=MAX_DEPTH||queue.size()+seen.size()>=MAX_FOLDERS){limited=true;continue;}List<FolderRef> ancestry=new ArrayList<>(f.ancestry);ancestry.add(new FolderRef(item.url,item.name));queue.addLast(new Folder(item.url,item.name,f.depth+1,ancestry));continue;}
        if(!booksSeen.add(item.url)||read.contains(item.url))continue;
        if(item.createdAt<=0){unknown++;continue;}
        if(item.createdAt<cutoff||item.createdAt>now)continue;
        Book book=new Book(item,f.url,f.name,f.ancestry);if(newest.size()<MAX_RESULTS)newest.add(book);
        else{limited=true;if(ORDER.compare(book,newest.peek())<0){newest.poll();newest.add(book);}}
      }
      if(scanned%16==0&&System.nanoTime()-lastProgress>=750_000_000L){progress.update(snapshot(newest,scanned,errors,unknown,limited,false));lastProgress=System.nanoTime();}
    }
    Result result=snapshot(newest,scanned,errors,unknown,limited,true);progress.update(result);return result;
  }
  private static Result snapshot(PriorityQueue<Book> q,int scanned,int errors,int unknown,boolean limited,boolean complete){List<Book> b=new ArrayList<>(q);b.sort(ORDER);return new Result(b,scanned,errors,unknown,limited,complete);}
}
