package jp.reader.manga;
import java.util.Locale;
final class RemoteBooks {
  static RemoteZip open(Dav dav,String url,String name)throws Exception{
    String n=name.toLowerCase(Locale.ROOT);
    return n.endsWith(".rar")||n.endsWith(".cbr")?new RemoteRar(dav,url):new RemoteZip(dav,url);
  }
}
