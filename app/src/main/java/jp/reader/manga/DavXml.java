package jp.reader.manga;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Android-compatible WebDAV multistatus parser. DTDs and unknown entities are rejected. */
final class DavXml {
  static final class Entry { final String href,creationDate; final boolean directory; Entry(String h,boolean d,String c){href=h;directory=d;creationDate=c;} }
  static List<Entry> parse(byte[] xml) throws Exception {
    XmlPullParserFactory factory=XmlPullParserFactory.newInstance();factory.setNamespaceAware(true);
    XmlPullParser parser=factory.newPullParser();parser.setInput(new ByteArrayInputStream(xml),null);
    List<Entry> result=new ArrayList<>();
    int responseDepth=-1,propstatDepth=-1,textDepth=-1;String textName=null;StringBuilder text=null,href=null;
    boolean directory=false,groupDirectory=false;String creation=null,groupCreation=null,groupStatus=null;
    for(int type=parser.getEventType();type!=XmlPullParser.END_DOCUMENT;type=parser.nextToken()) {
      if(type==XmlPullParser.DOCDECL)throw new IOException("DOCTYPEを含むWebDAV応答は使用できません");
      if(type==XmlPullParser.ENTITY_REF && parser.getText()==null)throw new IOException("未対応のXMLエンティティです");
      if(type==XmlPullParser.START_TAG && "DAV:".equals(parser.getNamespace())){
        String name=parser.getName();int depth=parser.getDepth();
        if(responseDepth<0&&"response".equals(name)){responseDepth=depth;href=null;directory=false;creation=null;}
        else if(responseDepth>0&&"propstat".equals(name)&&depth==responseDepth+1){propstatDepth=depth;groupDirectory=false;groupCreation=null;groupStatus=null;}
        else if(responseDepth>0&&"href".equals(name)&&depth==responseDepth+1&&href==null){textDepth=depth;textName="href";text=new StringBuilder();}
        else if(propstatDepth>0&&"status".equals(name)&&depth==propstatDepth+1){textDepth=depth;textName="status";text=new StringBuilder();}
        else if(propstatDepth>0&&"creationdate".equals(name)&&depth==propstatDepth+2){textDepth=depth;textName="creationdate";text=new StringBuilder();}
        else if(propstatDepth>0&&"collection".equals(name))groupDirectory=true;
      }else if((type==XmlPullParser.TEXT||type==XmlPullParser.CDSECT||type==XmlPullParser.ENTITY_REF)&&textDepth>0){String value=parser.getText();if(value!=null)text.append(value);}
      else if(type==XmlPullParser.END_TAG){
        int depth=parser.getDepth();String name=parser.getName();
        if(textDepth==depth){if("href".equals(textName))href=text;else if("status".equals(textName))groupStatus=text.toString().trim();else if("creationdate".equals(textName))groupCreation=text.toString().trim();textDepth=-1;textName=null;text=null;}
        if(propstatDepth==depth&&"DAV:".equals(parser.getNamespace())&&"propstat".equals(name)){
          if(groupStatus!=null&&groupStatus.matches("(?i)HTTP/\\S+\\s+2\\d\\d(?:\\s+.*)?")){directory|=groupDirectory;if(groupCreation!=null&&!groupCreation.isEmpty())creation=groupCreation;}
          // Some non-compliant servers omit propstat status: retain collection
          // classification, but never trust an unconfirmed creation date.
          else if(groupStatus==null&&groupDirectory)directory=true;
          propstatDepth=-1;
        }
        if(responseDepth==depth&&"DAV:".equals(parser.getNamespace())&&"response".equals(name)){if(href!=null)result.add(new Entry(href.toString(),directory,creation));responseDepth=-1;}
      }
    }
    return result;
  }
}
