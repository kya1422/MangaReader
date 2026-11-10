package jp.reader.manga;
import java.util.*;
class TestSpread {
 static void check(boolean c,String msg){if(!c)throw new AssertionError(msg);System.out.println("PASS "+msg);}
 public static void main(String[] args)throws Exception{
   boolean[] wide={true,false,false,false,false,true,false,false,false}; Set<Integer> cuts=new HashSet<>();
   SpreadPlanner.Orientation o=i->wide[i];
   check(SpreadPlanner.containing(0,wide.length,cuts,o)==0,"wide cover single");
   check(SpreadPlanner.containing(1,wide.length,cuts,o)==1 && SpreadPlanner.containing(2,wide.length,cuts,o)==1,"second page selects same spread");
   check(SpreadPlanner.containing(4,wide.length,cuts,o)==3,"next spread");
   check(SpreadPlanner.containing(5,wide.length,cuts,o)==5,"wide double-page single");
   check(SpreadPlanner.containing(7,wide.length,cuts,o)==6,"spread after wide page");
   cuts.add(2);check(SpreadPlanner.containing(1,wide.length,cuts,o)==1 && SpreadPlanner.containing(2,wide.length,cuts,o)==2 && SpreadPlanner.containing(3,wide.length,cuts,o)==2,"adjustment starts at chosen boundary");
   Set<Integer> prevCut=new HashSet<>();prevCut.add(3);
   boolean[] portrait=new boolean[8];
   check(SpreadPlanner.containing(4,8,prevCut,i->portrait[i])==3 && SpreadPlanner.containing(5,8,prevCut,i->portrait[i])==5,"previous page joins current after adjustment");
   check(SpreadPlanner.paired(1,wide.length,Collections.emptySet(),o),"two portrait pages use half width");
   check(!SpreadPlanner.paired(4,wide.length,Collections.emptySet(),o),"portrait before wide uses full width");
   check(!SpreadPlanner.paired(1,wide.length,cuts,o),"cut makes portrait full width");
   check(!SpreadPlanner.paired(8,wide.length,Collections.emptySet(),o),"last portrait uses full width");
   check(!SpreadPlanner.paired(0,wide.length,Collections.emptySet(),o),"wide page uses full width");
   Random random=new Random(7);
   for(int test=0;test<1000;test++){
     boolean[] dimensions=new boolean[1+random.nextInt(100)];Set<Integer> boundaries=new HashSet<>();
     for(int i=0;i<dimensions.length;i++){dimensions[i]=random.nextInt(5)==0;if(random.nextInt(9)==0)boundaries.add(i);}
     int[] expected=new int[dimensions.length];
     for(int start=0;start<dimensions.length;){int next=start+1;if(!dimensions[start]&&next<dimensions.length&&!boundaries.contains(next)&&!dimensions[next])next++;for(int i=start;i<next;i++)expected[i]=start;start=next;}
     for(int i=0;i<dimensions.length;i++){final int target=i;int actual=SpreadPlanner.containing(target,dimensions.length,boundaries,n->dimensions[n]);if(actual!=expected[i])throw new AssertionError("case "+test+" page "+i);}
   }
   System.out.println("PASS randomized spread/previous-page cases (1000 books)");
   System.out.println("Spread tests complete");
 }
}
