package jp.reader.manga;

import java.text.Normalizer;
import java.util.Locale;

/** Case- and width-insensitive matching on the original entry name. */
final class BrowseSearch {
  private static String normalize(String value){
    return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }
  static boolean matches(String name,boolean isDirectory,String query){
    String q=normalize(query.trim());
    return q.isEmpty() || (isDirectory && normalize(name).contains(q));
  }
}
