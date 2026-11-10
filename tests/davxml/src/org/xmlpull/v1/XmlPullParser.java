package org.xmlpull.v1;
import java.io.*;import javax.xml.stream.*;
public class XmlPullParser {
 public static final int START_DOCUMENT=0,END_DOCUMENT=1,START_TAG=2,END_TAG=3,TEXT=4,CDSECT=5,ENTITY_REF=6,DOCDECL=10;
 XMLStreamReader reader;int depth=0,type=START_DOCUMENT;boolean afterEnd;
 public void setInput(InputStream in,String enc)throws Exception{XMLInputFactory f=XMLInputFactory.newFactory();f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE,true);f.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES,false);reader=f.createXMLStreamReader(in);}
 public int getEventType(){return type;}public int getDepth(){return depth;}
 public String getNamespace(){return reader.hasName()?reader.getNamespaceURI():null;}public String getName(){return reader.hasName()?reader.getLocalName():null;}
 public String getText(){return reader.hasText()?reader.getText():null;}
 public int nextToken()throws Exception{
  if(afterEnd){depth--;afterEnd=false;}
  while(reader.hasNext()){
   switch(reader.next()){
    case XMLStreamConstants.START_ELEMENT:depth++;return type=START_TAG;
    case XMLStreamConstants.END_ELEMENT:afterEnd=true;return type=END_TAG;
    case XMLStreamConstants.CHARACTERS:case XMLStreamConstants.SPACE:return type=TEXT;
    case XMLStreamConstants.CDATA:return type=CDSECT;
    case XMLStreamConstants.ENTITY_REFERENCE:return type=ENTITY_REF;
    case XMLStreamConstants.DTD:return type=DOCDECL;
    case XMLStreamConstants.END_DOCUMENT:return type=END_DOCUMENT;
   }
  }return type=END_DOCUMENT;
 }
}
