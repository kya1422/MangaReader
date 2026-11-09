package jp.reader.manga;
class TestCoverCrop {
 static void ok(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){
  ok(CoverCrop.leftWidth(1800,1200)==900);
  ok(CoverCrop.leftWidth(1801,1200)==900);
  ok(CoverCrop.leftWidth(800,1200)==800);
  ok(CoverCrop.leftWidth(1200,1000)==1200);
  ok(CoverCrop.leftWidth(1201,1000)==600);
  System.out.println("PASS cover crop for wide images, full portrait image");
 }
}
