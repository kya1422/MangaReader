package jp.reader.manga;

import android.content.Context;
import android.security.KeyChain;
import android.util.Base64;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

final class Dav {
  static final String USER_AGENT = "MangaReader/0.1 (Android)";
  static final class Item { String url,name; boolean dir; Item(String u,String n,boolean d){url=u;name=n;dir=d;} }
  final Context ctx; final String base,user,pass,alias; final SSLSocketFactory ssl; final X509TrustManager trust;
  Dav(Context c,String b,String u,String p,String a) throws Exception {
    ctx=c; base=b; user=u; pass=p; alias=a;
    if (!"https".equalsIgnoreCase(new URL(b).getProtocol())) throw new IOException("HTTPSのURLを指定してください");
    TrustManagerFactory tmf=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); tmf.init((KeyStore)null); trust=(X509TrustManager)tmf.getTrustManagers()[0];
    if(a==null || a.isEmpty()) {ssl=null;return;}
    PrivateKey key=KeyChain.getPrivateKey(c,a); X509Certificate[] cert=KeyChain.getCertificateChain(c,a);
    if(key==null || cert==null) throw new IOException("証明書を取得できません。もう一度選択してください");
    X509ExtendedKeyManager km=new X509ExtendedKeyManager(){
      public String[] getClientAliases(String k,java.security.Principal[] i){return new String[]{a};}
      public String chooseClientAlias(String[] k,java.security.Principal[] i,java.net.Socket s){return a;}
      public String chooseEngineClientAlias(String[] k,java.security.Principal[] i,SSLEngine e){return a;}
      public String[] getServerAliases(String k,java.security.Principal[] i){return null;}
      public String chooseServerAlias(String k,java.security.Principal[] i,java.net.Socket s){return null;}
      public X509Certificate[] getCertificateChain(String x){return cert;}
      public PrivateKey getPrivateKey(String x){return key;}
    };
    SSLContext sc=SSLContext.getInstance("TLS"); sc.init(new KeyManager[]{km},null,null);ssl=sc.getSocketFactory();
  }
  HttpURLConnection open(String u,String method) throws Exception {
    URL original=new URL(base), target=new URL(u);
    if(!"https".equalsIgnoreCase(target.getProtocol()) || !original.getHost().equalsIgnoreCase(target.getHost()) || original.getPort()!=target.getPort()) throw new IOException("設定したWebDAV以外への接続は許可されません");
    HttpsURLConnection c=(HttpsURLConnection)target.openConnection(); if(ssl!=null)c.setSSLSocketFactory(ssl);
    c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setRequestMethod(method);c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("User-Agent",USER_AGENT);
    if(!user.isEmpty())c.setRequestProperty("Authorization","Basic "+Base64.encodeToString((user+":"+pass).getBytes("UTF-8"),Base64.NO_WRAP));return c;
  }
  static byte[] read(InputStream in,int max) throws IOException { try(InputStream s=in;ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n,total=0;while((n=s.read(b))!=-1){total+=n;if(total>max)throw new IOException("データが上限を超えました");o.write(b,0,n);}return o.toByteArray();} }
  List<Item> list(String url) throws Exception {
    URL root=new URL(base), targetUrl=new URL(url); if(!"https".equals(targetUrl.getProtocol()) || !root.getHost().equalsIgnoreCase(targetUrl.getHost()) || root.getPort()!=targetUrl.getPort()) throw new IOException("接続先が不正です");
    okhttp3.OkHttpClient.Builder builder=new okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(20,java.util.concurrent.TimeUnit.SECONDS).readTimeout(30,java.util.concurrent.TimeUnit.SECONDS);
    if(ssl!=null)builder.sslSocketFactory(ssl,trust);
    okhttp3.Request.Builder req=new okhttp3.Request.Builder().url(url).header("Depth","1").header("User-Agent",USER_AGENT).header("Accept-Encoding","identity").method("PROPFIND",okhttp3.RequestBody.create("<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:displayname/></d:prop></d:propfind>",okhttp3.MediaType.get("application/xml; charset=utf-8")));
    if(!user.isEmpty())req.header("Authorization","Basic "+Base64.encodeToString((user+":"+pass).getBytes("UTF-8"),Base64.NO_WRAP));
    try(okhttp3.Response response=builder.build().newCall(req.build()).execute()) {if(response.code()!=207||response.body()==null)throw new IOException("WebDAV一覧取得失敗 HTTP "+response.code());
      DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
      Document d=f.newDocumentBuilder().parse(new ByteArrayInputStream(read(response.body().byteStream(),4*1024*1024)));NodeList rs=d.getElementsByTagNameNS("DAV:","response");List<Item> out=new ArrayList<>();
      for(int i=0;i<rs.getLength();i++){Element e=(Element)rs.item(i);NodeList h=e.getElementsByTagNameNS("DAV:","href");if(h.getLength()==0)continue;String href=new URL(new URL(url),h.item(0).getTextContent()).toExternalForm();if(href.replaceAll("/$","").equals(url.replaceAll("/$","")))continue;
        URL target=new URL(href);URL origin=new URL(base);if(!target.getProtocol().equals("https") || !target.getHost().equalsIgnoreCase(origin.getHost()) || target.getPort()!=origin.getPort())continue;
        boolean dir=e.getElementsByTagNameNS("DAV:","collection").getLength()>0;String path=target.getPath().replaceAll("/$","");String name=URLDecoder.decode(path.substring(path.lastIndexOf('/')+1).replace("+","%2B"),"UTF-8");if(dir&&!href.endsWith("/"))href+="/";
        if(dir||name.toLowerCase(Locale.ROOT).endsWith(".zip")||name.toLowerCase(Locale.ROOT).endsWith(".cbz"))out.add(new Item(href,name,dir));
      }return out;
    }
  }
  static class Range {byte[] data;long total;Range(byte[] d,long t){data=d;total=t;}}
  Range range(String u,long start,long end) throws Exception {
    File cached=DownloadCache.available(ctx,u);
    if(cached!=null){try(RandomAccessFile f=new RandomAccessFile(cached,"r")){long total=f.length();long first=start<0?Math.max(0,total-end):start;long last=start<0?total-1:Math.min(end,total-1);if(first<0||last<first||last-first+1>33L*1024*1024)throw new IOException("保存ファイルの範囲が不正です");byte[] bytes=new byte[(int)(last-first+1)];f.seek(first);f.readFully(bytes);return new Range(bytes,total);}catch(FileNotFoundException ignored){/* deleted concurrently: use network */}}
    HttpURLConnection c=open(u,"GET");c.setRequestProperty("Range",start<0?"bytes=-"+end:"bytes="+start+"-"+end);
    try {int code=c.getResponseCode();if(code!=206)throw new IOException("部分取得に非対応です（HTTP "+code+"）。WebDAVとCloudflareのRange設定を確認してください。");
      String cr=c.getHeaderField("Content-Range");if(cr==null||!cr.matches("bytes \\d+-\\d+/\\d+"))throw new IOException("Content-Rangeが不正です");String[] p=cr.substring(6).split("[-/]");long first=Long.parseLong(p[0]),last=Long.parseLong(p[1]),total=Long.parseLong(p[2]);long expectedStart=start<0?Math.max(0,total-end):start; long expectedEnd=start<0?total-1:Math.min(end,total-1);
      if(first!=expectedStart||last!=expectedEnd||last<first||last-first+1>33L*1024*1024)throw new IOException("Range応答が不正か上限超過です");byte[] data=read(c.getInputStream(),33*1024*1024);if(data.length!=last-first+1)throw new IOException("通信が途中で切れました");return new Range(data,total);
    }finally{c.disconnect();}
  }
}
