package jp.reader.manga;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.*;

final class RemoteZip {
  static final class Page {String name;long offset,compressed,uncompressed;int method,flags; Page(String n,long o,long c,long u,int m,int f){name=n;offset=o;compressed=c;uncompressed=u;method=m;flags=f;}}
  final Dav dav;final String url;final List<Page> pages=new ArrayList<>();
  final java.util.concurrent.ConcurrentHashMap<Integer,Boolean> orientations=new java.util.concurrent.ConcurrentHashMap<>();
  static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);} static long u32(byte[] b,int p){return ((long)u16(b,p))|((long)u16(b,p+2)<<16);}
  RemoteZip(Dav d,String u) throws Exception {dav=d;url=u;Dav.Range tail=d.range(u,-1,65557);byte[] b=tail.data;int end=-1;
    for(int i=b.length-22;i>=0;i--)if(u32(b,i)==0x06054b50L && i+22+u16(b,i+20)==b.length){end=i;break;}
    if(end<0)throw new IOException("ZIPの終端が見つかりません"); if(u16(b,end+4)!=0||u16(b,end+6)!=0)throw new IOException("分割ZIPは未対応です");long size=u32(b,end+12),off=u32(b,end+16);int count=u16(b,end+10);
    if(size==0xffffffffL||off==0xffffffffL||count==65535)throw new IOException("ZIP64は未対応です");if(size<=0||size>8*1024*1024||off+size>tail.total)throw new IOException("ZIP目次が大きすぎるか不正です");
    b=d.range(u,off,off+size-1).data;int p=0;for(int i=0;i<count;i++) {if(p+46>b.length||u32(b,p)!=0x02014b50L)throw new IOException("ZIP目次が不正です");int flags=u16(b,p+8),method=u16(b,p+10),nl=u16(b,p+28),el=u16(b,p+30),cl=u16(b,p+32);if(p+46+nl+el+cl>b.length)throw new IOException("ZIP目次が途中で切れています");String n=ZipNames.decode(b,p+46,nl,(flags&0x800)!=0);
      String lo=n.toLowerCase(Locale.ROOT);if((lo.endsWith(".jpg")||lo.endsWith(".jpeg"))&&!n.startsWith("__MACOSX/")&&!n.substring(n.lastIndexOf('/')+1).startsWith("."))pages.add(new Page(n,u32(b,p+42),u32(b,p+20),u32(b,p+24),method,flags));p+=46+nl+el+cl;}
    Collections.sort(pages,(a,c)->natural(a.name,c.name));if(pages.isEmpty())throw new IOException("JPEGが見つかりません");
  }
  static int natural(String a,String b){int i=0,j=0;while(i<a.length()&&j<b.length()){char x=a.charAt(i),y=b.charAt(j);if(Character.isDigit(x)&&Character.isDigit(y)){int ie=i,je=j;while(ie<a.length()&&Character.isDigit(a.charAt(ie)))ie++;while(je<b.length()&&Character.isDigit(b.charAt(je)))je++;String as=a.substring(i,ie).replaceFirst("^0+(?!$)",""),bs=b.substring(j,je).replaceFirst("^0+(?!$)","");int r=Integer.compare(as.length(),bs.length());if(r==0)r=as.compareTo(bs);if(r!=0)return r;i=ie;j=je;}else{int r=Character.compare(Character.toLowerCase(x),Character.toLowerCase(y));if(r!=0)return r;i++;j++;}}return Integer.compare(a.length()-i,b.length()-j);}
  byte[] jpegData(int idx) throws Exception {Page p=pages.get(idx);if((p.flags&1)!=0)throw new IOException("暗号化ZIPは未対応です");if(p.compressed>32*1024*1024||p.uncompressed>80*1024*1024)throw new IOException("画像が大きすぎます");if(p.method!=0&&p.method!=8)throw new IOException("ZIP圧縮形式が未対応です");
    byte[] head=dav.range(url,p.offset,p.offset+29).data;if(u32(head,0)!=0x04034b50L)throw new IOException("ZIPヘッダーが不正です");long start=p.offset+30+u16(head,26)+u16(head,28);if(p.compressed==0)throw new IOException("画像が空です");byte[] packed=dav.range(url,start,start+p.compressed-1).data;byte[] raw;if(p.method==0)raw=packed;else{Inflater inflater=new Inflater(true);try{raw=Dav.read(new InflaterInputStream(new ByteArrayInputStream(packed),inflater),80*1024*1024);}finally{inflater.end();}}
    return raw;
  }
  boolean isWide(int idx) throws Exception {
    Boolean known=orientations.get(idx);if(known!=null)return known;
    Page p=pages.get(idx);
    if((p.flags&1)!=0 || (p.method!=0 && p.method!=8) || p.compressed>32L*1024*1024 || p.uncompressed>80L*1024*1024 || p.compressed==0)
      throw new IOException("画像形式が未対応か、画像が大きすぎます");
    byte[] head=dav.range(url,p.offset,p.offset+29).data;
    if(u32(head,0)!=0x04034b50L)throw new IOException("ZIPヘッダーが不正です");
    long offset=p.offset+30+u16(head,26)+u16(head,28);
    int size=(int)Math.min(p.compressed,8192);
    while(size>0){
      byte[] packed=dav.range(url,offset,offset+size-1).data;
      byte[] prefix=p.method==0?packed:inflatePrefix(packed,1024*1024);
      int[] dimensions=JpegHeader.dimensions(prefix);
      if(dimensions!=null){boolean wide=dimensions[0]>=dimensions[1];orientations.put(idx,wide);return wide;}
      if(size>=p.compressed || size>=512*1024)break;
      size=(int)Math.min(p.compressed,Math.min(512*1024L,size*4L));
    }
    // Rare JPEGs may have very large metadata before SOF; retain the previous full-data fallback.
    byte[] raw=jpegData(idx);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
    BitmapFactory.decodeByteArray(raw,0,raw.length,o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException("JPEGを読めません");
    boolean wide=o.outWidth>=o.outHeight;orientations.put(idx,wide);return wide;
  }
  static byte[] inflatePrefix(byte[] packed,int limit)throws IOException {
    Inflater inflater=new Inflater(true);
    try{inflater.setInput(packed);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];
      while(!inflater.finished()&&!inflater.needsInput()&&out.size()<limit){
        int n;try{n=inflater.inflate(b,0,Math.min(b.length,limit-out.size()));}catch(java.util.zip.DataFormatException e){throw new IOException("ZIP圧縮データが不正です",e);}
        if(n==0){if(inflater.needsDictionary())throw new IOException("ZIPの辞書圧縮は未対応です");break;}out.write(b,0,n);
      }return out.toByteArray();
    }finally{inflater.end();}
  }
  Bitmap bitmap(int idx,int maxW,int maxH) throws Exception {
    byte[] raw=jpegData(idx);
    BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException("JPEGを読めません");orientations.put(idx,o.outWidth>=o.outHeight);o.inSampleSize=1;while(o.outWidth/o.inSampleSize>maxW*2 || o.outHeight/o.inSampleSize>maxH*2)o.inSampleSize*=2;o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;Bitmap result=BitmapFactory.decodeByteArray(raw,0,raw.length,o);if(result==null)throw new IOException("画像の読み込みに失敗しました");return result;
  }
}
