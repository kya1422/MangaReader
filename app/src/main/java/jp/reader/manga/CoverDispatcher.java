package jp.reader.manga;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** Cache hits never wait behind a slow WebDAV request. One network request at a time. */
final class CoverDispatcher {
  interface Work<T>{T run() throws Exception;}
  interface Result<T>{void done(T value,Exception error);}
  private final ExecutorService cache=Executors.newSingleThreadExecutor();
  private final ExecutorService network=Executors.newSingleThreadExecutor();
  <T> void submit(BooleanSupplier cancelled,Work<T> cached,Work<T> fetch,Runnable fetching,Result<T> result){
    cache.execute(()->{
      try{
        if(cancelled.getAsBoolean()){result.done(null,null);return;}
        T value=cached.run();
        if(value!=null||cancelled.getAsBoolean()){result.done(value,null);return;}
        network.execute(()->{
          try{if(cancelled.getAsBoolean()){result.done(null,null);return;}fetching.run();result.done(fetch.run(),null);}
          catch(Exception e){result.done(null,e);}
        });
      }catch(Exception e){result.done(null,e);}
    });
  }
  void executeCache(Runnable task){cache.execute(task);}
  void shutdownNow(){cache.shutdownNow();network.shutdownNow();}
}
