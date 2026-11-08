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
  android.content.SharedPreferences prefs; ExecutorService worker=Executors.newSingleThreadExecutor(); Handler ui=new Handler(Looper.getMainLooper()); volatile int generation=0; LinearLayout root; Dav dav; RemoteZip zip; String bookUrl,bookName,folderUrl,folderName; int page=0,next=0; boolean reading=false; HashMap<Integer,Integer> previous=new HashMap<>(); TreeSet<Integer> cuts=new TreeSet<>(); Bitmap right,left; String alias=""; String downloadState=""; TextView downloadLabel; boolean controlsVisible=false; final ExecutorService thumbWorker=Executors.newSingleThreadExecutor(); final ArrayList<ImageView> thumbnailViews=new ArrayList<>(); final ArrayList<Bitmap> thumbnailBitmaps=new ArrayList<>(); volatile int previewSerial=0; Runnable previewPending;
  interface Task<T>{T run() throws Exception;} interface Done<T>{void accept(T x) throws Exception;}
  public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("reader",MODE_PRIVATE);alias=prefs.getString("alias","");DownloadCache.cleanup(this);home();}
  void clear(){
    generation++;
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
  <T> void async(Task<T> task,Done<T> done){int id=generation;worker.execute(()->{try{T result=task.run();ui.post(()->{if(id!=generation){if(result instanceof Spread){Spread discarded=(Spread)result;discarded.a.recycle();if(discarded.b!=null)discarded.b.recycle();}return;}try{done.accept(result);}catch(Exception e){message(e.toString());}});}catch(Exception e){ui.post(()->{if(id==generation)message("エラー: "+e.getMessage());});}});}
  JSONObject records(){try{return new JSONObject(prefs.getString("records","{}"));}catch(Exception e){return new JSONObject();}}
  void put(String key,JSONObject value){try{JSONObject all=records();all.put(key,value);prefs.edit().putString("records",all.toString()).apply();}catch(Exception e){message(e.toString());}}
  JSONObject record(String key){JSONObject j=records().optJSONObject(key);return j==null?new JSONObject():j;}
  String clean(String s){return s.replaceFirst("^\\s*\\([^)]*\\)\\s*","").replaceFirst("^\\s*\\[[^]]*\\]\\s*","").replaceFirst("(?i)\\.(zip|cbz)$","").trim();}
  String author(String s){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\[([^]]+)\\]").matcher(s);return m.find()?m.group(1):"";}
  void home(){reading=false;zip=null;release();clear();root.addView(text("漫画リーダー",25));LinearLayout bar=new LinearLayout(this);bar.addView(button("フォルダ",()->browse(prefs.getString("url",""),"フォルダ")));bar.addView(button("接続設定",this::settings));root.addView(bar);LinearLayout list=scroll();JSONObject all=records();ArrayList<JSONObject> recent=new ArrayList<>();Iterator<String> keys=all.keys();while(keys.hasNext()){JSONObject o=all.optJSONObject(keys.next());if(o!=null&&!o.optBoolean("finished"))recent.add(o);}recent.sort((a,b)->Long.compare(b.optLong("time"),a.optLong("time")));
    list.addView(text("最近読んだ本",21));if(recent.isEmpty())list.addView(text("読みかけの本はありません",14));for(JSONObject o:recent){String name=o.optString("name"),url=o.optString("url"),fu=o.optString("folder"),fn=o.optString("folderName");TextView v=entry(clean(name),author(name)+"  途中 "+(o.optInt("page")+1)+" / "+o.optInt("total"));list.addView(v);v.setOnClickListener(w->openBook(url,name,fu,fn));}
    list.addView(text("読みかけのシリーズ",21));HashSet<String> seen=new HashSet<>();ArrayList<JSONObject> series=new ArrayList<>();for(JSONObject o:recent)if(seen.add(o.optString("folder")))series.add(o);
    String sort=prefs.getString("sort","最近読んだ順");if(sort.equals("タイトル順"))series.sort((a,b)->RemoteZip.natural(clean(a.optString("folderName")),clean(b.optString("folderName"))));else if(sort.equals("著者順"))series.sort((a,b)->RemoteZip.natural(author(a.optString("folderName")),author(b.optString("folderName"))));
    list.addView(button("並び替え: "+sort,()->new AlertDialog.Builder(this).setTitle("並び替え").setItems(new String[]{"最近読んだ順","タイトル順","著者順"},(d,n)->{prefs.edit().putString("sort",new String[]{"最近読んだ順","タイトル順","著者順"}[n]).apply();home();}).show()));
    for(JSONObject o:series){String fn=o.optString("folderName"),fu=o.optString("folder"),bn=o.optString("name");TextView v=entry(clean(fn),author(fn)+"\n"+clean(bn)+"  途中 "+(o.optInt("page")+1)+" / "+o.optInt("total"));list.addView(v);v.setOnClickListener(w->browse(fu,fn));}
  }
  void settings(){clear();root.addView(button("← ホーム",this::home));root.addView(text("WebDAV接続設定",23));LinearLayout l=scroll();EditText u=new EditText(this),n=new EditText(this),p=new EditText(this);u.setHint("https://example.com/webdav/");u.setSingleLine();u.setText(prefs.getString("url",""));n.setHint("ユーザー名（必要な場合）");n.setText(prefs.getString("user",""));p.setHint("パスワード（端末内に保存）");p.setInputType(129);p.setText(prefs.getString("pass",""));l.addView(u);l.addView(n);l.addView(p);TextView cert=text("証明書: "+(alias.isEmpty()?"未選択":alias),15);l.addView(cert);l.addView(button("証明書をインストール",()->{try{startActivityForResult(KeyChain.createInstallIntent(),102);}catch(Exception e){message("証明書インストール画面を開けません: "+e.getMessage());}}));l.addView(button("インストール済み証明書を選択",()->{try{KeyChain.choosePrivateKeyAlias(this,a->ui.post(()->{if(a!=null){alias=a;cert.setText("証明書: "+a);}else message("証明書が選択されませんでした。端末にクライアント証明書をインストールしてください。");}),null,null,null,-1,alias.isEmpty()?null:alias);}catch(Exception e){message("証明書選択画面を開けません: "+e.getMessage());}}));l.addView(button("証明書を解除",()->{alias="";cert.setText("証明書: 未選択");}));l.addView(text("PKCS#12（.p12/.pfx）を端末にインストール後、インストール済み証明書を選択し、最後に保存を押してください。WebDAVパスワードはアプリ専用領域に保存します。",14));l.addView(button("保存",()->{String url=u.getText().toString().trim();if(!url.startsWith("https://")){message("HTTPSのURLを入力してください");return;}if(!url.endsWith("/"))url+="/";prefs.edit().putString("url",url).putString("user",n.getText().toString()).putString("pass",p.getText().toString()).putString("alias",alias).apply();home();}));}
  Dav client() throws Exception{return new Dav(this,prefs.getString("url",""),prefs.getString("user",""),prefs.getString("pass",""),prefs.getString("alias",""));}
  void browse(String url,String name){if(url.isEmpty()){settings();return;}reading=false;release();clear();root.addView(button("← ホーム",this::home));root.addView(text(clean(name),23));LinearLayout list=scroll();TextView status=text("読み込み中…",16);list.addView(status);async(()->{Dav d=client();return d.list(url);},items->{list.removeView(status);items.sort((a,b)->a.dir!=b.dir?(a.dir?-1:1):RemoteZip.natural(a.name,b.name));for(Dav.Item item:items){JSONObject rec=record(item.url);String sub=item.dir?"フォルダ":rec.length()==0?"未読":rec.optBoolean("finished")?"既読":"途中  "+(rec.optInt("page")+1)+" / "+rec.optInt("total");TextView v=entry(clean(item.name),author(item.name)+"  "+sub);list.addView(v);v.setOnClickListener(w->{if(item.dir)browse(item.url,item.name);else openBook(item.url,item.name,url,name);});}if(items.isEmpty())list.addView(text("フォルダまたはZIPがありません",16));});}
  void openBook(String url,String name,String folder,String fname){bookUrl=url;bookName=name;folderUrl=folder;folderName=fname;reading=true;controlsVisible=false;previous.clear();cuts.clear();JSONObject rec=record(url);page=rec.optInt("page",0);JSONArray arr=rec.optJSONArray("cuts");if(arr!=null)for(int i=0;i<arr.length();i++)cuts.add(arr.optInt(i));clear();root.addView(button("← ホーム",this::home));root.addView(text("本を開いています…",20));downloadState="保存準備中";async(()->{Dav d=client();RemoteZip opened=new RemoteZip(d,url);DownloadCache.start(this,d,url,state->ui.post(()->{if(reading && url.equals(bookUrl)){downloadState=state;if(downloadLabel!=null)downloadLabel.setText("端末への保存: "+state);}}));return opened;},z->{zip=z;page=Math.max(0,Math.min(page,zip.pages.size()-1));render();});}
  void save(){if(zip==null)return;JSONObject o=record(bookUrl);try{o.put("url",bookUrl);o.put("name",bookName);o.put("folder",folderUrl);o.put("folderName",folderName);o.put("page",page);o.put("total",zip.pages.size());o.put("time",System.currentTimeMillis());o.put("finished",false);JSONArray a=new JSONArray();for(int cut:cuts)a.put(cut);o.put("cuts",a);put(bookUrl,o);}catch(Exception ignored){}}
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
    bar.addView(button("見開き調整",()->new AlertDialog.Builder(this).setTitle("見開き調整").setItems(new String[]{"このページの次から1ページずらす","この本の調整をすべて解除"},(d,n)->{if(n==0 && page+1<zip.pages.size())cuts.add(page+1);else if(n==1)cuts.clear();previous.clear();render();}).show()));
    controls.addView(bar);
    downloadLabel=text("端末への保存: "+downloadState,12);controls.addView(downloadLabel);
    TextView status=text("読み込み中…",14);controls.addView(status);
    frame.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
    LinearLayout seekPanel=new LinearLayout(this);seekPanel.setOrientation(1);seekPanel.setGravity(Gravity.CENTER);seekPanel.setBackgroundColor(0xeef5f5f5);
    seekPanel.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(insets.getSystemWindowInsetLeft(),0,insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
    TextView caption=text("",14);caption.setGravity(Gravity.CENTER);caption.setPadding(4,2,4,2);seekPanel.addView(caption);
    HorizontalScrollView scroller=new HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(true);
    LinearLayout strip=new LinearLayout(this);strip.setOrientation(LinearLayout.HORIZONTAL);scroller.addView(strip);seekPanel.addView(scroller,new LinearLayout.LayoutParams(-1,-2));
    SeekBar seek=new SeekBar(this);seek.setMax(zip.pages.size()-1);seek.setProgress(page);seekPanel.addView(seek,new LinearLayout.LayoutParams(-1,-2));
    frame.addView(seekPanel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
    controls.setVisibility(controlsVisible?View.VISIBLE:View.GONE);seekPanel.setVisibility(controlsVisible?View.VISIBLE:View.GONE);
    final int requested=page;final RemoteZip current=zip;final TreeSet<Integer> anchors=new TreeSet<>(cuts);
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar v,int progress,boolean fromUser){if(fromUser)scheduleGallery(current,progress,strip,scroller,caption);}
      public void onStartTrackingTouch(SeekBar v){}
      public void onStopTrackingTouch(SeekBar v){int target=v.getProgress();if(target!=page)jumpToPage(target);}
    });
    if(controlsVisible)scheduleGallery(current,page,strip,scroller,caption);
    int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;
    async(()->{Bitmap a=current.bitmap(requested,width,height);Bitmap b=null;int n=requested+1;if(!current.isWide(requested)&&n<current.pages.size()&&!anchors.contains(n)){try{b=current.bitmap(n,width,height);}catch(Exception e){a.recycle();throw e;}if(!current.isWide(n))n++;else{b.recycle();b=null;}}return new Spread(a,b,n);},s->{
      release();right=s.a;left=s.b;next=s.next;status.setText((page+1)+(next>page+1?"–"+next:"")+" / "+zip.pages.size());
      if(left!=null){ImageView lv=new ImageView(this);lv.setImageBitmap(left);lv.setScaleType(ImageView.ScaleType.FIT_CENTER);images.addView(lv,new LinearLayout.LayoutParams(0,-1,1));}
      ImageView rv=new ImageView(this);rv.setImageBitmap(right);rv.setScaleType(ImageView.ScaleType.FIT_CENTER);images.addView(rv,new LinearLayout.LayoutParams(0,-1,1));
      images.setOnTouchListener(new View.OnTouchListener(){float x;public boolean onTouch(View v,android.view.MotionEvent e){
        if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){x=e.getX();return true;}
        if(e.getAction()==android.view.MotionEvent.ACTION_UP){float delta=e.getX()-x;if(Math.abs(delta)>70){if(delta>0)forward();else backPage();}
          else if(e.getX()<v.getWidth()/3f)forward();else if(e.getX()>v.getWidth()*2f/3f)backPage();
          else {controlsVisible=!controlsVisible;systemBars(!controlsVisible);controls.setVisibility(controlsVisible?View.VISIBLE:View.GONE);seekPanel.setVisibility(controlsVisible?View.VISIBLE:View.GONE);if(controlsVisible)scheduleGallery(current,seek.getProgress(),strip,scroller,caption);else {clearThumbnail();strip.removeAllViews();}root.requestApplyInsets();controls.requestApplyInsets();seekPanel.requestApplyInsets();}
          return true;}return true;}});
      save();
    });
  }
  void jumpToPage(int target){
    if(zip==null)return;
    RemoteZip current=zip;TreeSet<Integer> anchors=new TreeSet<>(cuts);
    controlsVisible=false;clear();release();
    root.addView(button("← ホーム",this::home));
    root.addView(text("見開きの位置を確認しています…",20));
    final int jobGeneration=generation;
    async(()->SpreadPlanner.containing(target,current.pages.size(),anchors,index->{
      if(jobGeneration!=generation)throw new java.io.IOException("中断しました");
      return current.isWide(index);
    }),start->{previous.clear();page=start;next=0;render();});
  }
  void forward(){if(zip==null||next<=page)return;if(next>=zip.pages.size()){finishBook();return;}previous.put(next,page);page=next;next=0;controlsVisible=false;render();}
  void finishBook(){
    final String finishedUrl=bookUrl, parent=folderUrl; final JSONObject o=record(bookUrl);
    try{o.put("finished",true);o.put("time",System.currentTimeMillis());put(bookUrl,o);}catch(Exception ignored){}
    DownloadCache.delete(this,finishedUrl);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("読み終わりました").setMessage("次の巻を確認しています…").setCancelable(false).setPositiveButton("次の巻へ",null).setNegativeButton("ホームへ戻る",(d,w)->home()).create();
    dialog.setOnShowListener(v->{Button b=dialog.getButton(AlertDialog.BUTTON_POSITIVE);b.setEnabled(false);async(()->{Dav d=client();java.util.List<Dav.Item> items=d.list(parent);items.removeIf(item->item.dir);items.sort((a,c)->RemoteZip.natural(a.name,c.name));for(int i=0;i<items.size();i++)if(items.get(i).url.equals(finishedUrl))return i+1<items.size()?items.get(i+1):null;return null;},item->{if(!dialog.isShowing())return;if(item==null){dialog.setMessage("次の巻はありません。ホームへ戻ってください。");}else{dialog.setMessage("次の巻: "+clean(item.name));b.setEnabled(true);b.setOnClickListener(w->{dialog.dismiss();openBook(item.url,item.name,parent,folderName);});}});});
    dialog.show();
  }
  void backPage(){if(zip==null||page<=0)return;Integer p=previous.get(page);page=p!=null?p:Math.max(0,page-2);next=0;controlsVisible=false;render();}
  @Override public void onBackPressed(){if(reading)home();else home();}
  @Override protected void onDestroy(){generation++;worker.shutdownNow();thumbWorker.shutdownNow();clearThumbnail();release();super.onDestroy();}
}
