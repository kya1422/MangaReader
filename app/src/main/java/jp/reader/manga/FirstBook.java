package jp.reader.manga;
import java.util.List;
final class FirstBook {
  static Dav.Item find(List<Dav.Item> entries){
    Dav.Item best=null;
    for(Dav.Item item:entries){
      if(item.dir || !(item.name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")||item.name.toLowerCase(java.util.Locale.ROOT).endsWith(".cbz")))continue;
      if(best==null || RemoteZip.natural(item.name,best.name)<0)best=item;
    }
    return best;
  }
}
