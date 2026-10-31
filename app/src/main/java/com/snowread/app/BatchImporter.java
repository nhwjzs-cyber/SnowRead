package com.snowread.app;

import java.io.*;
import java.util.*;

/** Stage additions in memory, then commit the shelf once; a failed final write rolls them back. */
final class BatchImporter {
    interface Source {String name() throws Exception;InputStream open() throws Exception;}
    interface Progress {void update(int completed,int total,String name);}
    interface Cancellation {boolean stopped();}
    static final class Result {
        int imported,duplicates,processed,total,rolledBack;
        String commitError;
        LibraryStore.Book firstImported;
        final ArrayList<String> failures=new ArrayList<>();
        /** Source indexes that failed, so a partial import can retry exactly those files. */
        final ArrayList<Integer> failedIndexes=new ArrayList<>();
        String summary(){return (commitError==null?"成功导入 "+imported+" 本":"未能保存书架，本次新增的 "+rolledBack+" 本已撤回\n"+commitError)+" · 已存在 "+duplicates+" 本\n文件失败 "+failures.size()+" 本"+(processed<total?" · 未处理 "+(total-processed)+" 本":"");}
    }
    static Result run(LibraryStore store,List<Source> sources,String category,String encoding,Progress progress,Cancellation cancellation) {
        Result result=new Result();result.total=sources.size();HashSet<String> known=new HashSet<>();for(LibraryStore.Book b:store.books)known.add(b.id);
        boolean finished=false;
        try {
        store.beginBatch();
        for(Source source:sources) {
            if(cancellation.stopped())break;
            String name="第 "+(result.processed+1)+" 个文件";
            try {
                name=source.name();progress.update(result.processed,result.total,name);
                if(!EpubDocument.supported(name))throw new IOException("请选择 TXT 或 EPUB 文件");
                byte[] bytes=TextDocument.readLimited(source.open());
                LibraryStore.Book book=store.add(bytes,name,encoding,category);
                if(known.add(book.id)){result.imported++;if(result.firstImported==null)result.firstImported=book;}else result.duplicates++;
            }catch(Exception e){result.failures.add(name+"\n"+(e.getMessage()==null?"无法读取文件":e.getMessage()));result.failedIndexes.add(result.processed);}
            result.processed++;progress.update(result.processed,result.total,name);
        }
        if(result.imported>0)progress.update(result.processed,result.total,"正在保存书架…");
        store.endBatch();finished=true;
        }catch(Exception e){result.firstImported=null;result.rolledBack=result.imported;result.imported=0;result.commitError=e.getMessage()==null?"导入未完成，请重试":e.getMessage();}
        finally{if(!finished)store.abortBatch();}
        return result;
    }
}
