package jp.reader.manga;

import android.content.pm.PackageInfo;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.util.*;
import okhttp3.*;

/** Checks public GitHub release metadata; installation is handled outside this app. */
final class Updater {
  static final String API="https://api.github.com/repos/kya1422/MangaReader/releases/latest";
  static class Release {String name,page;int code;Release(String n,String p,int c){name=n;page=p;code=c;}}
  static boolean allowed(HttpUrl u){String h=u.host().toLowerCase(Locale.ROOT);return u.isHttps()&&(h.equals("api.github.com")||h.equals("github.com")||h.equals("objects.githubusercontent.com")||h.equals("release-assets.githubusercontent.com"));}
  static OkHttpClient client(){return new OkHttpClient.Builder().connectTimeout(20,java.util.concurrent.TimeUnit.SECONDS).readTimeout(60,java.util.concurrent.TimeUnit.SECONDS).addNetworkInterceptor(chain->{if(!allowed(chain.request().url()))throw new IOException("更新先のURLが許可されていません");return chain.proceed(chain.request());}).build();}
  static Request request(String url){return new Request.Builder().url(url).header("User-Agent","MangaReader-Updater/1.0").header("Accept","application/vnd.github+json").build();}
  static Release latest() throws Exception {
    try(Response r=client().newCall(request(API)).execute()){
      if(!r.isSuccessful()||r.body()==null)throw new IOException("GitHubのリリースを取得できません (HTTP "+r.code()+")。リポジトリが公開されているか確認してください。");
      byte[] data=Dav.read(r.body().byteStream(),1024*1024);JSONObject json=new JSONObject(new String(data,"UTF-8"));String tag=json.optString("tag_name");
      if(!tag.matches("build-[0-9]{1,8}"))throw new IOException("更新バージョンが不正です");int code=1000+Integer.parseInt(tag.substring(6));JSONArray assets=json.optJSONArray("assets");String url=null;if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject a=assets.optJSONObject(i);if(a!=null&&"MangaReader.apk".equals(a.optString("name")))url=a.optString("browser_download_url");}
      if(url==null)throw new IOException("リリースにMangaReader.apkがありません");HttpUrl parsed=HttpUrl.parse(url);if(parsed==null||!allowed(parsed)||!"github.com".equals(parsed.host()))throw new IOException("APKのURLが不正です");return new Release(json.optString("name",tag),"https://github.com/kya1422/MangaReader/releases/tag/"+tag,code);
    }
  }
  static long version(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
}
