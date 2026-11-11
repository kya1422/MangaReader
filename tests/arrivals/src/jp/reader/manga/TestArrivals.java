package jp.reader.manga;
import java.time.*;import java.util.*;
class TestArrivals {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);System.out.println("PASS "+msg);}
 static Dav.Item file(String url,long at){return new Dav.Item(url,url.substring(url.lastIndexOf('/')+1),false,at);}
 public static void main(String[] args)throws Exception{
  long now=Instant.parse("2026-11-08T00:00:00Z").toEpochMilli(),cutoff=Instant.parse("2026-10-08T00:00:00Z").toEpochMilli();
  long valid=Instant.parse("2026-10-20T00:00:00Z").toEpochMilli(),valid2=Instant.parse("2026-11-03T00:00:00Z").toEpochMilli();
  check(NewArrivals.parseCreation("2026-11-03T09:00:00+09:00")==valid2,"creation date offset parsed");
  check(NewArrivals.parseCreation(null)==0&&NewArrivals.parseCreation("invalid")==0,"missing and invalid dates do not fall back");
  String root="https://example.com/books/",sub=root+"series/",hidden=root+"hidden/",error=root+"unreadable/";
  Map<String,List<Dav.Item>> folders=new HashMap<>();folders.put(root,Arrays.asList(new Dav.Item(sub,"series",true),new Dav.Item(hidden,"hidden",true),new Dav.Item(error,"unreadable",true),file(root+"missing.zip",0),file(root+"old.zip",cutoff-1),file(root+"future.zip",now+1),file(root+"boundary.zip",cutoff),file(root+"already.zip",valid2),file("https://evil.invalid/not-child.zip",valid2)));
  folders.put(sub,Arrays.asList(file(sub+"new.rar",valid2),file(sub+"second.cbz",valid),file(root+"stray.zip",valid2)));
  folders.put(hidden,Arrays.asList(file(hidden+"secret.zip",valid2)));
  Set<String> seenFolders=new HashSet<>();NewArrivals.Result r=NewArrivals.scan(root,url->{seenFolders.add(url);if(url.equals(error))throw new java.io.IOException("denied");return folders.get(url);},Collections.singleton(root+"already.zip"),Collections.singleton(hidden),now,cutoff,()->false,x->{});
  check(r.complete&&r.errors==1&&r.scanned==3,"recursive scan reports failed folder");
  NewArrivals.Result progress=new NewArrivals.Result(Collections.emptyList(),1,0,0,false,false);
  NewArrivals.Result good=new NewArrivals.Result(Collections.emptyList(),1,0,0,false,true);
  NewArrivals.Result limited=new NewArrivals.Result(Collections.emptyList(),1,0,0,true,true);
  NewArrivals.Result badRoot=new NewArrivals.Result(Collections.emptyList(),1,1,0,false,true);
  NewArrivals.Result manyErrors=new NewArrivals.Result(Collections.emptyList(),10,6,0,false,true);
  check(!ArrivalCachePolicy.cacheable(progress)&&ArrivalCachePolicy.cacheable(limited)&&ArrivalCachePolicy.cacheable(good),"cache completed scans but never interrupted scans");
  check(ArrivalCachePolicy.cacheable(r)&&!ArrivalCachePolicy.cacheable(badRoot)&&!ArrivalCachePolicy.cacheable(manyErrors),"few errors cache only if at least one folder succeeded");
  check(ArrivalCachePolicy.fresh(good,1000,1000+ArrivalCachePolicy.TTL-1)&&!ArrivalCachePolicy.fresh(good,1000,1000+ArrivalCachePolicy.TTL)&&!ArrivalCachePolicy.fresh(good,1000,999)&&!ArrivalCachePolicy.fresh(good,-1,100),"30 minute successful result TTL and boundary");
  check(ArrivalCachePolicy.fresh(r,1000,1000+ArrivalCachePolicy.ERROR_TTL-1)&&!ArrivalCachePolicy.fresh(r,1000,1000+ArrivalCachePolicy.ERROR_TTL)&&!ArrivalCachePolicy.fresh(progress,1000,1001),"five minute error TTL and no caching progress");
  check(ArrivalCachePolicy.fresh(limited,1000,1000+ArrivalCachePolicy.ERROR_TTL-1)&&!ArrivalCachePolicy.fresh(limited,1000,1000+ArrivalCachePolicy.ERROR_TTL),"limited completed scan expires in five minutes");
  check(!seenFolders.contains(hidden),"hidden folders are not scanned");
  check(r.unknown==1,"missing date reported");
  check(r.books.size()==3&&r.books.get(0).url.equals(sub+"new.rar")&&r.books.get(1).url.equals(sub+"second.cbz")&&r.books.get(2).url.endsWith("boundary.zip"),"unread, cutoff, direct children and descending date");
  check(r.books.get(0).folder.equals(sub)&&r.books.get(0).folderName.equals("series"),"result retains parent for opening");
  List<Dav.Item> many=new ArrayList<>();for(int i=0;i<NewArrivals.MAX_RESULTS+1;i++)many.add(file(root+"book"+i+".zip",valid+i));
  NewArrivals.Result capped=NewArrivals.scan(root,url->many,Collections.emptySet(),Collections.emptySet(),now,cutoff,()->false,x->{});
  check(ArrivalCachePolicy.cacheable(capped),"complete scan with result cap can be briefly reused");
  check(capped.limited&&capped.books.size()==NewArrivals.MAX_RESULTS&&capped.books.get(0).url.endsWith("book5000.zip")&&!capped.books.get(capped.books.size()-1).url.endsWith("book0.zip"),"result limit keeps newest and reports truncation");
  try{NewArrivals.scan(root,url->Collections.emptyList(),Collections.emptySet(),Collections.emptySet(),now,cutoff,()->true,x->{});throw new AssertionError("cancel ignored");}catch(java.io.IOException expected){System.out.println("PASS scan cancellation");}
 }
}
