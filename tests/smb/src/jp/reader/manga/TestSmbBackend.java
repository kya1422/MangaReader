package jp.reader.manga;
import java.util.*;
class TestSmbBackend{
 static void ok(boolean value,String label){if(!value)throw new AssertionError(label);System.out.println("PASS "+label);}
 public static void main(String[] a)throws Exception{
  String root="https://dav.example/Comic/";
  SmbBackend smb=new SmbBackend(root,"127.0.0.1",1445,"books","smbtest","test-password".toCharArray(),"");
  List<SmbBackend.Entry> rows=smb.list(root);SmbBackend.Entry dir=null;for(SmbBackend.Entry e:rows)if(e.name.equals("作品"))dir=e;
  ok(dir!=null&&dir.directory,"SMB UTF-8 directory listing");
  List<SmbBackend.Entry> files=smb.list(dir.url);SmbBackend.Entry small=null,large=null;for(SmbBackend.Entry e:files){if(e.name.equals("01.zip"))small=e;if(e.name.equals("large.zip"))large=e;}
  ok(small!=null&&large!=null,"SMB archive listing");
  java.lang.reflect.Field sharedField=SmbBackend.class.getDeclaredField("shared");sharedField.setAccessible(true);Object original=sharedField.get(smb);
  String url=small.url;SmbBackend.Range p=smb.range(url,10,17);ok(p.total==36&&new String(p.data,"UTF-8").equals("abcdefgh"),"SMB range exact");
  ok(original!=null&&sharedField.get(smb)==original,"SMB session reused for multiple requests");
  java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newFixedThreadPool(4);
  java.util.List<java.util.concurrent.Future<Boolean>> results=new java.util.ArrayList<>();
  for(int i=0;i<12;i++){final int offset=i;results.add(executor.submit(()->smb.range(url,offset,offset+3).data.length==4));}
  for(java.util.concurrent.Future<Boolean> result:results)ok(result.get(),"parallel SMB range");executor.shutdown();
  java.lang.reflect.Field connectionField=original.getClass().getDeclaredField("connection");connectionField.setAccessible(true);
  ((com.hierynomus.smbj.connection.Connection)connectionField.get(original)).close();
  ok(new String(smb.range(url,0,3).data,"UTF-8").equals("0123")&&sharedField.get(smb)!=original,"closed connection re-established");
  SmbBackend.Range tail=smb.range(url,-1,4);ok(new String(tail.data,"UTF-8").equals("wxyz"),"SMB tail");
  try(SmbBackend.Download dl=smb.download(url)){ok(dl.length==36&&new String(dl.stream.readAllBytes(),"UTF-8").equals("0123456789abcdefghijklmnopqrstuvwxyz"),"SMB complete stream");}
  SmbBackend.Range big=smb.range(large.url,65000,65000+2*1024*1024-1);ok(big.data.length==2*1024*1024&&big.total==3*1024*1024,"multi-block SMB range");
  for(int i=0;i<big.data.length;i++)if((big.data[i]&255)!=((65000+i)&255))throw new AssertionError("range byte "+i);
  try(SmbBackend.Download dl=smb.download(large.url)){byte[] buf=new byte[65536];int n;long pos=0;while((n=dl.stream.read(buf))!=-1){for(int i=0;i<n;i++)if((buf[i]&255)!=((pos+i)&255))throw new AssertionError("download byte "+pos);pos+=n;}ok(pos==3*1024*1024&&dl.length==pos,"multi-block SMB download");}
  try{new SmbBackend(root,"127.0.0.1",1445,"books","smbtest","wrong".toCharArray(),"").list(root);throw new AssertionError("wrong password");}catch(Exception expected){System.out.println("PASS bad password rejected");}
  try{smb.range("https://dav.example/Comic2/x.zip",0,3);throw new AssertionError("outside root accepted");}catch(IllegalArgumentException expected){System.out.println("PASS root confinement");}
  smb.close();try{smb.list(root);throw new AssertionError("closed client used");}catch(java.io.IOException expected){System.out.println("PASS closed client rejects reuse");}
 }
}
