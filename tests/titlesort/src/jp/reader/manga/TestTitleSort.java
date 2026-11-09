package jp.reader.manga;
class TestTitleSort {
 static void ok(boolean v,String name){if(!v)throw new AssertionError(name);System.out.println("PASS "+name);}
 public static void main(String[] args){
  TitleSort s=new TitleSort();
  ok(s.compare("[作者Z] Alpha","[作者A] Zebra")<0,"作者名でなくタイトル順");
  ok(s.compare("[作家] 作品2.zip","[作家] 作品10.zip")<0,"巻数の自然順");
  ok(s.compare("[作家] 作品２.zip","[作家] 作品１０.zip")<0,"全角数字の自然順");
  ok(s.compare("[作家] あいう","[作家] かきく")<0,"かなの順序");
  java.util.Random r=new java.util.Random(31);String chars="abcあか漢作品0123";java.util.ArrayList<String> items=new java.util.ArrayList<>();for(int i=0;i<400;i++){StringBuilder b=new StringBuilder();for(int j=0;j<1+r.nextInt(8);j++)b.append(chars.charAt(r.nextInt(chars.length())));items.add(b.toString());}for(int i=0;i<30;i++){java.util.Collections.shuffle(items,r);items.sort(s::compare);}ok(true,"多様なタイトルの並べ替え");
  ok(TitleSort.displayTitle("(一般) [作家] 作品.zip").equals("作品"),"表示名と同じタイトル抽出");
 }
}
