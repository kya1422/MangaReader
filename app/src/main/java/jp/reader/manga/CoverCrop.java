package jp.reader.manga;
/** Best-effort detection: wide covers may be two-page scans. */
final class CoverCrop {
  // Manual frame uses the same 72:96 proportions as the folder-list cover slot.
  static int manualWidth(int width,int height){
    if(width<=0||height<=0)throw new IllegalArgumentException("Invalid cover dimensions");
    return (int)Math.min(width,Math.max(1L,((long)height*3+2)/4));
  }
  static int manualLeft(int width,int height,int position){
    int available=width-manualWidth(width,height);int p=Math.max(0,Math.min(10000,position));
    return (int)(((long)available*p+5000)/10000);
  }
  static int leftWidth(int width,int height){
    if(width<=0||height<=0)throw new IllegalArgumentException("Invalid cover dimensions");
    return (long)width*5>(long)height*6?Math.max(1,width/2):width;
  }
}
