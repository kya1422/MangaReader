package jp.reader.manga;
import java.util.*;
class TestOrientation {
 public static void main(String[] args){Map<Integer,Boolean> m=new HashMap<>();m.put(0,true);m.put(3,false);m.put(99,true);String s=OrientationCodec.encode(100,m);if(!m.equals(OrientationCodec.decode(100,s)))throw new AssertionError("roundtrip");if(OrientationCodec.decode(99,s).size()!=2)throw new AssertionError("truncated page count");if(!OrientationCodec.decode(101,s).isEmpty())throw new AssertionError("invalid length");if(!OrientationCodec.decode(100,"%%%bad").isEmpty())throw new AssertionError("bad base64");System.out.println("PASS compact orientation state and invalid input");}
}
