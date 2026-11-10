package jp.reader.manga;
import java.util.HashMap;
import java.util.Map;
/** Retains previously stored WebDAV URL spelling for the same SMB path. */
final class SmbAliases {
  private final SmbPathMap paths;private final Map<String,String> originals=new HashMap<>();
  SmbAliases(String root){paths=new SmbPathMap(root);remember(root);}
  void remember(String url){try{if(url!=null&&!url.isEmpty())originals.putIfAbsent(paths.relative(url),url);}catch(IllegalArgumentException ignored){}}
  String lookup(String url){try{String original=originals.get(paths.relative(url));return original==null?url:original;}catch(IllegalArgumentException ignored){return url;}}
}
