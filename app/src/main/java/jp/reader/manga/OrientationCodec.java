package jp.reader.manga;
import java.util.*;
/** Two bits per page; 0 unknown, 1 portrait, 2 landscape. */
final class OrientationCodec {
  static String encode(int count,Map<Integer,Boolean> values){
    if(count<0||count>65534)return "";
    byte[] b=new byte[(count+3)/4];
    for(Map.Entry<Integer,Boolean> e:values.entrySet()){int i=e.getKey();if(i>=0&&i<count&&e.getValue()!=null)b[i/4]|=(e.getValue()?2:1)<<((i%4)*2);}
    return Base64.getEncoder().encodeToString(b);
  }
  static Map<Integer,Boolean> decode(int count,String value){
    Map<Integer,Boolean> out=new HashMap<>();if(count<0||count>65534||value==null||value.length()>22000)return out;
    try{byte[] b=Base64.getDecoder().decode(value);if(b.length!=(count+3)/4)return out;
      for(int i=0;i<count;i++){int v=(b[i/4]>>((i%4)*2))&3;if(v==1||v==2)out.put(i,v==2);}
    }catch(IllegalArgumentException ignored){}return out;
  }
}
