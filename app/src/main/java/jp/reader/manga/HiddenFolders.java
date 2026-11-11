package jp.reader.manga;
import java.net.URI;
import java.util.*;
/** Display filter only; not an access-control boundary. */
final class HiddenFolders {
  private static final class Path {
    final String scheme,host,path;final int port;
    Path(String s,String h,int p,String x){scheme=s;host=h;port=p;path=x;}
  }
  private static Path parse(String value){
    if(value==null||value.isEmpty())return null;
    try{
      URI u=new URI(NestedBook.safe(value)).normalize();
      if(u.getHost()==null||u.getScheme()==null||u.getPath()==null)return null;
      String path=u.getPath();while(path.length()>1&&path.endsWith("/"))path=path.substring(0,path.length()-1);
      int port=u.getPort()<0?("https".equalsIgnoreCase(u.getScheme())?443:80):u.getPort();
      return new Path(u.getScheme(),u.getHost(),port,path);
    }catch(Exception e){return null;}
  }
  private static boolean contains(Path a,Path b){
    if(a==null||b==null||a.port!=b.port||!a.scheme.equalsIgnoreCase(b.scheme)||!a.host.equalsIgnoreCase(b.host))return false;
    return a.path.equals(b.path)||b.path.startsWith(a.path.equals("/")?"/":a.path+"/");
  }
  static boolean contains(String folder,String item){return contains(parse(folder),parse(item));}
  static final class Index {
    private final List<Path> paths=new ArrayList<>();
    Index(Set<String> folders){for(String folder:folders){Path p=parse(folder);if(p!=null)paths.add(p);}}
    boolean hidden(String item){if(paths.isEmpty())return false;Path p=parse(item);for(Path folder:paths)if(contains(folder,p))return true;return false;}
    boolean hasDescendant(String parent){if(paths.isEmpty())return false;Path p=parse(parent);for(Path folder:paths)if(contains(p,folder))return true;return false;}
    boolean explicitlyHidden(String item){if(paths.isEmpty())return false;Path p=parse(item);for(Path folder:paths)if(contains(folder,p)&&contains(p,folder))return true;return false;}
  }
  static boolean hasDescendant(Set<String> folders,String parent){return new Index(folders).hasDescendant(parent);}
  static boolean hidden(Set<String> folders,String item){return new Index(folders).hidden(item);}
  static boolean explicitlyHidden(Set<String> folders,String item){return new Index(folders).explicitlyHidden(item);}
}
