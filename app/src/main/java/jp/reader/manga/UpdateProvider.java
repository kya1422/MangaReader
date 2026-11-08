package jp.reader.manga;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Exposes only the checked APK to Android's installer via a temporary URI grant. */
public final class UpdateProvider extends ContentProvider {
  static final String PATH="/MangaReader.apk";
  static File file(android.content.Context context){return new File(new File(context.getCacheDir(),"update"),"MangaReader.apk");}
  private File checked(Uri uri)throws FileNotFoundException {if(!PATH.equals(uri.getPath()))throw new FileNotFoundException();File f=file(getContext());if(!f.isFile())throw new FileNotFoundException();return f;}
  @Override public boolean onCreate(){return true;}
  @Override public String getType(Uri uri){return "application/vnd.android.package-archive";}
  @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(checked(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
  @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){try{File f=checked(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=f.getName();else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();}c.addRow(row);return c;}catch(FileNotFoundException e){return null;}}
  @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
  @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
  @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
}
