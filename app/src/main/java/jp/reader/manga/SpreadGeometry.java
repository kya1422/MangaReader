package jp.reader.manga;

/** Fits pages without distortion; the inner edges meet at the screen center. */
final class SpreadGeometry {
  static final class Box {final float left,top,right,bottom;Box(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}
  static Box fit(int screenWidth,int screenHeight,int imageWidth,int imageHeight,int side){
    if(screenWidth<=0||screenHeight<=0||imageWidth<=0||imageHeight<=0)throw new IllegalArgumentException("Invalid dimensions");
    float maxWidth=side==0?screenWidth:screenWidth/2f;
    float scale=Math.min(maxWidth/imageWidth,(float)screenHeight/imageHeight);
    float width=imageWidth*scale,height=imageHeight*scale,center=screenWidth/2f,top=(screenHeight-height)/2f;
    float x=side<0?center-width:side>0?center:center-width/2f;
    return new Box(x,top,x+width,top+height);
  }
}
