package jp.reader.manga;
import java.text.Normalizer;
import java.util.Locale;
final class CoverMatch {
  private static String normalized(String s){return Normalizer.normalize(s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}\\p{Z}]+","");}
  static boolean matchesFirstVolume(String requested,String result){
    String title=normalized(requested),value=normalized(result);
    if(title.isEmpty()||value.contains("lightnovel")||value.contains("小説")||!value.startsWith(title))return false;
    return value.substring(title.length()).matches("^(第)?1(巻|巻目|manga|漫画|comic|comics|vol|volume)*$");
  }
}
