package jp.reader.manga;
import java.util.*;
class TestOcrInference {
 static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);System.out.println("PASS "+msg);}
 public static void main(String[] args){
  List<OcrSpreadInference.Observation> obs=new ArrayList<>();
  for(int i=0;i<8;i++)obs.add(new OcrSpreadInference.Observation(i,i+1,(i%2)==1));
  OcrSpreadInference.Result r=OcrSpreadInference.infer(obs,0,8,new HashSet<>(),new HashSet<>());
  ok(r.reliable&&!r.leftToRight&&Boolean.FALSE.equals(r.toggleBreak),"右開きと正しい見開き");
  Set<Integer> breaks=new HashSet<>();breaks.add(1);
  r=OcrSpreadInference.infer(obs,0,8,breaks,new HashSet<>());
  ok(r.reliable&&!r.leftToRight&&Boolean.TRUE.equals(r.toggleBreak),"見開きずれの候補");
  obs.clear();for(int i=0;i<8;i++)obs.add(new OcrSpreadInference.Observation(i,i+1,(i%2)==0));
  r=OcrSpreadInference.infer(obs,0,8,new HashSet<>(),new HashSet<>());
  ok(r.reliable&&r.leftToRight,"左開きの候補");
  r=OcrSpreadInference.infer(obs.subList(0,2),0,8,new HashSet<>(),new HashSet<>());
  ok(!r.reliable,"情報不足時は推定しない");
  obs.clear();for(int i=0;i<8;i++)obs.add(new OcrSpreadInference.Observation(i,i+1,false));
  r=OcrSpreadInference.infer(obs,0,8,new HashSet<>(),new HashSet<>());
  ok(!r.reliable,"矛盾する観測は推定しない");
 }
}
