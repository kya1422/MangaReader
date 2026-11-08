package jp.reader.manga;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.util.*;
import okhttp3.*;

/** Downloads from public GitHub only. The installed app's signing certificate is the trust anchor. */
final class Updater {
  static final String API="https://api.github.com/repos/kya1422/MangaReader/releases/latest";
  static final long LIMIT=100L*1024*1024;
  static class Release {String name,url;int code;Release(String n,String u,int c){name=n;url=u;code=c;}}
  static boolean allowed(HttpUrl u){String h=u.host().toLowerCase(Locale.ROOT);return u.isHttps()&&(h.equals("api.github.com")||h.equals("github.com")||h.equals("objects.githubusercontent.com")||h.equals("release-assets.githubusercontent.com"));}
  static OkHttpClient client(){return new OkHttpClient.Builder().connectTimeout(20,java.util.concurrent.TimeUnit.SECONDS).readTimeout(60,java.util.concurrent.TimeUnit.SECONDS).addNetworkInterceptor(chain->{if(!allowed(chain.request().url()))throw new IOException("更新先のURLが許可されていません");return chain.proceed(chain.request());}).build();}
  static Request request(String url){return new Request.Builder().url(url).header("User-Agent","MangaReader-Updater/1.0").header("Accept","application/vnd.github+json").build();}
  static Release latest() throws Exception {
    try(Response r=client().newCall(request(API)).execute()){
      if(!r.isSuccessful()||r.body()==null)throw new IOException("GitHubのリリースを取得できません (HTTP "+r.code()+")。リポジトリが公開されているか確認してください。");
      byte[] data=Dav.read(r.body().byteStream(),1024*1024);JSONObject json=new JSONObject(new String(data,"UTF-8"));String tag=json.optString("tag_name");
      if(!tag.matches("build-[0-9]{1,8}"))throw new IOException("更新バージョンが不正です");int code=1000+Integer.parseInt(tag.substring(6));JSONArray assets=json.optJSONArray("assets");String url=null;if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a!=null&&"MangaReader.apk".equals(a.optString("name")))url=a.optString("browser_download_url");}
      if(url==null)throw new IOException("リリースにMangaReader.apkがありません");HttpUrl parsed=HttpUrl.parse(url);if(parsed==null||!allowed(parsed)||!"github.com".equals(parsed.host()))throw new IOException("APKのURLが不正です");return new Release(json.optString("name",tag),url,code);
    }
  }
  static long version(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
  static Signature[] signatures(PackageInfo p){return Build.VERSION.SDK_INT>=28?(p.signingInfo==null?null:p.signingInfo.getApkContentsSigners()):p.signatures;}
  static File download(Context ctx,Release release) throws Exception {
    File dir=new File(ctx.getCacheDir(),"update");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("保存場所を作成できません");File tmp=new File(dir,"download.tmp"),out=UpdateProvider.file(ctx);tmp.delete();
    try {
      try(Response r=client().newCall(request(release.url)).execute()){
        if(!r.isSuccessful()||r.body()==null)throw new IOException("APK取得失敗 (HTTP "+r.code()+")");long length=r.body().contentLength();if(length>LIMIT)throw new IOException("APKが大きすぎます");
        try(InputStream in=r.body().byteStream();FileOutputStream f=new FileOutputStream(tmp)){byte[] b=new byte[32768];int n;long count=0;while((n=in.read(b))!=-1){count+=n;if(count>LIMIT)throw new IOException("APKが大きすぎます");f.write(b,0,n);}if(length>=0&&count!=length)throw new IOException("APKのダウンロードが途中で切れました");f.getFD().sync();}
      }
      PackageManager pm=ctx.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
      PackageInfo installed=pm.getPackageInfo(ctx.getPackageName(),flags),incoming=pm.getPackageArchiveInfo(tmp.getAbsolutePath(),flags);
      if(incoming==null||!ctx.getPackageName().equals(incoming.packageName))throw new IOException("APKのアプリIDが一致しません");
      Signature[] a=signatures(installed),b=signatures(incoming);if(a==null||b==null||a.length==0||b.length==0||!new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b))))throw new IOException("APKの署名がインストール済みアプリと一致しません");
      if(version(incoming)!=release.code||version(incoming)<=version(installed))throw new IOException("APKのバージョンが不正か、すでに最新版です");
      if(!tmp.renameTo(out))throw new IOException("APKの保存に失敗しました");return out;
    }finally{tmp.delete();}
  }
}
