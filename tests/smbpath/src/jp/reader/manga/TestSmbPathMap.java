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
 }
}
