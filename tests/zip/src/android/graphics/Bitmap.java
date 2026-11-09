package android.graphics;
public class Bitmap { public enum Config {RGB_565,ARGB_8888} final int w,h; public Bitmap(int w,int h){this.w=w;this.h=h;} public int getWidth(){return w;}public int getHeight(){return h;}public void recycle(){} }
