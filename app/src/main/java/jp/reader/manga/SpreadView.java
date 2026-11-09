package jp.reader.manga;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Draws a spread with the pages touching at the binding, without cropping. */
final class SpreadView extends View {
  private final Bitmap left,right;
  private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
  SpreadView(Context context,Bitmap left,Bitmap right){super(context);this.left=left;this.right=right;}
  @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);if(getWidth()==0||getHeight()==0)return;
    if(left==null){draw(canvas,right,0);return;}
    draw(canvas,left,-1);draw(canvas,right,1);
  }
  private void draw(Canvas canvas,Bitmap image,int side){
    if(image==null||image.isRecycled())return;
    SpreadGeometry.Box b=SpreadGeometry.fit(getWidth(),getHeight(),image.getWidth(),image.getHeight(),side);
    canvas.drawBitmap(image,null,new RectF(b.left,b.top,b.right,b.bottom),paint);
  }
}
