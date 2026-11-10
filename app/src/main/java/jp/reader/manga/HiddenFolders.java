package jp.reader.manga;
import java.net.URI;
import java.util.*;
/** Display filter only; not an access-control boundary. */
final class HiddenFolders {
  static boolean contains(String folder,String item){
    if(folder==null||item==null||folder.isEmpty()||item.isEmpty())return false;
    try{
      URI a=new URI(NestedBook.safe(folder)).normalize(), b=new URI(NestedBook.safe(item)).normalize();
      if(a.getHost()==null||b.getHost()==null||a.getScheme()==null||b.getScheme()==null)return false;
      int ap=a.getPort()<0?("https".equalsIgnoreCase(a.getScheme())?443:80):a.getPort();
      int bp=b.getPort()<0?("https".equalsIgnoreCase(b.getScheme())?443:80):b.getPort();
      if(!a.getScheme().equalsIgnoreCase(b.getScheme())||!a.getHost().equalsIgnoreCase(b.getHost())||ap!=bp)return false;
      String base=a.getPath(),path=b.getPath();if(base==null||path==null)return false;
      while(base.length()>1&&base.endsWith("/"))base=base.substring(0,base.length()-1);
      while(path.length()>1&&path.endsWith("/"))path=path.substring(0,path.length()-1);
      return base.equals(path)||path.startsWith(base.equals("/")?"/":base+"/");
    }catch(Exception e){return false;}
  }
  static boolean hasDescendant(Set<String> folders,String parent){for(String folder:folders)if(contains(parent,folder))return true;return false;}
  static boolean hidden(Set<String> folders,String item){for(String folder:folders)if(contains(folder,item))return true;return false;}
  static boolean explicitlyHidden(Set<String> folders,String item){for(String folder:folders)if(contains(folder,item)&&contains(item,folder))return true;return false;}
}
