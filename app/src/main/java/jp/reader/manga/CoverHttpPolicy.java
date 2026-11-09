package jp.reader.manga;
import java.net.URL;
import java.util.Locale;
import java.util.regex.*;
final class CoverHttpPolicy {
  static boolean allowed(URL url){
    String h=url.getHost().toLowerCase(Locale.ROOT);
    return "https".equalsIgnoreCase(url.getProtocol()) && url.getUserInfo()==null && (url.getPort()==-1||url.getPort()==443)
      && (h.equals("covers.openlibrary.org")||h.equals("archive.org")||h.endsWith(".archive.org"));
  }
  static long freshnessMillis(String cacheControl,String ageHeader,long expires,long date,long now,long cap){
    String cc=cacheControl==null?"":cacheControl.toLowerCase(Locale.ROOT);
    if(Pattern.compile("(?:^|,)\\s*(?:no-store|no-cache|private)(?:\\s|,|$)").matcher(cc).find())return 0;
    long seconds=0;Matcher m=Pattern.compile("(?:^|,)\\s*max-age\\s*=\\s*\\\"?(\\d+)\\\"?").matcher(cc);
    try{
      if(m.find()){long max=Long.parseLong(m.group(1));long age=ageHeader==null?0:Math.max(0,Long.parseLong(ageHeader.trim()));seconds=Math.max(0,max-age);}
      else if(expires>0){long base=date>0?date:now;seconds=Math.max(0,(expires-base)/1000);if(ageHeader!=null)seconds=Math.max(0,seconds-Math.max(0,Long.parseLong(ageHeader.trim())));}
      return Math.min(cap,seconds>cap/1000?cap:seconds*1000);
    }catch(NumberFormatException e){return 0;}
  }
}
