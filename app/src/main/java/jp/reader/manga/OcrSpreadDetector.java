package jp.reader.manga;

import android.graphics.Bitmap;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.*;

/** Optional, explicitly invoked on-device OCR. Processes one page and one corner at a time. */
final class OcrSpreadDetector {
  interface Progress {void page(int done,int total);boolean cancelled();}
  static OcrSpreadInference.Result scan(RemoteZip zip,int start,Set<Integer> cuts,Progress progress)throws Exception {
    int end=Math.min(zip.pages.size(),start+12);
    List<OcrSpreadInference.Observation> observations=new ArrayList<>();Set<Integer> wide=new HashSet<>();boolean skipped=false;
    TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    try{
      for(int i=start;i<end;i++){
        if(progress.cancelled())throw new java.io.IOException("解析を中止しました");
        progress.page(i-start+1,end-start);RemoteZip.Page entry=zip.pages.get(i);
        if(entry.compressed>8L*1024*1024 || entry.uncompressed>8L*1024*1024){skipped=true;continue;}
        Bitmap page=zip.bitmap(i,700,1000);
        try{
          if(zip.isWide(i)){wide.add(i);continue;}
          Integer l=corner(recognizer,page,true,zip.pages.size()+100);
          if(progress.cancelled())throw new java.io.IOException("解析を中止しました");
          Integer r=corner(recognizer,page,false,zip.pages.size()+100);
          if((l!=null) != (r!=null))observations.add(new OcrSpreadInference.Observation(i,l!=null?l:r,l!=null));
        }finally{page.recycle();}
      }
    }finally{recognizer.close();}
    OcrSpreadInference.Result result=OcrSpreadInference.infer(observations,start,end,cuts,wide);
    return skipped?new OcrSpreadInference.Result(result.reliable,result.leftToRight,null,result.observations):result;
  }
  private static Integer corner(TextRecognizer recognizer,Bitmap page,boolean left,int maxPage)throws Exception {
    int w=Math.max(1,page.getWidth()/4),h=Math.max(1,page.getHeight()/5);
    Bitmap crop=Bitmap.createBitmap(page,left?0:page.getWidth()-w,page.getHeight()-h,w,h);
    Task<Text> task=null;
    try{
      task=recognizer.process(InputImage.fromBitmap(crop,0));Text text=Tasks.await(task);Integer result=null;
      for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines())for(Text.Element elem:line.getElements()){
        String value=elem.getText().trim();if(!value.matches("[0-9]{1,4}"))continue;
        int number=Integer.parseInt(value);if(number<1||number>maxPage)continue;
        if(result!=null && result!=number)return null;result=number;
      }
      return result;
    }finally{
      if(task==null || task.isComplete())crop.recycle();
      else task.addOnCompleteListener(ignored->crop.recycle());
    }
  }
}
