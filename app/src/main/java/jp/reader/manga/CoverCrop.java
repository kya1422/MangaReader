package jp.reader.manga;
/** Best-effort detection: wide covers may be two-page scans. */
final class CoverCrop {
  static int leftWidth(int width,int height){
    if(width<=0||height<=0)throw new IllegalArgumentException("Invalid cover dimensions");
    return (long)width*5>(long)height*6?Math.max(1,width/2):width;
  }
}
