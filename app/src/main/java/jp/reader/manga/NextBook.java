package jp.reader.manga;

import java.util.ArrayList;
import java.util.List;

/** Resolves the next ZIP/CBZ in the same natural order as the folder listing. */
final class NextBook {
  static Dav.Item find(List<Dav.Item> entries,String currentUrl){
    List<Dav.Item> files=new ArrayList<>();
    for(Dav.Item entry:entries)if(!entry.dir)files.add(entry);
    files.sort((a,b)->RemoteZip.natural(a.name,b.name));
    for(int i=0;i<files.size();i++)if(files.get(i).url.equals(currentUrl))return i+1<files.size()?files.get(i+1):null;
    return null;
  }
  static boolean reachedThreshold(int visibleEnd,int total){return total>0 && visibleEnd>0 && (long)visibleEnd*10L >= (long)total*9L;}
}
