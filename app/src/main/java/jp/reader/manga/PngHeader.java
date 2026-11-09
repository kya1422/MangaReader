package jp.reader.manga;
/** Reads PNG IHDR dimensions without decoding pixel data. */
final class PngHeader {
  private static final int[] MAGIC={137,80,78,71,13,10,26,10};
  static int[] dimensions(byte[] b){
    if(b.length<24)return null;
    for(int i=0;i<MAGIC.length;i++)if((b[i]&255)!=MAGIC[i])return null;
    if(b[8]!=0||b[9]!=0||b[10]!=0||b[11]!=13||b[12]!='I'||b[13]!='H'||b[14]!='D'||b[15]!='R')return null;
    long w=number(b,16),h=number(b,20);
    if(w==0||h==0||w>Integer.MAX_VALUE||h>Integer.MAX_VALUE)return null;
    return new int[]{(int)w,(int)h};
  }
  private static long number(byte[] b,int p){return ((long)(b[p]&255)<<24)|((long)(b[p+1]&255)<<16)|((long)(b[p+2]&255)<<8)|(b[p+3]&255);}
}
