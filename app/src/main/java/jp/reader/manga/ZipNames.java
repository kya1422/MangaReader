package jp.reader.manga;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** Pragmatic legacy-name detection; archives without the UTF-8 flag are ambiguous. */
final class ZipNames {
  static String strict(byte[] data,int offset,int length,String charset){
    try{return Charset.forName(charset).newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data,offset,length)).toString();}catch(Exception e){return null;}
  }
  static String decode(byte[] data,int offset,int length,boolean utf8){
    if(utf8)return new String(data,offset,length,StandardCharsets.UTF_8);
    String asUtf8=strict(data,offset,length,"UTF-8");if(asUtf8!=null)return asUtf8;
    String sjis=strict(data,offset,length,"Shift_JIS");
    if(sjis!=null){int japanese=0;for(int i=0;i<sjis.length();i++){char c=sjis.charAt(i);if((c>=0x3040&&c<=0x30ff)||(c>=0x4e00&&c<=0x9fff))japanese++;}if(japanese>0)return sjis;}
    return new String(data,offset,length,Charset.forName("CP437"));
  }
}
