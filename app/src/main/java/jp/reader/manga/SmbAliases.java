package jp.reader.manga;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
/** Keeps existing WebDAV identifiers for paths also accessed through SMB. */
final class SmbAliases {
  private final SmbPathMap paths;private final String root;private final Map<String,String> originals=new HashMap<>();
  private final Function<String,String> stored;
  SmbAliases(String root){this(root,k->null);}
  SmbAliases(String root,Function<String,String> stored){this.root=root;this.stored=stored;paths=new SmbPathMap(root);remember(root);}
  void remember(String url){try{if(url!=null&&!url.isEmpty())originals.putIfAbsent(paths.relative(url),url);}catch(IllegalArgumentException ignored){}}
  String key(String url){try{
    String relative=paths.relative(url);byte[] hash=MessageDigest.getInstance("SHA-256").digest((root+"\n"+relative).getBytes(StandardCharsets.UTF_8));
    StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
  }catch(Exception ignored){return null;}}
  String lookup(String url){try{
    String original=originals.get(paths.relative(url));if(original!=null)return original;
    String key=key(url);String found=key==null?null:stored.apply(key);
    // Refuse stale or corrupt entries that don't refer to exactly this same path.
    return found!=null&&paths.relative(found).equals(paths.relative(url))?found:url;
  }catch(IllegalArgumentException ignored){return url;}}
  boolean has(String url){try{return originals.containsKey(paths.relative(url))||!lookup(url).equals(url);}catch(IllegalArgumentException e){return false;}}
}
