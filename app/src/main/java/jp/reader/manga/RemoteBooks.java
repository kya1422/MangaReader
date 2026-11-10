package jp.reader.manga;
import java.util.Locale;
final class RemoteBooks {
  static RemoteZip open(Dav dav,String url,String name)throws Exception{
    return open(dav,url,name,()->false);
  }
  static RemoteZip open(Dav dav,String url,String name,java.util.function.BooleanSupplier cancel)throws Exception{
    String n=name.toLowerCase(Locale.ROOT);
    return n.endsWith(".rar")||n.endsWith(".cbr")?new RemoteRar(dav,url,cancel):new RemoteZip(dav,url);
  }
}
