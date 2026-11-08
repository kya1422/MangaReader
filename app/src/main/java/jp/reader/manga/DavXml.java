package jp.reader.manga;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Android-compatible WebDAV multistatus parser. DTDs and unknown entities are rejected. */
final class DavXml {
  static final class Entry { final String href; final boolean directory; Entry(String h,boolean d){href=h;directory=d;} }
  static List<Entry> parse(byte[] xml) throws Exception {
    XmlPullParserFactory factory=XmlPullParserFactory.newInstance();
    factory.setNamespaceAware(true);
    XmlPullParser parser=factory.newPullParser();
    parser.setInput(new ByteArrayInputStream(xml),null);
    List<Entry> result=new ArrayList<>();
    int responseDepth=-1,hrefDepth=-1; boolean directory=false; StringBuilder href=null;
    for(int type=parser.getEventType();type!=XmlPullParser.END_DOCUMENT;type=parser.nextToken()) {
      if(type==XmlPullParser.DOCDECL) throw new IOException("DOCTYPEを含むWebDAV応答は使用できません");
      if(type==XmlPullParser.ENTITY_REF && parser.getText()==null) throw new IOException("未対応のXMLエンティティです");
      if(type==XmlPullParser.START_TAG && "DAV:".equals(parser.getNamespace())) {
        String name=parser.getName();
        if(responseDepth<0 && "response".equals(name)){responseDepth=parser.getDepth();href=null;directory=false;}
        else if(responseDepth>0 && "href".equals(name) && parser.getDepth()==responseDepth+1 && href==null){href=new StringBuilder();hrefDepth=parser.getDepth();}
        else if(responseDepth>0 && "collection".equals(name))directory=true;
      } else if((type==XmlPullParser.TEXT || type==XmlPullParser.CDSECT || type==XmlPullParser.ENTITY_REF) && hrefDepth>0) {
        String value=parser.getText();if(value!=null)href.append(value);
      } else if(type==XmlPullParser.END_TAG){
        if(hrefDepth==parser.getDepth())hrefDepth=-1;
        if(responseDepth==parser.getDepth() && "DAV:".equals(parser.getNamespace()) && "response".equals(parser.getName())){if(href!=null)result.add(new Entry(href.toString(),directory));responseDepth=-1;}
      }
    }
    return result;
  }
}
