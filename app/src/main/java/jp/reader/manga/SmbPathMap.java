package jp.reader.manga;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Maps a WebDAV URL to a share-relative SMB path. Does not open connections. */
final class SmbPathMap {
  private final URI root;
  private final List<String> rootSegments;
  SmbPathMap(String url){
    root=URI.create(NestedBook.safe(url));
    if(!"https".equalsIgnoreCase(root.getScheme())||root.getHost()==null||root.getRawQuery()!=null||root.getRawFragment()!=null||root.getUserInfo()!=null)throw new IllegalArgumentException("WebDAVルートが不正です");
    rootSegments=segments(root.getRawPath());
  }
  String relative(String url){
    URI target=URI.create(NestedBook.safe(url));
    if(!"https".equalsIgnoreCase(target.getScheme())||target.getHost()==null||!root.getHost().equalsIgnoreCase(target.getHost())||port(root)!=port(target)||target.getRawQuery()!=null||target.getRawFragment()!=null||target.getUserInfo()!=null)throw new IllegalArgumentException("WebDAVルート外です");
    List<String> path=segments(target.getRawPath());
    if(path.size()<rootSegments.size()||!path.subList(0,rootSegments.size()).equals(rootSegments))throw new IllegalArgumentException("共有フォルダ外です");
    return String.join("\\",path.subList(rootSegments.size(),path.size()));
  }
  String child(String parent,String name,boolean directory){
    relative(parent);validate(name);
    String uri=parent.endsWith("/")?parent:parent+"/";
    return uri+encode(name)+(directory?"/":"");
  }
  private static int port(URI uri){return uri.getPort()<0?443:uri.getPort();}
  private static List<String> segments(String path){
    List<String> list=new ArrayList<>();
    if(path==null)return list;
    for(String raw:path.split("/",-1)){
      if(raw.isEmpty())continue;
      try{String v=URLDecoder.decode(raw.replace("+","%2B"),"UTF-8");validate(v);list.add(v);}catch(java.io.UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}
    }return list;
  }
  private static void validate(String s){if(s==null||s.isEmpty()||s.equals(".")||s.equals("..")||s.indexOf('/')>=0||s.indexOf('\\')>=0||s.indexOf('\0')>=0||s.indexOf('\uFFFD')>=0)throw new IllegalArgumentException("不正なパスです");}
  private static String encode(String s){
    StringBuilder out=new StringBuilder();for(byte b:s.getBytes(StandardCharsets.UTF_8)){int c=b&255;if((c>='A'&&c<='Z')||(c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='-'||c=='_'||c=='.'||c=='~')out.append((char)c);else out.append('%').append("0123456789ABCDEF".charAt(c>>>4)).append("0123456789ABCDEF".charAt(c&15));}return out.toString();
  }
}
