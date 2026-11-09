package jp.reader.manga;

import java.text.Normalizer;
import java.util.Locale;

/** Case- and width-insensitive matching on the original entry name. */
final class BrowseSearch {
  private static String normalize(String value){
    return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }
  static boolean matches(String name,String query){
    return normalize(name).contains(normalize(query.trim()));
  }
}
