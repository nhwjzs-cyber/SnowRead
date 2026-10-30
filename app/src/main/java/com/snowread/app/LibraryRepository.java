package com.snowread.app;

import android.content.Context;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One process-wide store, one I/O queue, one shared decoded book session. No Activity references. */
final class LibraryRepository {
    interface Failure {void failed(Exception error);}
    private static LibraryRepository instance;
    static synchronized LibraryRepository get(Context context)throws Exception{if(instance==null)instance=new LibraryRepository(context.getApplicationContext());return instance;}
    private volatile LibraryStore store;
    volatile String listenOwner="";
    final ExecutorService io=Executors.newSingleThreadExecutor();
    private TextDocument cached;
    private String cachedId="",encoding="";
    private ReadingAnchor.Resolver resolver;
    private long dirtyRevision,savedRevision;
    private boolean saveQueued,saveScheduled;
    private Failure saveFailure;
    private final Runnable delayedSave=()->{synchronized(this){saveScheduled=false;}enqueueProgressSave();};
    private final Handler main=new Handler(Looper.getMainLooper());
    private LibraryRepository(Context context)throws Exception{store=new LibraryStore(context);}
    LibraryStore store(){return store;}
    boolean owns(String id){return id!=null&&!id.isEmpty()&&id.equals(listenOwner);}
    synchronized TextDocument load(LibraryStore.Book book)throws Exception{if(cached==null||!cachedId.equals(book.id)||!encoding.equals(book.encoding)){cached=null;resolver=null;cachedId=book.id;encoding=book.encoding;cached=store.load(book);resolver=new ReadingAnchor.Resolver(cached);if(book.anchor!=null&&book.anchor.offset==book.offset){int mapped=resolver.resolve(book.anchor);if(mapped>=0)book.offset=mapped;}}return cached;}
    synchronized ReadingAnchor anchor(LibraryStore.Book book,int offset){return resolver!=null&&cachedId.equals(book.id)&&encoding.equals(book.encoding)?resolver.capture(offset):null;}
    synchronized void adopt(LibraryStore.Book book,TextDocument doc){cachedId=book.id;encoding=book.encoding;cached=doc;resolver=new ReadingAnchor.Resolver(doc);}
    synchronized void invalidate(){cached=null;resolver=null;cachedId="";encoding="";}
    synchronized void replace(LibraryStore updated){store.retireProgress();store=updated;listenOwner="";invalidate();}
    void progress(LibraryStore.Book book,int offset,boolean force,Failure failure){
        int at=Math.max(0,Math.min(offset,Math.max(0,book.length-1)));ReadingAnchor location=anchor(book,at);synchronized(store){if(store.find(book.id)!=book)return;book.offset=at;if(location!=null)book.anchor=location;}
        requestProgressSave(force,failure);
    }
    void requestProgressSave(boolean force,Failure failure){
        store.dirtyProgress();
        synchronized(this){dirtyRevision++;saveFailure=failure;if(!force&&saveScheduled)return;saveScheduled=!force;}
        if(force){main.removeCallbacks(delayedSave);enqueueProgressSave();}else main.postDelayed(delayedSave,750);
    }
    private void enqueueProgressSave(){
        final long revision;final LibraryStore selected;
        synchronized(this){if(saveQueued||dirtyRevision==savedRevision)return;saveQueued=true;revision=dirtyRevision;selected=store;}
        io.execute(()->{
            Exception error=null;boolean retry=false;
            try{LibraryStore.ProgressWrite snapshot=selected.progressSnapshot();retry=!selected.writeProgress(snapshot);}catch(Exception e){error=e;}
            final Exception failed=error;final boolean stale=retry;
            main.post(()->{Failure listener;boolean again;
                synchronized(this){saveQueued=false;listener=saveFailure;if(failed==null&&!stale)savedRevision=revision;again=dirtyRevision!=revision||stale;if(!again)saveFailure=null;}
                if(failed!=null&&listener!=null)listener.failed(failed);
                if(again)enqueueProgressSave();
            });
        });
    }
}
