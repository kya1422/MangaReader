package jp.reader.manga;
/** Never reuse an interrupted scan. Explicitly labelled incomplete results get a short TTL. */
final class ArrivalCachePolicy {
  static final long TTL=30L*60*1000, ERROR_TTL=5L*60*1000;
  static final int MAX_ERRORS=5;
  static boolean cacheable(NewArrivals.Result result){
    return result!=null&&result.complete&&result.errors>=0&&result.errors<=MAX_ERRORS&&result.scanned>result.errors;
  }
  static boolean fresh(NewArrivals.Result result,long saved,long now){
    if(!cacheable(result)||saved<0||now<saved)return false;
    return now-saved<(result.errors==0&&!result.limited?TTL:ERROR_TTL);
  }
}
