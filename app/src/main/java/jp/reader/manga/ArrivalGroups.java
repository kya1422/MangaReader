package jp.reader.manga;

import java.util.*;
/** Groups an already-filtered newest-first list by its immediate parent. */
final class ArrivalGroups {
  static final int GROUP_THRESHOLD=10, PAGE_SIZE=100;
  static final class Node {
    final String folder,name;final List<NewArrivals.Book> books=new ArrayList<>();final LinkedHashMap<String,Node> children=new LinkedHashMap<>();int count;
    Node(String folder,String name){this.folder=folder;this.name=name;}
  }
  static boolean grouped(int count){return count>=GROUP_THRESHOLD;}
  static Node tree(List<NewArrivals.Book> books,String root){
    Node top=new Node(root,"フォルダ");
    for(NewArrivals.Book book:books){Node node=top;node.count++;
      for(NewArrivals.FolderRef ref:book.ancestry){if(ref.url.equals(root))continue;Node next=node.children.get(ref.url);if(next==null){next=new Node(ref.url,ref.name);node.children.put(ref.url,next);}node=next;node.count++;}
      node.books.add(book);
    }
    return top;
  }
  static Node find(Node root,String folder){
    if(root.folder.equals(folder))return root;
    for(Node child:root.children.values()){Node found=find(child,folder);if(found!=null)return found;}
    return null;
  }
  static int pages(int count){return Math.max(1,(int)((count+(long)PAGE_SIZE-1)/PAGE_SIZE));}
  static int clampPage(int page,int count){return Math.max(0,Math.min(page,pages(count)-1));}
  static int start(int page){return page*PAGE_SIZE;}
  static int end(int page,int count){return Math.min(count,start(page)+PAGE_SIZE);}
}
