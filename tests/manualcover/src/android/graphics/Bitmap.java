package android.graphics;
import java.io.*;
public class Bitmap {
 public enum Config{RGB_565} public enum CompressFormat{JPEG} private int w,h; public boolean recycled;
 public Bitmap(int w,int h){this.w=w;this.h=h;}public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h);}
 public int getWidth(){return w;}public int getHeight(){return h;}public void recycle(){recycled=true;}
 public boolean compress(CompressFormat f,int q,OutputStream out){try{new DataOutputStream(out).writeInt(w);new DataOutputStream(out).writeInt(h);return true;}catch(IOException e){return false;}}
}
