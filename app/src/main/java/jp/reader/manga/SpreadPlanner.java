package jp.reader.manga;

import java.util.Set;

/** Computes the spread containing a page, including wide images and explicit breaks. */
final class SpreadPlanner {
  interface Orientation { boolean isWide(int index) throws Exception; }
  static int containing(int target,int count,Set<Integer> breaks,Orientation orientation) throws Exception {
    if(target<0 || target>=count)throw new IllegalArgumentException("ページ番号が範囲外です");
    for(int start=0;start<count;){
      if(start==target)return start;
      int next=start+1;
      if(!orientation.isWide(start) && next<count && !breaks.contains(next) && !orientation.isWide(next))next++;
      if(target<next)return start;
      start=next;
    }
    throw new IllegalStateException("見開きを計算できません");
  }
}
