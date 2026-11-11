package jp.reader.manga;

import android.app.*;
import android.os.*;
import android.security.KeyChain;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  android.content.SharedPreferences prefs; ExecutorService worker=Executors.newFixedThreadPool(2); final ExecutorService browseWorker=Executors.newFixedThreadPool(2); Handler ui=new Handler(Looper.getMainLooper()); volatile int generation=0; LinearLayout root; Dav dav; RemoteZip zip; String bookUrl,bookName,folderUrl,folderName; int page=0,next=0; boolean reading=false; HashMap<Integer,Integer> previous=new HashMap<>(); TreeSet<Integer> cuts=new TreeSet<>(); Bitmap right,left; String alias=""; String downloadState=""; TextView downloadLabel; boolean controlsVisible=false; boolean leftToRightSpread=false; final ExecutorService thumbWorker=Executors.newSingleThreadExecutor(); final ArrayList<ImageView> thumbnailViews=new ArrayList<>(); final ArrayList<Bitmap> thumbnailBitmaps=new ArrayList<>(); volatile int previewSerial=0; Runnable previewPending; final ExecutorService prefetchWorker=Executors.newSingleThreadExecutor(); boolean prefetchRequested=false; String prefetchStatus=""; TextView prefetchLabel; JSONObject recordCache; String screen="home"; final ArrayDeque<String[]> folders=new ArrayDeque<>();
  String manualCoverTarget,manualCoverName; boolean manualCoverSaving;
  EditText pendingWifiName; int pendingWifiGeneration;
  void fillWifiName(EditText field){
    String name=SmbWifi.currentSsid(this);
    if(name==null){message("Wi-Fi名を取得できません。Wi-Fi接続、正確な位置情報の許可、端末の位置情報設定を確認してください。手入力もできます。");return;}
    field.setText(name);message("現在のWi-Fi名を入力しました。保存を押してください。");
  }
  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
    super.onRequestPermissionsResult(requestCode,permissions,grantResults);
    if(requestCode!=302)return;
    EditText field=pendingWifiName;pendingWifiName=null;
    if(field==null||pendingWifiGeneration!=generation||!"settings".equals(screen)||!field.isAttachedToWindow())return;
    if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)fillWifiName(field);
    else message("正確な位置情報が許可されていないためWi-Fi名を取得できません。WebDAVは引き続き利用できます。");
  }
  final ExecutorService arrivalsWorker=Executors.newFixedThreadPool(2);
  NewArrivals.Result arrivalsCache;String arrivalsCacheRoot;Set<String> arrivalsCacheHidden;
  volatile Future<?> arrivalsJob; private final Object arrivalsLock=new Object();private Dav arrivalsClient;
  void cancelArrivals(){Future<?> job=arrivalsJob;arrivalsJob=null;if(job!=null)job.cancel(true);Dav client;synchronized(arrivalsLock){client=arrivalsClient;}if(client!=null)client.cancelPending();}
  final CoverDispatcher coverWorker=new CoverDispatcher(); final ExecutorService bookCloser=Executors.newSingleThreadExecutor(); volatile int coverSerial=0;
  void closeBookAsync(RemoteZip book){
    if(book==null)return;book.cancel();
    Runnable release=()->{try{book.close();}finally{book.dav.close();}};
    try{bookCloser.execute(release);}catch(java.util.concurrent.RejectedExecutionException e){Thread t=new Thread(release,"close-book");t.setDaemon(true);t.start();}
  }
  void closeCurrentBook(){RemoteZip old=zip;zip=null;closeBookAsync(old);}
  interface Task<T>{T run() throws Exception;} interface Done<T>{void accept(T x) throws Exception;}
  public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("reader",MODE_PRIVATE);alias=prefs.getString("alias","");DownloadCache.cleanup(this);home();}
  void clear(){
    generation++;cancelArrivals();
    coverSerial++;
    clearThumbnail();
    systemBars(reading && !controlsVisible);
    root=new LinearLayout(this);
    root.setOrientation(1);
    root.setBackgroundColor(0xfffafafa);
    // Android 15+ may draw edge-to-edge. Keep buttons out of system gesture/status/navigation areas.
    root.setOnApplyWindowInsetsListener((view,insets)->{
      if(reading) view.setPadding(0,0,0,0);
      else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
          insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
      return insets;
    });
    setContentView(root);
    root.requestApplyInsets();
  }
  void loadingBars(){
    systemBars(false);
    root.setOnApplyWindowInsetsListener((view,insets)->{
      view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
    });
    root.requestApplyInsets();
  }
  TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(0xff111111);v.setPadding(16,12,16,12);return v;}
  TextView entry(String title,String details){
    TextView v=text("",22);
    String label=title+"\n"+details;
    SpannableString styled=new SpannableString(label);
    styled.setSpan(new StyleSpan(Typeface.BOLD),0,title.length(),0);
    if(label.length()>title.length())styled.setSpan(new RelativeSizeSpan(0.64f),title.length(),label.length(),0);
    v.setText(styled);return v;
  }
  void systemBars(boolean immersive){
    int layout=reading?(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE):0;
    int flags=layout|(immersive?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY):0);
    getWindow().getDecorView().setSystemUiVisibility(flags);
  }
  void clearThumbnail(){
    previewSerial++;
    if(previewPending!=null){ui.removeCallbacks(previewPending);previewPending=null;}
    for(ImageView view:thumbnailViews)view.setImageDrawable(null);
    thumbnailViews.clear();
    for(Bitmap bitmap:thumbnailBitmaps)bitmap.recycle();
    thumbnailBitmaps.clear();
  }
  Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->r.run());return b;}
  LinearLayout scroll(){ScrollView sv=new ScrollView(this);LinearLayout l=new LinearLayout(this);l.setOrientation(1);sv.addView(l);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));return l;}
  void message(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  <T> void async(Task<T> task,Done<T> done){async(task,done,e->message("エラー: "+e.getMessage()));}
  <T> void async(Task<T> task,Done<T> done,java.util.function.Consumer<Exception> fail){
    int id=generation;ExecutorService executor="reader".equals(screen)?worker:browseWorker;
    executor.execute(()->{if(id!=generation)return;try{T result=task.run();ui.post(()->{
      if(id!=generation){if(result instanceof Spread){Spread discarded=(Spread)result;discarded.a.recycle();if(discarded.b!=null)discarded.b.recycle();}else if(result instanceof RemoteZip)closeBookAsync((RemoteZip)result);return;}
      try{done.accept(result);}catch(Exception e){fail.accept(e);}
    });}catch(Exception e){ui.post(()->{if(id==generation)fail.accept(e);});}});
  }
  JSONObject records(){if(recordCache==null){try{recordCache=new JSONObject(prefs.getString("records","{}"));}catch(Exception e){recordCache=new JSONObject();}}return recordCache;}
  void put(String key,JSONObject value){try{JSONObject all=records();all.put(key,value);prefs.edit().putString("records",all.toString()).apply();}catch(Exception e){message(e.toString());}}
  JSONObject record(String key){JSONObject j=records().optJSONObject(key);return j==null?new JSONObject():j;}
  Set<String> hiddenFolders(){return new HashSet<>(prefs.getStringSet("hidden_folders",Collections.emptySet()));}
  boolean hiddenItem(String url){return HiddenFolders.hidden(hiddenFolders(),url);}
  boolean hiddenRecord(JSONObject o){return hiddenItem(o.optString("folder"))||hiddenItem(o.optString("url"));}
  void setHiddenFolder(String url,boolean hide){
    Set<String> all=hiddenFolders();all.removeIf(old->HiddenFolders.contains(old,url)&&HiddenFolders.contains(url,old));
    if(hide)all.add(url);prefs.edit().putStringSet("hidden_folders",all).apply();
  }
  String clean(String s){return TitleSort.displayTitle(s);}
  String author(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\[([^]]+)\\]").matcher(s);return m.find()?m.group(1):"";}
  void home(){manualCoverTarget=null;manualCoverName=null;screen="home";folders.clear();DownloadCache.pin(null);DownloadCache.cleanup(this);reading=false;closeCurrentBook();clear();release();root.addView(text("漫画リーダー",25));LinearLayout bar=new LinearLayout(this);bar.addView(button("フォルダ",()->browse(prefs.getString("url",""),"フォルダ")));bar.addView(button("接続設定",this::settings));root.addView(bar);LinearLayout list=scroll();
    list.addView(text("新着・未読（1か月）",21));
    LinearLayout arrivalHeader=new LinearLayout(this);TextView arrivalStatus=text("新着を確認中…",13);arrivalHeader.addView(arrivalStatus,new LinearLayout.LayoutParams(0,-2,1));arrivalHeader.addView(button("更新",()->{arrivalsCache=null;home();}));list.addView(arrivalHeader);
    LinearLayout arrivalList=new LinearLayout(this);arrivalList.setOrientation(LinearLayout.VERTICAL);list.addView(arrivalList);
    startNewArrivals(arrivalStatus,arrivalList);
    JSONObject all=records();ArrayList<JSONObject> recent=new ArrayList<>();Iterator<String> keys=all.keys();while(keys.hasNext()){JSONObject o=all.optJSONObject(keys.next());if(o!=null&&!o.optBoolean("finished")&&!hiddenRecord(o))recent.add(o);}recent.sort((a,b)->Long.compare(b.optLong("time"),a.optLong("time")));
    list.addView(text("最近読んだ本",21));if(recent.isEmpty())list.addView(text("読みかけの本はありません",14));for(JSONObject o:recent){String name=o.optString("name"),url=o.optString("url"),fu=o.optString("folder"),fn=o.optString("folderName");TextView v=entry(clean(name),author(name)+"  途中 "+(o.optInt("page")+1)+" / "+o.optInt("total"));list.addView(v);v.setOnClickListener(w->openBook(url,name,fu,fn));}
    list.addView(text("読みかけのシリーズ",21));HashSet<String> seen=new HashSet<>();ArrayList<JSONObject> series=new ArrayList<>();for(JSONObject o:recent)if(seen.add(o.optString("folder")))series.add(o);
    String sort=prefs.getString("sort","最近読んだ順");if(sort.equals("タイトル順")){TitleSort ts=new TitleSort();series.sort((a,b)->ts.compare(a.optString("folderName"),b.optString("folderName")));}else if(sort.equals("著者順"))series.sort((a,b)->RemoteZip.natural(author(a.optString("folderName")),author(b.optString("folderName"))));
    list.addView(button("並び替え: "+sort,()->new AlertDialog.Builder(this).setTitle("並び替え").setItems(new String[]{"最近読んだ順","タイトル順","著者順"},(d,n)->{prefs.edit().putString("sort",new String[]{"最近読んだ順","タイトル順","著者順"}[n]).apply();home();}).show()));
    for(JSONObject o:series){String fn=o.optString("folderName"),fu=o.optString("folder"),bn=o.optString("name");TextView v=entry(clean(fn),author(fn)+"\n"+clean(bn)+"  途中 "+(o.optInt("page")+1)+" / "+o.optInt("total"));list.addView(v);v.setOnClickListener(w->browse(fu,fn));}
  }
  void displayNewArrivals(TextView status,LinearLayout list,NewArrivals.Result result,long now,long cutoff){
    ArrayList<NewArrivals.Book> visible=new ArrayList<>();HiddenFolders.Index hidden=new HiddenFolders.Index(hiddenFolders());JSONObject all=records();
    for(NewArrivals.Book book:result.books)if(book.date>=cutoff&&book.date<=now&&!all.has(book.url)&&!hidden.hidden(book.url))visible.add(book);
    String label=(result.complete?"取得完了":"途中結果")+"："+visible.size()+"冊 / "+result.scanned+"フォルダ";
    if(visible.size()>100)label+="（新しい順に100冊表示）";
    if(result.errors>0)label+="・取得失敗 "+result.errors+"フォルダ";
    if(result.unknown>0)label+="・作成日不明 "+result.unknown+"冊（対象外）";
    if(result.limited)label+="・探索または表示の上限に到達（一部のみ）";
    status.setText(label);list.removeAllViews();
    if(result.complete&&visible.isEmpty())list.addView(text(result.errors>0||result.limited?"一部を取得できていないため、結果は不完全です":"対象の未読本はありません",14));
    java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy/MM/dd",java.util.Locale.JAPAN);
    int shown=0;for(NewArrivals.Book book:visible){
      if(shown++>=100)break;
      TextView row=entry(clean(book.name),clean(book.folderName)+"  作成日 "+format.format(new java.util.Date(book.date)));list.addView(row);
      row.setOnClickListener(v->openBook(book.url,book.name,book.folder,book.folderName));
    }
  }
  void startNewArrivals(TextView status,LinearLayout list){
    final String rootUrl=prefs.getString("url","");if(rootUrl.isEmpty()){status.setText("接続設定後に新着を確認できます");return;}
    final int serial=generation;Set<String> hidden=hiddenFolders();
    final long now=System.currentTimeMillis(),cutoff=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).minusMonths(1).toInstant().toEpochMilli();
    if(arrivalsCache!=null&&rootUrl.equals(arrivalsCacheRoot)&&hidden.equals(arrivalsCacheHidden)){
      displayNewArrivals(status,list,arrivalsCache,now,cutoff);status.append("（前回の結果・更新で再取得）");return;
    }
    Set<String> read=new HashSet<>();Iterator<String> keys=records().keys();while(keys.hasNext())read.add(keys.next());
    arrivalsJob=arrivalsWorker.submit(()->{
      if(serial!=generation||Thread.currentThread().isInterrupted())return;
      try{
        try(Dav client=client()){
        synchronized(arrivalsLock){if(serial!=generation||Thread.currentThread().isInterrupted())return;arrivalsClient=client;}
        try{NewArrivals.scan(rootUrl,client::list,read,hidden,now,cutoff,()->serial!=generation||Thread.currentThread().isInterrupted(),result->ui.post(()->{
          if(serial!=generation||!"home".equals(screen))return;
          arrivalsCache=result;arrivalsCacheRoot=rootUrl;arrivalsCacheHidden=new HashSet<>(hidden);
          long current=System.currentTimeMillis();long currentCutoff=java.time.Instant.ofEpochMilli(current).atZone(java.time.ZoneId.systemDefault()).minusMonths(1).toInstant().toEpochMilli();
          displayNewArrivals(status,list,result,current,currentCutoff);
        }));}finally{synchronized(arrivalsLock){if(arrivalsClient==client)arrivalsClient=null;}}}
      }catch(Exception e){ui.post(()->{if(serial==generation&&"home".equals(screen))status.setText("新着を取得できません: "+(e.getMessage()==null?"不明なエラー":e.getMessage()));});}
    });
  }
  void settings(){screen="settings";alias=prefs.getString("alias","");clear();root.addView(button("← ホーム",this::home));root.addView(text("WebDAV接続設定",23));LinearLayout l=scroll();EditText u=new EditText(this),n=new EditText(this),p=new EditText(this);u.setHint("https://example.com/webdav/");u.setSingleLine();u.setText(prefs.getString("url",""));n.setHint("ユーザー名（必要な場合）");n.setText(prefs.getString("user",""));p.setHint("パスワード（端末内に保存）");p.setInputType(129);try{p.setText(PasswordStore.read(prefs));}catch(Exception e){message("保存したパスワードを読み込めません。再入力して保存してください。");}l.addView(u);l.addView(n);l.addView(p);TextView cert=text("証明書: "+(alias.isEmpty()?"未選択":alias),15);l.addView(cert);l.addView(button("証明書をインストール",()->{try{startActivityForResult(KeyChain.createInstallIntent(),102);}catch(Exception e){message("証明書インストール画面を開けません: "+e.getMessage());}}));l.addView(button("インストール済み証明書を選択",()->{try{KeyChain.choosePrivateKeyAlias(this,a->ui.post(()->{if(a!=null){alias=a;cert.setText("証明書: "+a);}else message("証明書が選択されませんでした。端末にクライアント証明書をインストールしてください。");}),null,null,null,-1,alias.isEmpty()?null:alias);}catch(Exception e){message("証明書選択画面を開けません: "+e.getMessage());}}));l.addView(button("証明書を解除",()->{alias="";cert.setText("証明書: 未選択");}));l.addView(text("PKCS#12（.p12/.pfx）を端末にインストール後、インストール済み証明書を選択し、最後に保存を押してください。WebDAVパスワードはAndroid Keystoreの鍵で暗号化して保存します。",14));l.addView(button("保存パスワードをリセット",()->new AlertDialog.Builder(this).setTitle("保存パスワードのリセット")
      .setMessage("保存済みパスワードと暗号化鍵を削除します。次回接続には再入力が必要です。クライアント証明書は削除しません。")
      .setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{try{PasswordStore.reset(prefs);p.setText("");message("パスワードをリセットしました。再入力して保存してください。");}catch(Exception e){message("リセットに失敗しました: "+e.getMessage());}}).show()));
    l.addView(text("自宅Wi-FiでSambaを優先（任意）",19));
    l.addView(text("SMBの共有直下をWebDAVのルートと同じフォルダにしてください。指定Wi-Fiで接続できなければWebDAVを使います。Wi-Fi名は偽装可能です。信頼できるネットワークと読み取り専用Sambaアカウントを使用してください。署名は必須ですが通信が必ず暗号化されるわけではありません。",13));
    CheckBox smbEnabled=new CheckBox(this);smbEnabled.setText("指定Wi-FiではSambaを優先する");smbEnabled.setChecked(prefs.getBoolean("smb_enabled",false));l.addView(smbEnabled);
    EditText smbSsid=new EditText(this),smbHost=new EditText(this),smbShare=new EditText(this),smbUser=new EditText(this),smbPass=new EditText(this);
    smbSsid.setSingleLine();smbSsid.setHint("自宅Wi-Fiの名前（SSID）");smbSsid.setText(prefs.getString("smb_ssid",""));l.addView(smbSsid);
    l.addView(button("現在のWi-Fi名を取得",()->{
      if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED){fillWifiName(smbSsid);return;}
      new AlertDialog.Builder(this).setTitle("Wi-Fi名の取得")
        .setMessage("AndroidでWi-Fi名を取得・照合するには正確な位置情報の権限が必要です。端末の位置情報設定もオンが必要な場合があります。位置情報そのものは取得・送信しません。許可しない場合はWebDAVを使用します。")
        .setNegativeButton("キャンセル",null).setPositiveButton("権限を確認",(d,w)->{pendingWifiName=smbSsid;pendingWifiGeneration=generation;requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},302);}).show();
    }));
    smbHost.setSingleLine();smbHost.setHint("SambaサーバーのプライベートIPv4アドレス");smbHost.setText(prefs.getString("smb_host",""));l.addView(smbHost);
    smbShare.setSingleLine();smbShare.setHint("共有名（例: Comic）");smbShare.setText(prefs.getString("smb_share",""));l.addView(smbShare);
    smbUser.setSingleLine();smbUser.setHint("Sambaユーザー名");smbUser.setText(prefs.getString("smb_user",""));l.addView(smbUser);
    smbPass.setSingleLine();smbPass.setHint("Sambaパスワード");smbPass.setInputType(129);try{smbPass.setText(SmbPasswordStore.read(prefs));}catch(Exception e){message("保存済みSambaパスワードを読み込めません");}l.addView(smbPass);
    l.addView(button("Sambaの保存パスワードをリセット",()->new AlertDialog.Builder(this).setTitle("Sambaパスワードのリセット")
      .setMessage("Sambaの保存済みパスワードと暗号化鍵を削除します。再接続には再入力が必要です。WebDAVの設定や履歴は削除しません。")
      .setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{try{SmbPasswordStore.reset(prefs);smbPass.setText("");message("Sambaパスワードをリセットしました。");}catch(Exception e){message("リセットに失敗しました: "+e.getMessage());}}).show()));
    l.addView(button("非表示タイトル",this::hiddenTitles));
    CheckBox prefetch=new CheckBox(this);prefetch.setText("読み終わる前に次巻をダウンロード（90％付近）");prefetch.setChecked(prefs.getBoolean("prefetch_next",true));l.addView(prefetch);l.addView(button("閲覧履歴を管理",this::history));l.addView(button("アプリの更新を確認",this::checkUpdate));l.addView(button("保存",()->{String url=u.getText().toString().trim();if(!url.startsWith("https://")){message("HTTPSのURLを入力してください");return;}if(!url.endsWith("/"))url+="/";String host=smbHost.getText().toString().trim(),share=smbShare.getText().toString().trim(),ssid=smbSsid.getText().toString().trim(),smbUsername=smbUser.getText().toString().trim();
    if(smbEnabled.isChecked()&&(!SmbAddress.privateIpv4(host)||share.isEmpty()||share.contains("/")||share.contains("\\")||smbUsername.isEmpty()||ssid.isEmpty())){message("SambaにはプライベートIPv4・共有名・ユーザー名・Wi-Fi名を入力してください");return;}
    try{PasswordStore.write(prefs,p.getText().toString());SmbPasswordStore.write(prefs,smbPass.getText().toString());}catch(Exception e){message("パスワードを安全に保存できませんでした。設定をご確認ください。");return;}arrivalsCache=null;prefs.edit().putBoolean("smb_enabled",smbEnabled.isChecked()).putString("smb_host",host).putString("smb_share",share).putString("smb_ssid",ssid).putString("smb_user",smbUsername).putString("url",url).putString("user",n.getText().toString()).putString("alias",alias).putBoolean("prefetch_next",prefetch.isChecked()).apply();home();}));}
  void hiddenTitles(){
    screen="hidden";clear();root.addView(button("← 接続設定",this::settings));root.addView(text("非表示タイトル",23));
    LinearLayout list=scroll();ArrayList<String> items=new ArrayList<>(hiddenFolders());
    items.sort((a,b)->RemoteZip.natural(hiddenPath(a),hiddenPath(b)));
    if(items.isEmpty())list.addView(text("非表示のタイトルはありません",16));
    for(String url:items){
      String path=hiddenPath(url);String name=path;while(name.endsWith("/")&&name.length()>1)name=name.substring(0,name.length()-1);name=name.substring(name.lastIndexOf('/')+1);
      TextView row=entry(clean(name),path);list.addView(row);
      final String title=name;
      row.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("非表示を取り消しますか？").setMessage(clean(title)+"\n"+path+"\n\n親フォルダにも非表示設定がある場合は、そちらも解除する必要があります。")
        .setNegativeButton("キャンセル",null).setPositiveButton("非表示を取り消す",(d,w)->{setHiddenFolder(url,false);hiddenTitles();}).show());
    }
  }
  String hiddenPath(String url){try{String path=new java.net.URI(NestedBook.safe(url)).getPath();return path==null?url:path;}catch(Exception e){return url;}}
  void history(){
    screen="history";clear();root.addView(button("← ホーム",this::home));root.addView(text("閲覧履歴（タップして削除）",23));
    LinearLayout list=scroll();JSONObject all=records();ArrayList<JSONObject> entries=new ArrayList<>();Iterator<String> it=all.keys();
    while(it.hasNext()){JSONObject o=all.optJSONObject(it.next());if(o!=null&&!hiddenRecord(o))entries.add(o);}
    entries.sort((a,b)->Long.compare(b.optLong("time"),a.optLong("time")));
    if(entries.isEmpty())list.addView(text("履歴はありません",16));
    for(JSONObject o:entries){String url=o.optString("url"),name=o.optString("name");
      TextView row=entry(clean(name),clean(o.optString("folderName"))+"  "+(o.optBoolean("finished")?"既読":"途中"));list.addView(row);
      row.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("履歴を削除しますか？")
        .setMessage(clean(name)+"\n\n読書位置・既読状態・見開き設定と、端末に保存したこの本のZIPを削除します。保存中の場合は中止します。WebDAV上の元ファイルは削除しません。")
        .setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{
          if(!DownloadCache.delete(this,url)){message("端末内ファイルを削除できませんでした。履歴は残しています。");return;}
          all.remove(url);prefs.edit().putString("records",all.toString()).apply();history();
        }).show());
    }
  }
  void checkUpdate(){
    message("GitHubの更新を確認しています…");
    async(Updater::latest,release->{
      android.content.pm.PackageInfo installed=getPackageManager().getPackageInfo(getPackageName(),0);
      if(release.code<=Updater.version(installed)){new AlertDialog.Builder(this).setTitle("更新確認").setMessage("最新版を使用しています。\n現在: "+installed.versionName).setPositiveButton("OK",null).show();return;}
      new AlertDialog.Builder(this).setTitle("新しいバージョンがあります")
        .setMessage(release.name+" のGitHub Releaseページをブラウザで開きます。\nアプリ内から直接インストールは行いません。Play プロテクトで有害と警告された場合はインストールせず、警告内容を確認し、Googleによる再審査を検討してください。")
        .setNegativeButton("キャンセル",null).setPositiveButton("Releaseページを開く",(d,w)->{
          try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(release.page)));}
          catch(Exception e){message("ブラウザを開けません: "+e.getMessage());}
        }).show();
    });
  }
  Dav client() throws Exception{return new Dav(this,prefs.getString("url",""),prefs.getString("user",""),PasswordStore.read(prefs),prefs.getString("alias",""));}
  void browse(String url,String name){
    if(url.isEmpty()){settings();return;}
    screen="browse";DownloadCache.pin(null);DownloadCache.cleanup(this);reading=false;closeCurrentBook();clear();release();
    root.addView(button("← ホーム",this::home));root.addView(text(clean(name),23));
    if(manualCoverTarget!=null){
      LinearLayout selection=new LinearLayout(this);selection.addView(text("表紙選択中: "+clean(manualCoverName)+"。漫画を開いてページを選んでください",13),new LinearLayout.LayoutParams(0,-2,1));
      selection.addView(button("選択をやめる",()->{manualCoverTarget=null;manualCoverName=null;browse(url,name);}));root.addView(selection);
    }
    LinearLayout searchBar=new LinearLayout(this);
    EditText query=new EditText(this);query.setSingleLine();query.setHint("フォルダ名を検索");
    searchBar.addView(query,new LinearLayout.LayoutParams(0,-2,1));
    Button order=new Button(this);order.setAllCaps(false);searchBar.addView(order);root.addView(searchBar);
    LinearLayout coverBar=new LinearLayout(this);
    Button coverToggle=button("",()->{});coverBar.addView(coverToggle);
    coverBar.addView(button("表紙を再取得",()->new AlertDialog.Builder(this).setTitle("表紙を再取得")
      .setMessage("保存した表紙画像を削除し、表示中のフォルダから順次取得し直します。手動で選んだページの設定、漫画ファイルや履歴は削除しません。")
      .setNegativeButton("キャンセル",null).setPositiveButton("再取得",(d,w)->{coverSerial++;final int screenGeneration=generation;coverWorker.executeCache(()->{FolderCovers.clear(getApplicationContext());ui.post(()->{if(screenGeneration==generation && "browse".equals(screen))browse(url,name);});});}).show()));
    root.addView(coverBar);
    LinearLayout list=scroll();ScrollView coverScroll=(ScrollView)list.getParent();
    TextView status=text("読み込み中…",16);list.addView(status);
    ArrayList<Runnable> visibleCoverChecks=new ArrayList<>();
    Runnable checkCovers=()->{for(Runnable check:visibleCoverChecks)check.run();};
    coverToggle.setText(prefs.getBoolean("covers_enabled",false)?"表紙: オン":"表紙: オフ");
    coverToggle.setOnClickListener(v->{
      if(prefs.getBoolean("covers_enabled",false)){prefs.edit().putBoolean("covers_enabled",false).apply();browse(url,name);}
      else new AlertDialog.Builder(this).setTitle("WebDAVから表紙を取得")
        .setMessage("各フォルダを探索し、最初のZIP/CBZ/RAR/CBRから先頭ページを取得して、縮小し端末に保存します。直下になければ子フォルダを名前順に探します。漫画ファイルの先頭ページが表紙でない場合は別の画像になります。オンにしますか？")
        .setNegativeButton("キャンセル",null).setPositiveButton("オンにする",(d,w)->{prefs.edit().putBoolean("covers_enabled",true).apply();browse(url,name);}).show();
    });
    coverScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{
      checkCovers.run();
      // Recheck after scrolling/layout settles, including when a cancelled request finishes late.
      coverScroll.removeCallbacks(checkCovers);coverScroll.postDelayed(checkCovers,150);
    });
    coverScroll.getViewTreeObserver().addOnGlobalLayoutListener(()->checkCovers.run());
    ArrayList<Dav.Item> loaded=new ArrayList<>();boolean[] ready={false};
    Runnable refresh=()->{
      boolean descending=prefs.getBoolean("browse_descending",false);
      order.setText(descending?"作品名順 ↓":"作品名順 ↑");
      if(!ready[0])return;
      coverSerial++;final int serial=coverSerial;visibleCoverChecks.clear();list.removeAllViews();
      ArrayList<Dav.Item> visible=new ArrayList<>();
      Set<String> hidden=hiddenFolders();HiddenFolders.Index hiddenIndex=new HiddenFolders.Index(hidden);
      for(Dav.Item item:loaded)if(!hiddenIndex.hidden(item.url)&&BrowseSearch.matches(item.name,item.dir,query.getText().toString()))visible.add(item);
      TitleSort titleSort=new TitleSort();
      visible.sort((a,b)->{if(a.dir!=b.dir)return a.dir?-1:1;return descending?titleSort.compare(b.name,a.name):titleSort.compare(a.name,b.name);});
      for(Dav.Item item:visible){
        JSONObject rec=record(item.url);String sub=item.dir?"フォルダ":rec.length()==0?"未読":rec.optBoolean("finished")?"既読":"途中  "+(rec.optInt("page")+1)+" / "+rec.optInt("total");
        TextView v=entry(clean(item.name),author(item.name)+"  "+sub);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        if(item.dir){
          ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
          int density=(int)(getResources().getDisplayMetrics().density*100);
          FrameLayout coverFrame=new FrameLayout(this);
          coverFrame.addView(cover,new FrameLayout.LayoutParams(-1,-1));
          TextView coverStatus=new TextView(this);coverStatus.setTextSize(10);coverStatus.setTextColor(0xff555555);coverStatus.setGravity(Gravity.CENTER);
          coverFrame.addView(coverStatus,new FrameLayout.LayoutParams(-1,-1));
          row.addView(coverFrame,new LinearLayout.LayoutParams(72*density/100,96*density/100));
          boolean[] pending={false},failed={false};
          java.util.concurrent.atomic.AtomicBoolean[] active={null};
          Runnable check=()->{
            if(serial!=coverSerial)return;
            boolean rowNear=prefs.getBoolean("covers_enabled",false)&&row.isShown()&&CoverWindow.contains(row.getTop(),row.getBottom(),coverScroll.getScrollY(),coverScroll.getHeight());
            if(!rowNear){if(active[0]!=null)active[0].set(true);cover.setImageDrawable(null);return;}
            if(pending[0]||failed[0]||cover.getDrawable()!=null)return;
            pending[0]=true;coverStatus.setText("待機中…");
            java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();active[0]=cancelled;
            coverWorker.submit(()->cancelled.get()||serial!=coverSerial||Thread.currentThread().isInterrupted(),
              ()->FolderCovers.cached(getApplicationContext(),item.url),
              ()->FolderCovers.load(getApplicationContext(),item.url,()->client(),()->cancelled.get()||serial!=coverSerial||Thread.currentThread().isInterrupted()),
              ()->ui.post(()->{if(serial==coverSerial&&!cancelled.get())coverStatus.setText("取得中…");}),
              (result,error)->{
              final String failure=error==null?null:error instanceof NestedBook.Limit?"探索上限":error.getMessage()==null||error.getMessage().isEmpty()?"通信エラー":error.getMessage();
              ui.post(()->{
                pending[0]=false;active[0]=null;
                if(serial!=coverSerial){if(result!=null)result.recycle();return;}
                boolean nearNow=row.isShown()&&prefs.getBoolean("covers_enabled",false)&&CoverWindow.contains(row.getTop(),row.getBottom(),coverScroll.getScrollY(),coverScroll.getHeight());
                if(cancelled.get()||!nearNow){if(result!=null)result.recycle();coverStatus.setText("");if(nearNow)list.post(checkCovers);return;}
                if(result==null){failed[0]=true;coverStatus.setText((failure==null?"画像なし":failure)+"\nタップで再試行");return;}
                coverStatus.setText("");cover.setImageBitmap(result);cover.invalidate();coverFrame.invalidate();
              });
            });
          };
          boolean suppressCover=hiddenIndex.hasDescendant(item.url);
          if(suppressCover)coverStatus.setText("表紙非表示");else visibleCoverChecks.add(check);
          coverFrame.setOnClickListener(w->{if(suppressCover){folders.push(new String[]{url,name});browse(item.url,item.name);}else if(failed[0]){failed[0]=false;check.run();}else if(cover.getDrawable()!=null){folders.push(new String[]{url,name});browse(item.url,item.name);}});
        }
        row.addView(v,new LinearLayout.LayoutParams(0,-2,1));list.addView(row);
        row.setOnClickListener(w->{if(item.dir){folders.push(new String[]{url,name});browse(item.url,item.name);}else openBook(item.url,item.name,url,name);});
        if(item.dir)row.setOnLongClickListener(w->{
          boolean ownHidden=HiddenFolders.explicitlyHidden(hiddenFolders(),item.url);
          new AlertDialog.Builder(this).setTitle(clean(item.name)).setItems(new String[]{"漫画のページを表紙に選ぶ","手動の表紙設定を解除",ownHidden?"このフォルダの非表示設定を解除":"このフォルダを非表示にする"},(dialog,choice)->{
            if(choice==0){manualCoverTarget=item.url;manualCoverName=item.name;folders.push(new String[]{url,name});browse(item.url,item.name);}
            else if(choice==1){async(()->{FolderCovers.clearManual(getApplicationContext(),item.url);return true;},ignored->{message("自動の表紙に戻しました");browse(url,name);});}
            else{setHiddenFolder(item.url,!ownHidden);message(ownHidden?"非表示設定を解除しました":"非表示にしました");browse(url,name);}
          }).setNegativeButton("キャンセル",null).show();return true;
        });
      }
      if(visible.isEmpty())list.addView(text(loaded.isEmpty()?"フォルダまたは対応ファイルがありません":"一致する項目がありません",16));
      list.post(checkCovers);
    };
    order.setOnClickListener(v->{prefs.edit().putBoolean("browse_descending",!prefs.getBoolean("browse_descending",false)).apply();refresh.run();});
    query.addTextChangedListener(new android.text.TextWatcher(){
      public void beforeTextChanged(CharSequence s,int start,int count,int after){}
      public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}
      public void afterTextChanged(android.text.Editable e){}
    });
    refresh.run();
    async(()->{try(Dav d=client()){d.discoverExistingUrls();return d.list(url);}},items->{loaded.addAll(items);ready[0]=true;refresh.run();});
  }
  void openBook(String url,String name,String folder,String fname){
    closeCurrentBook();screen="reader";DownloadCache.pin(null);DownloadCache.touch(this,url);DownloadCache.pin(url);
    bookUrl=url;bookName=name;folderUrl=folder;folderName=fname;reading=true;controlsVisible=false;prefetchRequested=false;prefetchStatus="";previous.clear();cuts.clear();
    JSONObject rec=record(url);leftToRightSpread=rec.optBoolean("leftToRightSpread",false);page=rec.optInt("page",0);
    JSONArray arr=rec.optJSONArray("cuts");if(arr!=null)for(int i=0;i<arr.length();i++)cuts.add(arr.optInt(i));
    clear();loadingBars();root.addView(button("← ホーム",this::home));TextView loading=text("本を開いています…",20);root.addView(loading);downloadState="保存準備中";
    async(()->{
      Dav d=client();try{DownloadCache.start(this,d,url,state->ui.post(()->{if(reading && url.equals(bookUrl)){downloadState=state;if(downloadLabel!=null)downloadLabel.setText("端末への保存: "+state);}}));
      return RemoteBooks.open(d,url,name);}catch(Exception|Error e){d.close();throw e;}
    },z->{
      zip=z;if(rec.optInt("orientationCount",-1)==z.pages.size()&&rec.optLong("orientationLength",-1)==z.archiveLength)z.orientations.putAll(OrientationCodec.decode(z.pages.size(),rec.optString("orientations","")));
      page=Math.max(0,Math.min(page,zip.pages.size()-1));render();
    },error->{
      if(error instanceof RemoteRar.UnsupportedRar)DownloadCache.delete(this,url);
      DownloadCache.pin(null);loading.setText("本を開けませんでした: "+(error.getMessage()==null?"不明なエラー":error.getMessage()));
      root.addView(button("再試行",()->openBook(url,name,folder,fname)));
      root.addView(button("フォルダに戻る",()->{if(folder==null||folder.isEmpty())home();else browse(folder,fname);}));
    });
  }
  void save(){if(zip==null)return;JSONObject o=record(bookUrl);try{o.put("url",bookUrl);o.put("name",bookName);o.put("folder",folderUrl);o.put("folderName",folderName);o.put("page",page);o.put("total",zip.pages.size());o.put("time",System.currentTimeMillis());if(!o.has("finished"))o.put("finished",false);o.put("leftToRightSpread",leftToRightSpread);o.put("orientationCount",zip.pages.size());o.put("orientationLength",zip.archiveLength);o.put("orientations",OrientationCodec.encode(zip.pages.size(),zip.orientations));JSONArray a=new JSONArray();for(int cut:cuts)a.put(cut);o.put("cuts",a);put(bookUrl,o);}catch(Exception ignored){}}
  void release(){if(right!=null){right.recycle();right=null;}if(left!=null){left.recycle();left=null;}}
  static final class Spread {Bitmap a,b;int next;Spread(Bitmap x,Bitmap y,int n){a=x;b=y;next=n;}}
  void scheduleGallery(RemoteZip current,int index,LinearLayout strip,HorizontalScrollView scroller,TextView caption){
    clearThumbnail();strip.removeAllViews();
    int serial=previewSerial, currentGeneration=generation;
    caption.setText((index+1)+" / "+current.pages.size());
    int first=Math.max(0,index-10), last=Math.min(current.pages.size()-1,index+10);
    int density=(int)(getResources().getDisplayMetrics().density*100);
    int cardWidth=Math.max(1,80*density/100), imageHeight=Math.max(1,105*density/100);
    ArrayList<ImageView> views=new ArrayList<>(Collections.nCopies(last-first+1, null));
    // Manga order: earlier pages on the right, later pages on the left.
    for(int i=last;i>=first;i--){
      final int target=i;
      LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setGravity(Gravity.CENTER);
      card.setBackgroundColor(i==index?0xffc8c8c8:0xffeeeeee);
      ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
      image.setBackgroundColor(0xffdddddd);
      card.addView(image,new LinearLayout.LayoutParams(cardWidth,imageHeight));
      TextView number=text(String.valueOf(i+1),12);number.setGravity(Gravity.CENTER);number.setPadding(2,2,2,2);card.addView(number);
      LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(cardWidth,-2);cardParams.setMargins(2,2,2,2);strip.addView(card,cardParams);
      views.set(i-first,image);thumbnailViews.add(image);
      card.setOnClickListener(v->jumpToPage(target));
    }
    scroller.post(()->{if(serial==previewSerial && generation==currentGeneration){int center=(last-index)*(cardWidth+4)+cardWidth/2;scroller.scrollTo(Math.max(0,center-scroller.getWidth()/2),0);}});
    // Debounce changes, load nearest pages first, and never block the main page worker.
    previewPending=()->{
      ArrayList<Integer> order=new ArrayList<>();
      for(int n=0;n<=10;n++){if(index+n<=last)order.add(index+n);if(n>0 && index-n>=first)order.add(index-n);}
      for(int target:order){
        ImageView image=views.get(target-first);
        thumbWorker.execute(()->{
          if(serial!=previewSerial || currentGeneration!=generation)return;
          try{
            Bitmap bitmap=current.bitmap(target,140,180);
            ui.post(()->{
              if(serial!=previewSerial || currentGeneration!=generation || !controlsVisible){bitmap.recycle();return;}
              image.setImageBitmap(bitmap);thumbnailBitmaps.add(bitmap);
            });
          }catch(Exception e){ui.post(()->{if(serial==previewSerial && currentGeneration==generation)image.setBackgroundColor(0xffaaaaaa);});}
        });
      }
    };
    ui.postDelayed(previewPending,250);
  }
  void render(){
    if(zip==null)return;
    clear();release();
    FrameLayout frame=new FrameLayout(this);
    root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout images=new LinearLayout(this);images.setGravity(Gravity.CENTER);
    frame.addView(images,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout controls=new LinearLayout(this);controls.setOrientation(1);controls.setBackgroundColor(0xeef5f5f5);
    controls.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),0);return insets;});
    LinearLayout bar=new LinearLayout(this);
    bar.addView(button("← ホーム",this::home));
    bar.addView(button("見開き調整",()->new AlertDialog.Builder(this).setTitle("見開き調整").setItems(new String[]{"前のページから組み合わせをずらす","この本の調整をすべて解除"},(d,n)->{if(n==0)adjustFromPrevious();else {cuts.clear();previous.clear();jumpToPage(page);}}).show()));
    bar.addView(button("見開き左右",()->new AlertDialog.Builder(this).setTitle("見開きの表示順")
      .setSingleChoiceItems(new String[]{"右開き（先のページが右）","左開き（先のページが左）"},leftToRightSpread?1:0,(dialog,choice)->{
        leftToRightSpread=choice==1;save();dialog.dismiss();render();
      }).setNegativeButton("キャンセル",null).show()));
    controls.addView(bar);
    Button chooseCover=manualCoverTarget==null?null:button("このページを「"+clean(manualCoverName)+"」の表紙にする",()->chooseManualCover());
    if(chooseCover!=null){chooseCover.setEnabled(false);controls.addView(chooseCover);}
    controls.addView(button("文字・線の濃さ",()->new AlertDialog.Builder(this).setTitle("文字・線の濃さ（全ての本に適用）")
      .setSingleChoiceItems(new String[]{"標準（処理なし）","弱","強"},prefs.getInt("ink_strength",0),(dialog,choice)->{
        prefs.edit().putInt("ink_strength",choice).apply();
        if(images.getChildCount()>0 && images.getChildAt(0) instanceof SpreadView)((SpreadView)images.getChildAt(0)).setInkStrength(choice);
        dialog.dismiss();
      }).setNegativeButton("キャンセル",null).show()));
    downloadLabel=text("端末への保存: "+downloadState,12);controls.addView(downloadLabel);prefetchLabel=text(prefetchStatus,12);if(!prefetchStatus.isEmpty())controls.addView(prefetchLabel);
    TextView status=text("読み込み中…",14);controls.addView(status);
    frame.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
    LinearLayout seekPanel=new LinearLayout(this);seekPanel.setOrientation(1);seekPanel.setGravity(Gravity.CENTER);seekPanel.setBackgroundColor(0xeef5f5f5);
    seekPanel.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),0,insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
    TextView caption=text("",14);caption.setGravity(Gravity.CENTER);caption.setPadding(4,2,4,2);seekPanel.addView(caption);
    HorizontalScrollView scroller=new HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(true);
    LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);scroller.addView(strip);seekPanel.addView(scroller,new LinearLayout.LayoutParams(-1,-2));
    SeekBar seek=new SeekBar(this);seek.setMax(zip.pages.size()-1);seek.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);seek.setProgress(seek.getMax()-page);seekPanel.addView(seek,new LinearLayout.LayoutParams(-1,-2));
    frame.addView(seekPanel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
    controls.setVisibility(controlsVisible?View.VISIBLE:View.GONE);seekPanel.setVisibility(controlsVisible?View.VISIBLE:View.GONE);
    final int requested=page;final RemoteZip current=zip;final TreeSet<Integer> anchors=new TreeSet<>(cuts);
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar v,int progress,boolean fromUser){if(fromUser)scheduleGallery(current,v.getMax()-progress,strip,scroller,caption);}
      public void onStartTrackingTouch(SeekBar v){}
      public void onStopTrackingTouch(SeekBar v){int target=v.getMax()-v.getProgress();if(target!=page)jumpToPage(target);}
    });
    if(controlsVisible)scheduleGallery(current,page,strip,scroller,caption);
    int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;
    async(()->{
      boolean pair=SpreadPlanner.paired(requested,current.pages.size(),anchors,current::isWide);
      int pageWidth=pair?Math.max(1,width/2):width;
      Bitmap a=current.bitmap(requested,pageWidth,height);Bitmap b=null;int n=requested+1;
      if(pair){try{b=current.bitmap(n,pageWidth,height);n++;}catch(Exception e){a.recycle();throw e;}}
      return new Spread(a,b,n);
    },s->{
      release();right=s.a;left=s.b;next=s.next;if(chooseCover!=null)chooseCover.setEnabled(!manualCoverSaving);status.setText((page+1)+(next>page+1?"–"+next:"")+" / "+zip.pages.size()+"  接続: "+current.dav.connectionName());
      Bitmap pageOnLeft=left==null?null:(leftToRightSpread?right:left);
      Bitmap pageOnRight=left==null?right:(leftToRightSpread?left:right);
      SpreadView spreadView=new SpreadView(this,pageOnLeft,pageOnRight);
      spreadView.setInkStrength(prefs.getInt("ink_strength",0));
      images.addView(spreadView,new LinearLayout.LayoutParams(-1,-1));
      images.setOnTouchListener(new View.OnTouchListener(){float x;public boolean onTouch(View v,android.view.MotionEvent e){
        if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){x=e.getX();return true;}
        if(e.getAction()==android.view.MotionEvent.ACTION_UP){float delta=e.getX()-x;if(Math.abs(delta)>70){if(delta>0)forward();else backPage();}
          else if(e.getX()<v.getWidth()/3f)forward();else if(e.getX()>v.getWidth()*2f/3f)backPage();
          else {controlsVisible=!controlsVisible;systemBars(!controlsVisible);controls.setVisibility(controlsVisible?View.VISIBLE:View.GONE);seekPanel.setVisibility(controlsVisible?View.VISIBLE:View.GONE);if(controlsVisible)scheduleGallery(current,seek.getMax()-seek.getProgress(),strip,scroller,caption);else {clearThumbnail();strip.removeAllViews();}root.requestApplyInsets();controls.requestApplyInsets();seekPanel.requestApplyInsets();}
          return true;}return true;}});
      save();maybePrefetch();
    },error->{
      controlsVisible=true;systemBars(false);controls.setVisibility(View.VISIBLE);seekPanel.setVisibility(View.VISIBLE);
      status.setText("ページ "+(requested+1)+" の読み込みに失敗: "+(error.getMessage()==null?"不明なエラー":error.getMessage()));
      LinearLayout recovery=new LinearLayout(this);
      recovery.addView(button("再試行",()->render()));
      if(requested+1<current.pages.size())recovery.addView(button("次のページへ",()->{previous.put(requested+1,requested);page=requested+1;next=0;render();}));
      else recovery.addView(button("読了画面へ",this::finishBook));
      controls.addView(recovery);root.requestApplyInsets();controls.requestApplyInsets();seekPanel.requestApplyInsets();
    });
  }
  void chooseManualCover(){
    if(zip==null||manualCoverTarget==null||next<=page||manualCoverSaving)return;
    int first=page;boolean pair=next>page+1;
    String[] options=pair?new String[]{(first+1)+"ページ目（"+(leftToRightSpread?"左":"右")+"）",(first+2)+"ページ目（"+(leftToRightSpread?"右":"左")+"）"}:new String[]{(first+1)+"ページ目"};
    String target=manualCoverTarget,targetName=manualCoverName,source=bookUrl,sourceName=bookName;RemoteZip current=zip;
    new AlertDialog.Builder(this).setTitle("「"+clean(targetName)+"」の表紙にするページ")
      .setItems(options,(dialog,choice)->showCoverFrame(target,targetName,source,sourceName,current,first+choice,choice==0?right:left))
      .setNegativeButton("キャンセル",null).show();
  }
  void showCoverFrame(String target,String targetName,String source,String sourceName,RemoteZip current,int selected,Bitmap original){
    if(original==null||original.isRecycled()||zip!=current){message("ページをもう一度表示してください");return;}
    Bitmap preview;
    try{
      float scale=Math.min(1f,Math.min(1100f/original.getWidth(),800f/original.getHeight()));
      int w=Math.max(1,Math.round(original.getWidth()*scale)),h=Math.max(1,Math.round(original.getHeight()*scale));
      preview=Bitmap.createBitmap(w,h,Bitmap.Config.RGB_565);
      android.graphics.Canvas canvas=new android.graphics.Canvas(preview);canvas.drawColor(android.graphics.Color.WHITE);
      canvas.drawBitmap(original,null,new android.graphics.Rect(0,0,w,h),new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
    }catch(OutOfMemoryError e){message("表紙のプレビューを作るメモリーが不足しています");return;}
    CoverCropView frame=new CoverCropView(this,preview);
    LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
    content.addView(text("白い枠を左右にドラッグしてください。上下はページいっぱいで固定です。",14));
    int height=Math.max(1,(int)Math.min(getResources().getDisplayMetrics().heightPixels*0.52f,400*getResources().getDisplayMetrics().density));
    content.addView(frame,new LinearLayout.LayoutParams(-1,height));
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("表紙に表示する範囲").setView(content)
      .setNegativeButton("キャンセル",null).setPositiveButton("この範囲にする",(d,w)->saveManualCover(target,targetName,source,sourceName,current,selected,frame.position())).create();
    dialog.setOnDismissListener(d->frame.release());dialog.show();
  }
  void saveManualCover(String target,String targetName,String source,String sourceName,RemoteZip current,int selected,int position){
    if(manualCoverSaving)return;manualCoverSaving=true;
    worker.execute(()->{
      Exception failure=null;try{FolderCovers.setManual(getApplicationContext(),target,source,sourceName,selected,position,current);}catch(Exception e){failure=e;}
      Exception error=failure;ui.post(()->{
        manualCoverSaving=false;if(isFinishing()||isDestroyed())return;
        if(error==null){
          boolean sameTarget=target.equals(manualCoverTarget);if(sameTarget){manualCoverTarget=null;manualCoverName=null;}
          message("「"+clean(targetName)+"」の表紙を設定しました");
          if(sameTarget&&"reader".equals(screen)&&zip==current)render();
        }else message("表紙を保存できません: "+(error.getMessage()==null?"不明なエラー":error.getMessage()));
      });
    });
  }
  void adjustFromPrevious(){
    if(zip==null)return;
    if(page<=0){message("前のページがないため調整できません");return;}
    final RemoteZip current=zip;final int target=page;final int boundary=target-1;
    final TreeSet<Integer> updated=new TreeSet<>(cuts);
    if(!updated.add(boundary))updated.remove(boundary);
    async(()->{
      if(current.isWide(boundary)||current.isWide(target))return -1;
      return SpreadPlanner.containing(target,current.pages.size(),updated,current::isWide);
    },start->{
      if(start!=boundary){message("前のページと見開きにできません。横長ページや他の調整をご確認ください。");return;}
      cuts.clear();cuts.addAll(updated);previous.clear();page=start;next=0;save();render();
    });
  }
  void updatePrefetchStatus(String expectedUrl,String status){
    ui.post(()->{
      if(!reading || !expectedUrl.equals(bookUrl))return;
      prefetchStatus=status;
      if(prefetchLabel!=null){prefetchLabel.setText(status);if(prefetchLabel.getParent()==null){android.view.ViewParent parent=downloadLabel!=null?downloadLabel.getParent():null;if(parent instanceof LinearLayout)((LinearLayout)parent).addView(prefetchLabel);}}
    });
  }
  void maybePrefetch(){
    if(zip==null||prefetchRequested||!prefs.getBoolean("prefetch_next",true)||!NextBook.reachedThreshold(next,zip.pages.size()))return;
    prefetchRequested=true;
    final String currentUrl=bookUrl, parent=folderUrl;
    updatePrefetchStatus(currentUrl,"次巻を確認中…");
    prefetchWorker.execute(()->{
      try{
        Dav d=client();Dav.Item item;
        try{item=NextBook.find(d.list(parent),currentUrl);}catch(Exception e){d.close();throw e;}
        if(item==null){d.close();updatePrefetchStatus(currentUrl,"次巻はありません");return;}
        if(!ui.post(()->{try{
          // Do not start a stale prefetch after the reader has moved to another book.
          if(!reading || !currentUrl.equals(bookUrl))return;
          updatePrefetchStatus(currentUrl,"次巻を保存中: "+clean(item.name));
          DownloadCache.start(getApplicationContext(),d,item.url,state->updatePrefetchStatus(currentUrl,"次巻: "+clean(item.name)+" — "+state));
        }finally{d.close();}}))d.close();
      }catch(Exception e){updatePrefetchStatus(currentUrl,"次巻の先読みを開始できませんでした");}
    });
  }
  void jumpToPage(int target){
    if(zip==null)return;
    RemoteZip current=zip;TreeSet<Integer> anchors=new TreeSet<>(cuts);
    controlsVisible=false;clear();release();loadingBars();
    root.addView(button("← ホーム",this::home));
    root.addView(text("見開きの位置を確認しています…",20));
    final int jobGeneration=generation;
    async(()->SpreadPlanner.containing(target,current.pages.size(),anchors,index->{
      if(jobGeneration!=generation)throw new java.io.IOException("中断しました");
      return current.isWide(index);
    }),start->{previous.clear();page=start;next=0;render();},error->{controlsVisible=true;render();message("ページ移動できません: "+error.getMessage());});
  }
  void forward(){if(zip==null||next<=page)return;if(next>=zip.pages.size()){finishBook();return;}previous.put(next,page);page=next;next=0;controlsVisible=false;render();}
  void finishBook(){
    final String finishedUrl=bookUrl, parent=folderUrl; final JSONObject o=record(bookUrl);
    try{o.put("finished",true);o.put("time",System.currentTimeMillis());put(bookUrl,o);}catch(Exception ignored){}
    DownloadCache.delete(this,finishedUrl);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("読み終わりました").setMessage("次の巻を確認しています…").setCancelable(false).setPositiveButton("次の巻へ",null).setNegativeButton("ホームへ戻る",(d,w)->home()).create();
    dialog.setOnShowListener(v->{Button b=dialog.getButton(AlertDialog.BUTTON_POSITIVE);b.setEnabled(false);async(()->{try(Dav d=client()){return NextBook.find(d.list(parent),finishedUrl);}},item->{if(!dialog.isShowing())return;if(item==null){dialog.setMessage("次の巻はありません。ホームへ戻ってください。");}else{dialog.setMessage("次の巻: "+clean(item.name));b.setEnabled(true);b.setOnClickListener(w->{dialog.dismiss();openBook(item.url,item.name,parent,folderName);});}});});
    dialog.show();
  }
  void backPage(){if(zip==null||page<=0)return;Integer p=previous.get(page);if(p==null){jumpToPage(page-1);return;}page=p;next=0;controlsVisible=false;render();}
  @Override public void onBackPressed(){if("home".equals(screen)){super.onBackPressed();return;}if("hidden".equals(screen)){settings();return;}if("reader".equals(screen) && folderUrl!=null && !folderUrl.isEmpty()){browse(folderUrl,folderName);return;}if("browse".equals(screen)&&!folders.isEmpty()){String[] parent=folders.pop();browse(parent[0],parent[1]);return;}home();}
  @Override protected void onDestroy(){generation++;coverSerial++;closeCurrentBook();worker.shutdownNow();browseWorker.shutdownNow();arrivalsWorker.shutdownNow();coverWorker.shutdownNow();thumbWorker.shutdownNow();prefetchWorker.shutdownNow();bookCloser.shutdown();clearThumbnail();release();super.onDestroy();}
}
