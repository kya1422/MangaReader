package jp.reader.manga;

import java.util.*;

/** Conservative inference based only on printed page number and corner placement. */
final class OcrSpreadInference {
  static final class Observation {
    final int index,number; final boolean left;
    Observation(int index,int number,boolean left){this.index=index;this.number=number;this.left=left;}
  }
  static final class Result {
    final boolean reliable,leftToRight; final Boolean toggleBreak; final int observations;
    Result(boolean ok,boolean l,Boolean t,int n){reliable=ok;leftToRight=l;toggleBreak=t;observations=n;}
  }
  static Result infer(List<Observation> observations,int start,int count,Set<Integer> cuts,Set<Integer> wide){
    int right=0,left=0;
    for(Observation o:observations){boolean odd=(o.number&1)==1;
      if((odd&&!o.left)||(!odd&&o.left))right++;else left++;}
    int n=right+left;
    if(n<4 || Math.max(right,left)*4<n*3 || Math.abs(right-left)<2)return new Result(false,false,null,n);
    boolean ltr=left>right;
    int[] normal=score(observations,start,count,cuts,wide,ltr,false);
    int[] changed=score(observations,start,count,cuts,wide,ltr,true);
    Boolean toggle=null;
    if(normal[1]>=4 && changed[1]>=4 && Math.abs(normal[0]-changed[0])>=2){
      int[] best=normal[0]>changed[0]?normal:changed;
      if(best[0]*4>=best[1]*3)toggle=changed[0]>normal[0];
    }
    return new Result(true,ltr,toggle,n);
  }
  private static int[] score(List<Observation> obs,int start,int count,Set<Integer> cuts,Set<Integer> wide,boolean ltr,boolean toggle){
    Map<Integer,Boolean> expected=new HashMap<>();
    for(int p=start;p<count;){int q=p+1;boolean boundary=cuts.contains(q);if(toggle && q==start+1)boundary=!boundary;
      if(!wide.contains(p)&&q<count&&!boundary&&!wide.contains(q)){expected.put(p,ltr);expected.put(q,!ltr);p+=2;}else p++;}
    int matches=0,tested=0;for(Observation o:obs){Boolean isLeft=expected.get(o.index);if(isLeft!=null){tested++;if(isLeft==o.left)matches++;}}
    return new int[]{matches,tested};
  }
}
