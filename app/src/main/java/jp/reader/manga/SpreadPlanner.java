package jp.reader.manga;

import java.util.Set;

/** Computes the spread containing a page, including wide images and explicit breaks. */
final class SpreadPlanner {
  interface Orientation { boolean isWide(int index) throws Exception; }
  static boolean paired(int start,int count,Set<Integer> breaks,Orientation orientation) throws Exception {
    return !orientation.isWide(start) && start+1<count && !breaks.contains(start+1) && !orientation.isWide(start+1);
  }
  static int containing(int target,int count,Set<Integer> breaks,Orientation orientation) throws Exception {
    if(target<0 || target>=count)throw new IllegalArgumentException("ページ番号が範囲外です");
    for(int start=0;start<count;){
      if(start==target)return start;
      int next=start+1;
      if(paired(start,count,breaks,orientation))next++;
      if(target<next)return start;
      start=next;
    }
    throw new IllegalStateException("見開きを計算できません");
  }
}
