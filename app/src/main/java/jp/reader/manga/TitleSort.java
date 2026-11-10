package jp.reader.manga;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Locale;

/** Compares the titles shown by the reader, using Japanese collation and numeric runs. */
final class TitleSort {
  private final Collator collator=Collator.getInstance(Locale.JAPAN);
  static String displayTitle(String name){
    return name.replaceFirst("^\\s*\\([^)]*\\)\\s*","").replaceFirst("^\\s*\\[[^]]*\\]\\s*","").replaceFirst("(?i)\\.(zip|cbz|rar|cbr)$","").trim();
  }
  int compare(String a,String b){
    String x=Normalizer.normalize(displayTitle(a),Normalizer.Form.NFKC);
    String y=Normalizer.normalize(displayTitle(b),Normalizer.Form.NFKC);
    int c=natural(x,y);return c!=0?c:natural(Normalizer.normalize(a,Normalizer.Form.NFKC),Normalizer.normalize(b,Normalizer.Form.NFKC));
  }
  private int natural(String x,String y){
    int i=0,j=0;
    while(i<x.length() && j<y.length()){
      boolean xd=digit(x.charAt(i)),yd=digit(y.charAt(j));
      if(xd&&yd){
        int ie=i,je=j;while(ie<x.length()&&digit(x.charAt(ie)))ie++;while(je<y.length()&&digit(y.charAt(je)))je++;
        int iz=i,jz=j;while(iz<ie-1&&x.charAt(iz)=='0')iz++;while(jz<je-1&&y.charAt(jz)=='0')jz++;
        int c=Integer.compare(ie-iz,je-jz);if(c==0)c=x.substring(iz,ie).compareTo(y.substring(jz,je));if(c!=0)return c;i=ie;j=je;
      }else{
        int ie=i,je=j;while(ie<x.length()&&!digit(x.charAt(ie)))ie++;while(je<y.length()&&!digit(y.charAt(je)))je++;
        if(ie==i||je==j){int c=collator.compare(x.substring(i,i+1),y.substring(j,j+1));if(c!=0)return c;i++;j++;continue;}
        int c=collator.compare(x.substring(i,ie),y.substring(j,je));if(c!=0)return c;i=ie;j=je;
      }
    }
    return Integer.compare(x.length()-i,y.length()-j);
  }
  private static boolean digit(char c){return c>='0'&&c<='9';}
}
