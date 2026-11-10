package jp.reader.manga;
class TestCoverWindow {
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);}
 public static void main(String[] args){
  check(CoverWindow.contains(100,200,0,100),"preload just below viewport");
  check(!CoverWindow.contains(200,300,0,100),"distant rows not loaded");
  check(CoverWindow.contains(4300,4400,4400,400),"rows ahead and behind viewport stay loaded");
  check(!CoverWindow.contains(3900,4000,4400,400),"rows far behind viewport discarded");
  for(int row=0;row<200;row++)if(!CoverWindow.contains(row*100,row*100+100,row*100,500))throw new AssertionError("row "+row);
  System.out.println("PASS all 200 rows reachable without a count limit");
  check(!CoverWindow.contains(0,0,0,100)&&!CoverWindow.contains(0,100,0,0),"unlaid-out rows ignored");
 }
}
