package android.content;
import java.io.File;import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0; private final File dir; private final Map<String,Object> data=new HashMap<>();
 public Context(File f){dir=f;} public File getFilesDir(){return dir;}
 public SharedPreferences getSharedPreferences(String n,int mode){return new SharedPreferences(){
  public String getString(String k,String d){return (String)data.getOrDefault(k,d);}public int getInt(String k,int d){return (Integer)data.getOrDefault(k,d);}
  public Editor edit(){return new Editor(){Map<String,Object> writes=new HashMap<>();Set<String> removed=new HashSet<>();public Editor putString(String k,String v){writes.put(k,v);return this;}public Editor putInt(String k,int v){writes.put(k,v);return this;}public Editor remove(String k){removed.add(k);return this;}public boolean commit(){removed.forEach(data::remove);data.putAll(writes);return true;}};}
 };}
}
