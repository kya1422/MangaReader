package jp.reader.manga;
import java.net.URL;
class TestCoverHttpPolicy {
 static void ok(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  ok(CoverHttpPolicy.allowed(new URL("https://covers.openlibrary.org/b/id/1-M.jpg")));
  ok(CoverHttpPolicy.allowed(new URL("https://ia800505.us.archive.org/view_archive.php")));
  ok(!CoverHttpPolicy.allowed(new URL("https://archive.org.evil.example/x")));
  ok(!CoverHttpPolicy.allowed(new URL("http://archive.org/x")));
  ok(!CoverHttpPolicy.allowed(new URL("https://evil@archive.org/x")));
  ok(CoverHttpPolicy.freshnessMillis("public, max-age=10800","100",0,0,0,604800000)==10700000);
  ok(CoverHttpPolicy.freshnessMillis("no-store, max-age=100",null,0,0,0,604800000)==0);
  ok(CoverHttpPolicy.freshnessMillis("public",null,0,0,0,604800000)==0);
  System.out.println("PASS safe redirect hosts and HTTP cache lifetime");
 }
}
