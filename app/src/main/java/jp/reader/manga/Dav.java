package jp.reader.manga;

import android.content.Context;
import android.security.KeyChain;
import android.util.Base64;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

final class Dav {
  static final String USER_AGENT = "MangaReader/0.1 (Android)";
  private static final okhttp3.OkHttpClient SHARED_CLIENT=new okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(20,java.util.concurrent.TimeUnit.SECONDS).readTimeout(30,java.util.concurrent.TimeUnit.SECONDS).build();
  static final class Item { String url,name; boolean dir; long createdAt; Item(String u,String n,boolean d){this(u,n,d,0);} Item(String u,String n,boolean d,long c){url=u;name=n;dir=d;createdAt=c;} }
  final Context ctx; final String base,user,pass,alias; final SSLSocketFactory ssl; final X509TrustManager trust;
  private final SmbBackend smb;private final String smbSsid;private volatile boolean routeSelected,usingSmb;private boolean transferStarted;private final SmbAliases knownUrls;
  Dav(Context c,String b,String u,String p,String a) throws Exception {
    ctx=c; base=b; user=u; pass=p; alias=a;
    android.content.SharedPreferences preferences=c.getSharedPreferences("reader",Context.MODE_PRIVATE);
    String host=preferences.getString("smb_host",""),share=preferences.getString("smb_share",""),username=preferences.getString("smb_user","");smbSsid=preferences.getString("smb_ssid","");
    SmbBackend candidate=null;
    if(preferences.getBoolean("smb_enabled",false)&&SmbAddress.privateIpv4(host)&&!share.isEmpty()&&!username.isEmpty()&&SmbWifi.matches(c,smbSsid)){
      try{String password=SmbPasswordStore.read(preferences);candidate=new SmbBackend(b,host,445,share,username,password.toCharArray(),"");}catch(Exception|LinkageError ignored){/* invalid SMB settings: keep WebDAV available */}
    }
    smb=candidate;knownUrls=smb==null?null:new SmbAliases(b);
    if(smb!=null){
      try{org.json.JSONObject records=new org.json.JSONObject(preferences.getString("records","{}"));Iterator<String> keys=records.keys();while(keys.hasNext()){
        String key=keys.next();knownUrls.remember(key);org.json.JSONObject entry=records.optJSONObject(key);if(entry!=null){knownUrls.remember(entry.optString("url",""));knownUrls.remember(entry.optString("folder",""));}
      }}catch(Exception ignored){}
      for(String url:preferences.getStringSet("hidden_folders",Collections.emptySet()))knownUrls.remember(url);
    }
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
  private synchronized boolean useSmb() throws Exception {
    if(routeSelected){if(usingSmb&&!SmbWifi.matches(ctx,smbSsid)){if(transferStarted)throw new IOException("指定Wi-Fiから切断されました。本を開き直してください");usingSmb=false;}return usingSmb;}
    if(Thread.currentThread().isInterrupted())throw new InterruptedException("接続を中止しました");
    if(smb!=null&&SmbWifi.matches(ctx,smbSsid)){
      try{smb.list(base);usingSmb=true;}catch(Exception|LinkageError e){if(Thread.currentThread().isInterrupted())throw new IOException("接続を中止しました",e);usingSmb=false;}
    }
    routeSelected=true;return usingSmb;
  }
  String connectionName(){return !routeSelected?"端末保存または未判定":usingSmb?"自宅Samba":"WebDAV";}
  static final class Download implements Closeable {
    final InputStream stream;final long length;final Closeable closeable;
    Download(InputStream s,long n,Closeable c){stream=s;length=n;closeable=c;}
    public void close()throws IOException{closeable.close();}
  }
  Download openDownload(String url,java.util.function.Consumer<Closeable> register)throws Exception{
    synchronized(this){
      if(useSmb()){
        try{SmbBackend.Download d=smb.download(url);register.accept(d);transferStarted=true;return new Download(d.stream,d.length,d);}
        catch(Exception|LinkageError e){if(transferStarted||Thread.currentThread().isInterrupted())throw new IOException("Sambaに接続できません",e);usingSmb=false;}
      }
    }
    HttpURLConnection c=open(url,"GET");Closeable closer=()->c.disconnect();register.accept(closer);
    try{int code=c.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);InputStream stream=c.getInputStream();return new Download(stream,c.getContentLengthLong(),()->{try{stream.close();}finally{c.disconnect();}});}
    catch(Exception e){c.disconnect();throw e;}
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
    synchronized(this){if(useSmb()){
      try{List<Item> entries=new ArrayList<>();for(SmbBackend.Entry e:smb.list(url))entries.add(new Item(knownUrls.lookup(e.url),e.name,e.directory,e.createdAt));return entries;}
      catch(Exception|LinkageError e){if(transferStarted||Thread.currentThread().isInterrupted())throw new IOException("Sambaに接続できません",e);usingSmb=false;}
    }}
    URL root=new URL(base), targetUrl=new URL(url); if(!"https".equals(targetUrl.getProtocol()) || !root.getHost().equalsIgnoreCase(targetUrl.getHost()) || root.getPort()!=targetUrl.getPort()) throw new IOException("接続先が不正です");
    okhttp3.OkHttpClient.Builder builder=SHARED_CLIENT.newBuilder();
    if(ssl!=null)builder.sslSocketFactory(ssl,trust);
    okhttp3.Request.Builder req=new okhttp3.Request.Builder().url(url).header("Depth","1").header("User-Agent",USER_AGENT).header("Accept-Encoding","identity").method("PROPFIND",okhttp3.RequestBody.create("<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:displayname/><d:creationdate/></d:prop></d:propfind>",okhttp3.MediaType.get("application/xml; charset=utf-8")));
    if(!user.isEmpty())req.header("Authorization","Basic "+Base64.encodeToString((user+":"+pass).getBytes("UTF-8"),Base64.NO_WRAP));
    try(okhttp3.Response response=builder.build().newCall(req.build()).execute()) {if(response.code()!=207||response.body()==null)throw new IOException("WebDAV一覧取得失敗 HTTP "+response.code());
      List<DavXml.Entry> entries=DavXml.parse(read(response.body().byteStream(),4*1024*1024));List<Item> out=new ArrayList<>();
      for(DavXml.Entry entry:entries){try{String href=new URL(new URL(url),entry.href).toExternalForm();
        URL target=new URL(href);URL origin=new URL(base);if(!target.getProtocol().equals("https") || !target.getHost().equalsIgnoreCase(origin.getHost()) || target.getPort()!=origin.getPort())continue;
        String targetPath=URLDecoder.decode(target.getPath().replace("+","%2B"),"UTF-8").replaceAll("/$","");
        String currentPath=URLDecoder.decode(targetUrl.getPath().replace("+","%2B"),"UTF-8").replaceAll("/$","");
        if(targetPath.equals(currentPath) && target.getPort()==targetUrl.getPort() && target.getHost().equalsIgnoreCase(targetUrl.getHost()))continue;
        boolean dir=entry.directory;String path=target.getPath().replaceAll("/$","");String name=URLDecoder.decode(path.substring(path.lastIndexOf('/')+1).replace("+","%2B"),"UTF-8");if(dir&&!href.endsWith("/"))href+="/";
        if(dir||name.toLowerCase(Locale.ROOT).endsWith(".zip")||name.toLowerCase(Locale.ROOT).endsWith(".cbz")||name.toLowerCase(Locale.ROOT).endsWith(".rar")||name.toLowerCase(Locale.ROOT).endsWith(".cbr"))out.add(new Item(href,name,dir,NewArrivals.parseCreation(entry.creationDate)));
      }catch(IllegalArgumentException|java.net.MalformedURLException ignored){/* skip malformed entries, not the entire listing */}}return out;
    }
  }
  static class Range {byte[] data;long total;Range(byte[] d,long t){data=d;total=t;}}
  Range range(String u,long start,long end) throws Exception {
    File cached=DownloadCache.available(ctx,u);
    if(cached!=null){try(RandomAccessFile f=new RandomAccessFile(cached,"r")){long total=f.length();long first=start<0?Math.max(0,total-end):start;long last=start<0?total-1:Math.min(end,total-1);if(first<0||last<first||last-first+1>33L*1024*1024)throw new IOException("保存ファイルの範囲が不正です");byte[] bytes=new byte[(int)(last-first+1)];f.seek(first);f.readFully(bytes);return new Range(bytes,total);}catch(FileNotFoundException ignored){/* deleted concurrently: use network */}}
    synchronized(this){if(useSmb()){
      try{SmbBackend.Range result=smb.range(u,start,end);transferStarted=true;return new Range(result.data,result.total);}
      catch(Exception|LinkageError e){if(transferStarted||Thread.currentThread().isInterrupted())throw new IOException("Sambaに接続できません",e);usingSmb=false;}
    }}
    HttpURLConnection c=open(u,"GET");c.setRequestProperty("Range",start<0?"bytes=-"+end:"bytes="+start+"-"+end);
    try {int code=c.getResponseCode();if(code!=206)throw new IOException(HttpErrors.range(code));
      String cr=c.getHeaderField("Content-Range");if(cr==null||!cr.matches("bytes \\d+-\\d+/\\d+"))throw new IOException("Content-Rangeが不正です");String[] p=cr.substring(6).split("[-/]");long first=Long.parseLong(p[0]),last=Long.parseLong(p[1]),total=Long.parseLong(p[2]);long expectedStart=start<0?Math.max(0,total-end):start; long expectedEnd=start<0?total-1:Math.min(end,total-1);
      if(first!=expectedStart||last!=expectedEnd||last<first||last-first+1>33L*1024*1024)throw new IOException("Range応答が不正か上限超過です");byte[] data=read(c.getInputStream(),33*1024*1024);if(data.length!=last-first+1)throw new IOException("通信が途中で切れました");return new Range(data,total);
    }finally{c.disconnect();}
  }
}
