package jp.reader.manga;
class TestCoverMatch {
  static void ok(boolean x){if(!x)throw new AssertionError();}
  public static void main(String[] args){
    ok(CoverMatch.matchesFirstVolume("ONE PIECE","ONE PIECE 1"));
    ok(CoverMatch.matchesFirstVolume("ダンジョンに出会いを求めるのは間違っているだろうか","ダンジョンに出会いを求めるのは間違っているだろうか 1 (manga)"));
    ok(!CoverMatch.matchesFirstVolume("ONE PIECE","ONE PIECE 14"));
    ok(!CoverMatch.matchesFirstVolume("ONE PIECE","ONE PIECE PARTY 1"));
    ok(!CoverMatch.matchesFirstVolume("ダンジョンに出会いを求めるのは間違っているだろうか","ダンジョンに出会いを求めるのは間違っているだろうか 1 (light novel)"));
    System.out.println("PASS cover title and first volume matching");
  }
}
