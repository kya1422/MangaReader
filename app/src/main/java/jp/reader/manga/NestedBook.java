package jp.reader.manga;
import java.io.IOException;
import java.net.URI;
import java.util.*;
/** Bounded, cancellable traversal of folders; direct archives precede child folders. */
final class NestedBook {
  interface Source {List<Dav.Item> list(String url)throws Exception;}
  interface Cancel {boolean cancelled();}
  static Dav.Item find(String root,Source source,Cancel cancel)throws Exception{
    return search(root,source,cancel,new HashSet<>(),0,new int[]{0});
  }
  private static Dav.Item search(String url,Source source,Cancel cancel,Set<String> visited,int depth,int[] count)throws Exception{
    if(cancel.cancelled())throw new IOException("表紙取得を中止しました");
    if(!visited.add(url))return null;
    if(depth>12 || ++count[0]>128)throw new IOException("表紙検索のフォルダ数・階層が上限を超えました");
    List<Dav.Item> all=source.list(url),direct=new ArrayList<>();
    for(Dav.Item item:all)if(isDirectChild(url,item.url))direct.add(item);
    Dav.Item first=FirstBook.find(direct);if(first!=null)return first;
    ArrayList<Dav.Item> folders=new ArrayList<>();for(Dav.Item item:direct)if(item.dir)folders.add(item);
    folders.sort((a,b)->RemoteZip.natural(a.name,b.name));
    for(Dav.Item child:folders){Dav.Item found=search(child.url,source,cancel,visited,depth+1,count);if(found!=null)return found;}
    return null;
  }
  static boolean isDirectChild(String parent,String child){
    try{
      URI p=new URI(parent).normalize(),c=new URI(child).normalize();
      if(!Objects.equals(p.getScheme(),c.getScheme())||!Objects.equals(p.getAuthority(),c.getAuthority())||c.getQuery()!=null||c.getFragment()!=null)return false;
      String base=p.getPath();if(!base.endsWith("/"))base+="/";String path=c.getPath();
      if(!path.startsWith(base))return false;String rest=path.substring(base.length());if(rest.endsWith("/"))rest=rest.substring(0,rest.length()-1);
      return !rest.isEmpty()&&!rest.equals(".")&&!rest.equals("..")&&!rest.contains("/");
    }catch(Exception e){return false;}
  }
}
