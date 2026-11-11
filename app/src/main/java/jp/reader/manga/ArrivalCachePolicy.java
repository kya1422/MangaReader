package jp.reader.manga;
/** Only complete, error-free, non-truncated scans may be reused. Times are monotonic milliseconds. */
final class ArrivalCachePolicy {
  static final long TTL=30L*60*1000;
  static boolean cacheable(NewArrivals.Result result){return result!=null&&result.complete&&result.errors==0&&!result.limited;}
  static boolean fresh(long saved,long now){return saved>=0&&now>=saved&&now-saved<TTL;}
}
