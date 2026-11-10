package android.graphics;
import java.io.*;
public class BitmapFactory{public static Bitmap decodeFile(String path){try(DataInputStream in=new DataInputStream(new FileInputStream(path))){return new Bitmap(in.readInt(),in.readInt());}catch(IOException e){return null;}}}
