package jp.reader.manga;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Evicts the oldest completed downloads, never the currently open book. */
final class CachePolicy {
  static File accessFile(File f){return new File(f.getParentFile(),f.getName()+".access");}
  static long accessTime(File f){File meta=accessFile(f);return meta.isFile()?meta.lastModified():f.lastModified();}
  static void touch(File f){try{File meta=accessFile(f);if(meta.isFile()||meta.createNewFile())meta.setLastModified(System.currentTimeMillis());}catch(IOException ignored){}}
  static void ensure(File[] files,long incoming,long limit,String pinnedName)throws IOException {
    if(incoming<0||incoming>limit)throw new IOException("保存容量の上限を超えます");
    List<File> candidates=new ArrayList<>();long used=0;
    if(files!=null)for(File f:files){if(!f.isFile()||!f.getName().endsWith(".zipcache"))continue;used+=f.length();if(!f.getName().equals(pinnedName))candidates.add(f);}
    candidates.sort(Comparator.comparingLong(CachePolicy::accessTime));
    for(File f:candidates){if(used<=limit-incoming)break;long size=f.length();if(f.delete()){used-=size;accessFile(f).delete();}}
    if(used>limit-incoming)throw new IOException("保存容量の上限に達しました。開いている本以外の空き領域がありません。");
  }
}
