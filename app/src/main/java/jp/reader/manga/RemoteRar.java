package jp.reader.manga;

import com.github.junrar.Archive;
import com.github.junrar.ArchiveOptions;
import com.github.junrar.io.SeekableReadOnlyByteChannel;
import com.github.junrar.rarfile.FileHeader;
import com.github.junrar.volume.Volume;
import com.github.junrar.volume.VolumeManager;
import android.graphics.BitmapFactory;
import java.io.*;
import java.util.*;

/** RAR archive accessed through bounded WebDAV ranges. Single-volume, non-solid, unencrypted only.
 * Junrar uses the UnRAR License; its code may not be used to develop a RAR-compatible archiver.
 */
final class RemoteRar extends RemoteZip {
  private static final int BLOCK=64*1024, MAX_IMAGE=32*1024*1024;
  private static final java.util.concurrent.Semaphore DECODER=new java.util.concurrent.Semaphore(1);
  private static final long MAX_PACKED=32L*1024*1024, MAX_DICTIONARY=32L*1024*1024;
  RemoteRar(Dav dav,String url)throws Exception{
    super(dav,url,true);
    try(Archive archive=openArchive()){
      if(archive.hasBrokenHeaders())throw new IOException("RARのヘッダーが破損しています");
      if(archive.isPasswordProtected())throw new IOException("暗号化RARは未対応です");
      if(archive.getMainHeader()!=null && archive.getMainHeader().isSolid())throw new IOException("ソリッドRARは未対応です");
      List<FileHeader> headers=archive.getFileHeaders();
      if(headers.size()>30000)throw new IOException("RAR内のファイル数が多すぎます");
      for(int i=0;i<headers.size();i++){
        FileHeader h=headers.get(i);
        if(h.isSolid())throw new IOException("ソリッドRARは未対応です");
        if(h.isSplitAfter()||h.isSplitBefore())throw new IOException("分割RARは未対応です");
        if(h.isDirectory())continue;
        String n=h.getFileName().replace('\\','/'),lower=n.toLowerCase(Locale.ROOT);
        if((lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png"))&&!n.startsWith("__MACOSX/")&&!n.substring(n.lastIndexOf('/')+1).startsWith("."))
          pages.add(new Page(n,i,h.getFullPackSize(),h.getFullUnpackSize(),0,0));
      }
    }
    pages.sort((a,b)->natural(a.name,b.name));
    if(pages.isEmpty())throw new IOException("JPEGまたはPNGが見つかりません");
  }
  private Archive openArchive()throws Exception{
    VolumeManager manager=(archive,last)->last==null?new RemoteVolume(archive):null;
    return new Archive(manager,ArchiveOptions.builder().maxDictionarySize(MAX_DICTIONARY).build());
  }
  private final class RemoteVolume implements Volume {
    final Archive archive;RemoteVolume(Archive a){archive=a;}
    public SeekableReadOnlyByteChannel getChannel(){return new Channel();}
    public long getLength(){return archiveLength;}
    public Archive getArchive(){return archive;}
  }
  private final class Channel implements SeekableReadOnlyByteChannel {
    long position=0,blockStart=-1;byte[] block;
    public long getPosition(){return position;}
    public void setPosition(long p)throws IOException{if(p<0||p>archiveLength)throw new IOException("RAR位置が不正です");position=p;}
    public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}
    public int read(byte[] out,int off,int len)throws IOException{
      if(off<0||len<0||off>out.length-len)throw new IndexOutOfBoundsException();if(len==0)return 0;if(position>=archiveLength)return -1;
      if(Thread.currentThread().isInterrupted())throw new IOException("RARの読み込みを中止しました");
      try{
        if(block==null||position<blockStart||position>=blockStart+block.length){
          blockStart=position-(position%BLOCK);long end=Math.min(archiveLength-1,blockStart+BLOCK-1);
          block=dav.range(url,blockStart,end).data;
          if(block.length!=end-blockStart+1)throw new IOException("RARの部分取得が不完全です");
        }
        int n=(int)Math.min(len,blockStart+block.length-position);System.arraycopy(block,(int)(position-blockStart),out,off,n);position+=n;return n;
      }catch(IOException e){throw e;}catch(Exception e){throw new IOException("RARの部分取得に失敗しました",e);}
    }
    public int readFully(byte[] out,int count)throws IOException{int done=0;while(done<count){int n=read(out,done,count-done);if(n<0)throw new EOFException("RARが途中で切れています");done+=n;}return done;}
    public void close(){block=null;}
  }
  @Override byte[] imageData(int idx)throws Exception{
    Page page=pages.get(idx);
    if(page.compressed<0||page.compressed>MAX_PACKED||page.uncompressed<0||page.uncompressed>MAX_IMAGE)throw new IOException("RAR内の画像が大きすぎます");
    DECODER.acquire();
    try(Archive archive=openArchive()){
      List<FileHeader> headers=archive.getFileHeaders();int index=(int)page.offset;
      if(index<0||index>=headers.size())throw new IOException("RAR内のページが見つかりません");
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();
      OutputStream bounded=new OutputStream(){int written=0;
        public void write(int b)throws IOException{if(written>=MAX_IMAGE)throw new IOException("画像が大きすぎます");bytes.write(b);written++;}
        public void write(byte[] b,int off,int n)throws IOException{if(n<0||n>MAX_IMAGE-written)throw new IOException("画像が大きすぎます");bytes.write(b,off,n);written+=n;}
      };
      archive.extractFile(headers.get(index),bounded);return bytes.toByteArray();
    }finally{DECODER.release();}
  }
  @Override boolean isWide(int idx)throws Exception{
    Boolean known=orientations.get(idx);if(known!=null)return known;
    byte[] raw=imageData(idx);int[] d=JpegHeader.dimensions(raw);if(d==null)d=PngHeader.dimensions(raw);
    if(d==null){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException("RAR内の画像を読めません");d=new int[]{o.outWidth,o.outHeight};}
    boolean wide=d[0]>=d[1];orientations.put(idx,wide);return wide;
  }
}
