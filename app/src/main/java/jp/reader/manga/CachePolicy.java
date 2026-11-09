package jp.reader.manga;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Evicts the oldest completed downloads, never the currently open book. */
final class CachePolicy {
  static void ensure(File[] files,long incoming,long limit,String pinnedName)throws IOException {
    if(incoming<0||incoming>limit)throw new IOException("保存容量の上限を超えます");
    List<File> candidates=new ArrayList<>();long used=0;
    if(files!=null)for(File f:files){if(!f.isFile()||!f.getName().endsWith(".zipcache"))continue;used+=f.length();if(!f.getName().equals(pinnedName))candidates.add(f);}
    candidates.sort(Comparator.comparingLong(File::lastModified));
    for(File f:candidates){if(used<=limit-incoming)break;long size=f.length();if(f.delete())used-=size;}
    if(used>limit-incoming)throw new IOException("保存容量の上限に達しました。開いている本以外の空き領域がありません。");
  }
}
