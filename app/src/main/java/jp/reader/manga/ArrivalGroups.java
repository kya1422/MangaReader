package jp.reader.manga;

import java.util.*;
/** Groups an already-filtered newest-first list by its immediate parent. */
final class ArrivalGroups {
  static final int GROUP_THRESHOLD=10, PAGE_SIZE=100;
  static final class Group {
    final String folder,name;final List<NewArrivals.Book> books=new ArrayList<>();
    Group(NewArrivals.Book first){folder=first.folder;name=first.folderName;}
  }
  static boolean grouped(int count){return count>=GROUP_THRESHOLD;}
  static List<Group> group(List<NewArrivals.Book> books){
    LinkedHashMap<String,Group> byFolder=new LinkedHashMap<>();
    for(NewArrivals.Book book:books){Group g=byFolder.get(book.folder);if(g==null){g=new Group(book);byFolder.put(book.folder,g);}g.books.add(book);}
    return new ArrayList<>(byFolder.values());
  }
  static int pages(int count){return Math.max(1,(int)((count+(long)PAGE_SIZE-1)/PAGE_SIZE));}
  static int clampPage(int page,int count){return Math.max(0,Math.min(page,pages(count)-1));}
  static int start(int page){return page*PAGE_SIZE;}
  static int end(int page,int count){return Math.min(count,start(page)+PAGE_SIZE);}
}
