package jp.reader.manga;
import java.util.*;
class TestSmbBackend{
 static void ok(boolean value,String label){if(!value)throw new AssertionError(label);System.out.println("PASS "+label);}
 public static void main(String[] a)throws Exception{
  String root="https://dav.example/Comic/";
  SmbBackend smb=new SmbBackend(root,"127.0.0.1",1445,"books","smbtest","test-password".toCharArray(),"");
  List<SmbBackend.Entry> rows=smb.list(root);SmbBackend.Entry dir=null;for(SmbBackend.Entry e:rows)if(e.name.equals("作品"))dir=e;
  ok(dir!=null&&dir.directory,"SMB UTF-8 directory listing");
  List<SmbBackend.Entry> files=smb.list(dir.url);ok(files.size()==1&&files.get(0).name.equals("01.zip"),"SMB archive listing");
  String url=files.get(0).url;SmbBackend.Range p=smb.range(url,10,17);ok(p.total==36&&new String(p.data,"UTF-8").equals("abcdefgh"),"SMB range exact");
  SmbBackend.Range tail=smb.range(url,-1,4);ok(new String(tail.data,"UTF-8").equals("wxyz"),"SMB tail");
  try(SmbBackend.Download dl=smb.download(url)){ok(dl.length==36&&new String(dl.stream.readAllBytes(),"UTF-8").equals("0123456789abcdefghijklmnopqrstuvwxyz"),"SMB complete stream");}
  try{new SmbBackend(root,"127.0.0.1",1445,"books","smbtest","wrong".toCharArray(),"").list(root);throw new AssertionError("wrong password");}catch(Exception expected){System.out.println("PASS bad password rejected");}
  try{smb.range("https://dav.example/Comic2/x.zip",0,3);throw new AssertionError("outside root accepted");}catch(IllegalArgumentException expected){System.out.println("PASS root confinement");}
 }
}
