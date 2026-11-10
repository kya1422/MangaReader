package jp.reader.manga;

/** A viewport plus one viewport of preloading on each side, in content coordinates. */
final class CoverWindow {
  static boolean contains(int rowTop,int rowBottom,int scrollY,int viewportHeight){
    if(viewportHeight<=0 || rowBottom<=rowTop)return false;
    long first=Math.max(0L,(long)scrollY-viewportHeight);
    long last=(long)scrollY+2L*viewportHeight;
    return rowBottom>first && rowTop<last;
  }
}
