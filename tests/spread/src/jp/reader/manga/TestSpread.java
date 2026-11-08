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
   System.out.println("Spread tests complete");
 }
}
