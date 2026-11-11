package jp.reader.manga;
class TestSmbPathMap{
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);System.out.println("PASS "+msg);}
 public static void main(String[] args){
  SmbPathMap map=new SmbPathMap("https://example.com/漫画/");
  check(map.relative("https://example.com/%E6%BC%AB%E7%94%BB/").equals(""),"encoded root matches");
  check(map.relative("https://example.com/漫画/%E4%BD%9C%E5%93%81/A+B%20C.zip").equals("作品\\A+B C.zip"),"Japanese, plus and space map correctly");
  check(map.child("https://example.com/漫画/","作品",true).endsWith("/%E4%BD%9C%E5%93%81/"),"child is UTF-8 URL encoded");
  for(String bad:new String[]{"https://example.com/漫画2/file.zip","https://evil.invalid/漫画/file.zip","https://example.com/漫画/%2e%2e/file.zip","https://example.com/漫画/a%2Fb.zip","https://example.com/漫画/a%5Cb.zip","https://example.com/漫画/%FF.zip"}){
   try{map.relative(bad);throw new AssertionError("accepted "+bad);}catch(IllegalArgumentException expected){}
  }
  check(true,"cross-origin, outside-root and encoded traversal rejected");
  SmbAliases aliases=new SmbAliases("https://example.com/Comic/");
  aliases.remember("https://example.com/Comic/%e4%bd%9c%e5%93%81/%31.zip");
  check(aliases.lookup("https://example.com/Comic/%E4%BD%9C%E5%93%81/1.zip").equals("https://example.com/Comic/%e4%bd%9c%e5%93%81/%31.zip"),"legacy encoded URL reused");
  aliases.remember("https://example.com/Comic/作品/2.zip");
  check(aliases.lookup("https://example.com/Comic/%E4%BD%9C%E5%93%81/2.zip").equals("https://example.com/Comic/作品/2.zip"),"unencoded legacy URL reused");
  check(aliases.lookup("https://example.com/Comic/%E4%BD%9C%E5%93%81/3.zip").equals("https://example.com/Comic/%E4%BD%9C%E5%93%81/3.zip"),"unknown path keeps generated URL");
  java.util.Map<String,String> disk=new java.util.HashMap<>();
  SmbAliases discovery=new SmbAliases("https://example.com/Comic/",disk::get);
  String legacy="https://example.com/Comic/%e4%bd%9c%e5%93%81/%5Bauthor%5D/";
  String generated="https://example.com/Comic/%E4%BD%9C%E5%93%81/%5Bauthor%5D/";
  disk.put(discovery.key(legacy),legacy);
  SmbAliases newClient=new SmbAliases("https://example.com/Comic/",disk::get);
  check(newClient.lookup(generated).equals(legacy),"WebDAV discovery preserves old cover key across clients");
  newClient.remember("https://example.com/Comic/作品/%5Bauthor%5D/");
  check(newClient.lookup(generated).equals("https://example.com/Comic/作品/%5Bauthor%5D/"),"existing record URL takes precedence");
  disk.put(discovery.key(generated),"https://elsewhere.invalid/Comic/other/");
  check(discovery.lookup(generated).equals(generated),"reject corrupt or cross-origin persisted alias");
  check(!discovery.key(generated).equals(new SmbAliases("https://another.example/Comic/").key("https://another.example/Comic/%E4%BD%9C%E5%93%81/%5Bauthor%5D/")),"alias index separates WebDAV roots");
 }
}
