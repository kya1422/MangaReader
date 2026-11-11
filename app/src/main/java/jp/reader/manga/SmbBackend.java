package jp.reader.manga;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.msfscc.fileinformation.FileStandardInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only SMB2/3 access. A logical URL always remains the WebDAV URL. */
final class SmbBackend implements Closeable {
  static final class Entry {final String url,name;final boolean directory;final long createdAt;Entry(String u,String n,boolean d,long t){url=u;name=n;directory=d;createdAt=t;}}
  static final class Range {final byte[] data;final long total;Range(byte[] d,long t){data=d;total=t;}}
  static final class Download implements Closeable {final long length;final InputStream stream;Download(long n,InputStream s){length=n;stream=s;}public void close()throws IOException{stream.close();}}
  private final String host,share,user,domain;private final char[] password;private final SmbPathMap paths;private final int port;
  private OpenShare shared;private boolean closed;
  private synchronized OpenShare share() throws Exception {if(closed)throw new IOException("SMB接続は閉じています");if(shared==null)shared=new OpenShare();return shared;}
  private void invalidate(OpenShare failed){synchronized(this){if(shared!=failed)return;shared=null;}failed.close();}
  public void close(){OpenShare old;synchronized(this){if(closed)return;closed=true;old=shared;shared=null;java.util.Arrays.fill(password,'\0');}if(old!=null)old.close();}
  private interface Operation<T>{T run(OpenShare open)throws Exception;}
  private <T>T reuse(Operation<T> operation)throws Exception{
    for(int attempt=0;;attempt++){
      OpenShare open=share();
      try{return operation.run(open);}catch(Exception e){
        if(attempt>0||Thread.currentThread().isInterrupted()||(!reconnectable(e)&&open.connection.isConnected()&&open.disk.isConnected()))throw e;
        invalidate(open);
      }
    }
  }
  private static boolean reconnectable(Throwable t){
    for(;t!=null;t=t.getCause()){
      if(t instanceof com.hierynomus.protocol.transport.TransportException || t instanceof java.net.SocketException || t instanceof java.net.SocketTimeoutException)return true;
      if(t instanceof com.hierynomus.mssmb2.SMBApiException){String status=((com.hierynomus.mssmb2.SMBApiException)t).getStatus().name();return status.equals("STATUS_NETWORK_SESSION_EXPIRED")||status.equals("STATUS_USER_SESSION_DELETED")||status.equals("STATUS_CONNECTION_DISCONNECTED");}
    }return false;
  }
  SmbBackend(String root,String host,int port,String share,String user,char[] password,String domain){
    if(host==null||host.isEmpty()||share==null||share.isEmpty()||share.contains("/")||share.contains("\\"))throw new IllegalArgumentException("SMB接続先が不正です");
    this.host=host;this.port=port;this.share=share;this.user=user;this.password=password.clone();this.domain=domain;this.paths=new SmbPathMap(root);
  }
  String relative(String url){return paths.relative(url);}
  private static final SmbConfig CONFIG=SmbConfig.builder().withSigningRequired(true).withSoTimeout(8,TimeUnit.SECONDS).withTimeout(8,TimeUnit.SECONDS).build();
  private final class OpenShare implements Closeable {
    SMBClient client;Connection connection;Session session;DiskShare disk;
    OpenShare()throws Exception{
      try{client=new SMBClient(CONFIG);connection=client.connect(host,port);session=connection.authenticate(new AuthenticationContext(user,password.clone(),domain));disk=(DiskShare)session.connectShare(share);}
      catch(Exception e){close();throw e;}
    }
    public void close(){if(disk!=null)try{disk.close();}catch(Exception ignored){}if(session!=null)try{session.close();}catch(Exception ignored){}if(connection!=null)try{connection.close();}catch(Exception ignored){}if(client!=null)try{client.close();}catch(Exception ignored){}}
  }
  void checkConnection()throws Exception{
    reuse(open->{if(!open.connection.isConnected()||!open.disk.isConnected())throw new IOException("SMB接続が切れました");return null;});
  }
  private com.hierynomus.smbj.share.File openFile(OpenShare open,String url){return open.disk.openFile(paths.relative(url),EnumSet.of(AccessMask.GENERIC_READ),null,SMB2ShareAccess.ALL,SMB2CreateDisposition.FILE_OPEN,null);}
  List<Entry> list(String url)throws Exception{
    paths.relative(url);
    return reuse(open->{
      List<Entry> out=new ArrayList<>();
      for(FileIdBothDirectoryInformation f:open.disk.list(paths.relative(url))){
        String n=f.getFileName();if(n.equals(".")||n.equals("..")||(f.getFileAttributes()&0x400L)!=0)continue;
        boolean dir=(f.getFileAttributes()&0x10L)!=0;String lower=n.toLowerCase(Locale.ROOT);
        if(!dir&&!lower.endsWith(".zip")&&!lower.endsWith(".cbz")&&!lower.endsWith(".rar")&&!lower.endsWith(".cbr"))continue;
        try{long time=f.getCreationTime()==null?0:f.getCreationTime().toEpochMillis();out.add(new Entry(paths.child(url,n,dir),n,dir,time));}catch(IllegalArgumentException ignored){}
      }return out;
    });
  }
  Range range(String url,long start,long end)throws Exception{
    paths.relative(url);
    return reuse(open->{try(com.hierynomus.smbj.share.File file=openFile(open,url)){
      long total=file.getFileInformation(FileStandardInformation.class).getEndOfFile();
      long first=start<0?Math.max(0,total-end):start,last=start<0?total-1:Math.min(end,total-1);
      if(first<0||last<first||last-first+1>33L*1024*1024)throw new IOException("SMBの範囲が不正か上限超過です");
      byte[] out=new byte[(int)(last-first+1)];int done=0;
      while(done<out.length){int n=file.read(out,first+done,done,Math.min(1024*1024,out.length-done));if(n<=0)throw new EOFException("SMBの読み取りが途中で切れました");done+=n;}
      return new Range(out,total);
    }});
  }
  Download download(String url)throws Exception{
    paths.relative(url);OpenShare open=new OpenShare();com.hierynomus.smbj.share.File file=null;
    try{
      file=openFile(open,url);long length=file.getFileInformation(FileStandardInformation.class).getEndOfFile();
      final com.hierynomus.smbj.share.File owned=file;
      InputStream in=new InputStream(){
        boolean closed;long position;
        public int read()throws IOException{byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:one[0]&255;}
        public int read(byte[] b,int offset,int count)throws IOException{
          if(closed)throw new IOException("SMBストリームは閉じています");
          if(offset<0||count<0||offset>b.length-count)throw new IndexOutOfBoundsException();
          if(count==0)return 0;if(position>=length)return -1;
          try{int n=owned.read(b,position,offset,(int)Math.min(Math.min(count,65536),length-position));if(n<=0)throw new EOFException("SMBファイルが途中で切れました");position+=n;return n;}
          catch(IOException e){throw e;}catch(RuntimeException e){throw new IOException("SMB読み取りに失敗しました",e);}
        }
        public void close(){if(closed)return;closed=true;try{owned.close();}catch(Exception ignored){}finally{open.close();}}
      };
      return new Download(length,in);
    }catch(Exception e){if(file!=null)try{file.close();}catch(Exception ignored){}open.close();throw e;}
  }
}
