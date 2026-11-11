package jp.reader.manga;
import java.util.*;
class TestHidden{
 static void check(boolean x,String label){if(!x)throw new AssertionError(label);System.out.println("PASS "+label);}
 public static void main(String[] args){
  Set<String> hidden=new HashSet<>();hidden.add("https://example.jp/漫画/作品 A/");
  check(HiddenFolders.hidden(hidden,"https://example.jp/漫画/作品%20A/"),"encoded and decoded folder equal");
  check(HiddenFolders.hidden(hidden,"https://example.jp/漫画/作品%20A/別/巻01.zip"),"descendants and books hidden");
  check(!HiddenFolders.hidden(hidden,"https://example.jp/漫画/作品%20A2/巻.zip"),"similar prefix is not hidden");
  check(!HiddenFolders.hidden(hidden,"https://other.jp/漫画/作品%20A/"),"different host not hidden");
  check(HiddenFolders.explicitlyHidden(hidden,"https://example.jp:443/漫画/作品%20A"),"same https default port and explicit setting");
  check(!HiddenFolders.explicitlyHidden(hidden,"https://example.jp/漫画/作品%20A/別/"),"child is not explicitly hidden");
  check(HiddenFolders.hasDescendant(hidden,"https://example.jp/漫画/"),"ancestor cover must not expose hidden descendant");
  check(!HiddenFolders.hasDescendant(hidden,"https://example.jp/その他/"),"unrelated cover unaffected");
  check(!HiddenFolders.hidden(hidden,""),"empty record is not hidden");
  HiddenFolders.Index index=new HiddenFolders.Index(hidden);
  check(index.hidden("https://example.jp/漫画/作品%20A/別/巻01.zip")&&!index.hidden("https://example.jp/漫画/作品%20A2/"),"preparsed hidden index preserves matching");
  check(index.hasDescendant("https://example.jp/漫画/")&&index.explicitlyHidden("https://example.jp:443/漫画/作品%20A"),"preparsed ancestor and exact match");
 }
}
