package jp.reader.manga;
import java.nio.charset.StandardCharsets;import java.util.*;
class TestDavXml{
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);System.out.println("PASS "+m);}
 public static void main(String[] args)throws Exception{
  String xml="<d:multistatus xmlns:d='DAV:'><d:response><d:href>/a%20b/</d:href>"+
   "<d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:creationdate>2026-11-01T01:02:03Z</d:creationdate></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"+
   "<d:response><d:href>/book.zip</d:href><d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>"+
   "<d:propstat><d:prop><d:creationdate>2026-11-02T01:02:03Z</d:creationdate></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response></d:multistatus>";
  List<DavXml.Entry> e=DavXml.parse(xml.getBytes(StandardCharsets.UTF_8));check(e.size()==2,"two DAV responses");
  check(e.get(0).directory&&"/a%20b/".equals(e.get(0).href)&&"2026-11-01T01:02:03Z".equals(e.get(0).creationDate),"200 directory and creationdate read");
  check(!e.get(1).directory&&e.get(1).creationDate==null,"404 creationdate not used");
  String incomplete="<d:multistatus xmlns:d='DAV:'><d:response><d:href>/dir/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:creationdate>2026-11-01T01:02:03Z</d:creationdate></d:prop></d:propstat></d:response></d:multistatus>";
  DavXml.Entry missing=DavXml.parse(incomplete.getBytes(StandardCharsets.UTF_8)).get(0);
  check(missing.directory&&missing.creationDate==null,"missing status preserves directory but not unconfirmed creation date");
  try{DavXml.parse("<!DOCTYPE a [<!ENTITY x 'bad'>]><a>&x;</a>".getBytes(StandardCharsets.UTF_8));throw new AssertionError("doctype accepted");}catch(java.io.IOException expected){System.out.println("PASS doctype rejected");}
 }
}
