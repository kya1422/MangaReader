package jp.reader.manga;
import java.io.*;import java.util.function.Consumer;
final class Dav {
 byte[] data; Dav(byte[] x){data=x;}
 static final class Download implements Closeable {final InputStream stream;final long length;Download(InputStream s,long n){stream=s;length=n;}public void close()throws IOException{stream.close();}}
 Download openDownload(String u,Consumer<Closeable> register){InputStream in=new ByteArrayInputStream(data);register.accept(in);return new Download(in,data.length);}
}
