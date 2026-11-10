package jp.reader.manga;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Fixed-height frame: drag horizontally without changing its size or aspect ratio. */
final class CoverCropView extends View {
  private Bitmap image;
  private final Paint imagePaint=new Paint(Paint.FILTER_BITMAP_FLAG),shade=new Paint(),border=new Paint(Paint.ANTI_ALIAS_FLAG);
  private int position=0; private float downX,initialPosition,range;
  CoverCropView(Context ctx,Bitmap preview){
    super(ctx);image=preview;shade.setColor(0x99000000);border.setColor(Color.WHITE);border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(Math.max(3,getResources().getDisplayMetrics().density*3));setBackgroundColor(Color.DKGRAY);
  }
  int position(){return position;}
  void release(){Bitmap old=image;image=null;invalidate();if(old!=null)old.recycle();}
  @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);if(image==null||image.isRecycled()||getWidth()<=0||getHeight()<=0)return;
    float scale=Math.min((float)getWidth()/image.getWidth(),(float)getHeight()/image.getHeight());
    float w=image.getWidth()*scale,h=image.getHeight()*scale,x=(getWidth()-w)/2f,y=(getHeight()-h)/2f;
    canvas.drawBitmap(image,null,new RectF(x,y,x+w,y+h),imagePaint);
    float frame=CoverCrop.manualWidth(image.getWidth(),image.getHeight())*scale;range=Math.max(0,w-frame);
    float left=x+range*position/10000f;
    canvas.drawRect(x,y,left,y+h,shade);canvas.drawRect(left+frame,y,x+w,y+h,shade);
    canvas.drawRect(left,y,left+frame,y+h,border);
  }
  @Override public boolean onTouchEvent(MotionEvent e){
    if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();initialPosition=position;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;}
    if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(range>0)position=Math.max(0,Math.min(10000,Math.round(initialPosition+(e.getX()-downX)*10000f/range)));invalidate();return true;}
    return e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL;
  }
}
