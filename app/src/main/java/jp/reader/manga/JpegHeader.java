package jp.reader.manga;

/** Reads dimensions from JPEG SOF markers; null means the available prefix is incomplete. */
final class JpegHeader {
  static int[] dimensions(byte[] b){
    if(b.length<4 || (b[0]&255)!=255 || (b[1]&255)!=216)return null;
    int p=2;
    while(p<b.length){
      if((b[p++]&255)!=255)return null;
      while(p<b.length && (b[p]&255)==255)p++;
      if(p>=b.length)return null;
      int marker=b[p++]&255;
      if(marker==0xd9||marker==0xda)return null;
      if(marker==0x01 || marker==0xd8 || (marker>=0xd0&&marker<=0xd7))continue;
      if(p+2>b.length)return null;
      int len=((b[p]&255)<<8)|(b[p+1]&255);if(len<2||p+len>b.length)return null;
      if(marker>=0xc0&&marker<=0xcf&&marker!=0xc4&&marker!=0xc8&&marker!=0xcc){
        if(len<7)return null;int h=((b[p+3]&255)<<8)|(b[p+4]&255),w=((b[p+5]&255)<<8)|(b[p+6]&255);return w>0&&h>0?new int[]{w,h}:null;
      }
      p+=len;
    }
    return null;
  }
}
