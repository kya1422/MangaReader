package jp.reader.manga;
class TestBrowse {
  static void ok(boolean b,String s){if(!b)throw new AssertionError(s);System.out.println("PASS "+s);}
  public static void main(String[] args){
    ok(BrowseSearch.matches("[作者] Ｍａｎｇａ １２.zip",true,"manga 12"),"全角・半角と英字の大小を無視");
    ok(BrowseSearch.matches("[作者] 巻01.zip",true,"作者"),"元の名前の作者を検索");
    ok(!BrowseSearch.matches("第1巻.zip",true,"第2巻"),"不一致を除外");
    ok(BrowseSearch.matches("第1巻.zip",false,"  "),"空検索はファイルも表示");
    ok(!BrowseSearch.matches("漫画.zip",false,"漫画"),"検索中は一致するZIPも非表示");
    ok(BrowseSearch.matches("漫画",true,"漫画"),"一致するフォルダを表示");
  }
}
