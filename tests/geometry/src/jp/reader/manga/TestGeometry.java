package jp.reader.manga;
class TestGeometry {
 static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);System.out.println("PASS "+msg);}
 static boolean eq(float a,float b){return Math.abs(a-b)<0.01f;}
 public static void main(String[] args){
  SpreadGeometry.Box left=SpreadGeometry.fit(1600,1000,600,1200,-1),right=SpreadGeometry.fit(1600,1000,700,1200,1);
  ok(eq(left.right,800)&&eq(right.left,800),"見開きの中央に隙間なし");
  ok(eq((left.right-left.left)/(left.bottom-left.top),0.5f),"左ページの比率維持");
  ok(eq((right.right-right.left)/(right.bottom-right.top),700f/1200),"右ページの比率維持");
  SpreadGeometry.Box wide=SpreadGeometry.fit(1600,1000,2000,1000,0);
  ok(eq(wide.left,0)&&eq(wide.right,1600)&&eq(wide.top,100),"単独の横長画像を中央表示");
  SpreadGeometry.Box l=SpreadGeometry.fit(1001,800,601,1000,-1),r=SpreadGeometry.fit(1001,800,601,1000,1);
  ok(eq(l.right,r.left),"奇数ピクセル幅でも綴じ目を合わせる");
 }
}
