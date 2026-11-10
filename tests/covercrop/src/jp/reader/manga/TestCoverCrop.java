package jp.reader.manga;
class TestCoverCrop {
 static void ok(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){
  ok(CoverCrop.leftWidth(1800,1200)==900);
  ok(CoverCrop.leftWidth(1801,1200)==900);
  ok(CoverCrop.leftWidth(800,1200)==800);
  ok(CoverCrop.leftWidth(1200,1000)==1200);
  ok(CoverCrop.leftWidth(1201,1000)==600);
  ok(CoverCrop.manualWidth(1600,800)==600);
  ok(CoverCrop.manualLeft(1600,800,0)==0);
  ok(CoverCrop.manualLeft(1600,800,5000)==500);
  ok(CoverCrop.manualLeft(1600,800,10000)==1000);
  ok(CoverCrop.manualLeft(1600,800,99999)==1000);
  ok(CoverCrop.manualWidth(400,800)==400&&CoverCrop.manualLeft(400,800,10000)==0);
  System.out.println("PASS fixed-height frame horizontal crop and legacy auto crop");
 }
}
