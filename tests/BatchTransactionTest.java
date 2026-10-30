package com.snowread.app;

import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Count real index writes and exercise failures, rather than asserting elapsed-time thresholds. */
public final class BatchTransactionTest {
    private static int checks;
    private static Path workspace;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static LibraryStore fresh()throws Exception{return new LibraryStore(Files.createTempDirectory(workspace,"batch-").toFile());}
    private static File index(LibraryStore store){return new File(store.directory(),"index.json");}
    private static byte[] indexBytes(LibraryStore store)throws Exception{return Files.readAllBytes(index(store).toPath());}
    private static long raws(LibraryStore store){File[] files=store.directory().listFiles((dir,name)->name.matches("[a-f0-9]{64}\\.txt(?:\\.new|\\.bak)?"));return files==null?0:files.length;}
    private static BatchImporter.Source source(String name,String text){return new BatchImporter.Source(){public String name(){return name;}public InputStream open(){return new ByteArrayInputStream(bytes(text));}};}
    private static BatchImporter.Result run(LibraryStore store,List<BatchImporter.Source> inputs){return BatchImporter.run(store,inputs,"",null,(d,t,n)->{},()->false);}
    public static void main(String[] args)throws Exception {
        workspace=Paths.get(args[0]);
        LibraryStore store=fresh();String category=store.organization.add("原有分类");
        String oldText="第一章 原书\n这是原来已经在书架上的正文。";
        LibraryStore.Book old=store.add(bytes(oldText),"原书",null,category);old.offset=6;old.pinned=true;old.marks.add(4);store.save();
        File cover=store.cover(old);check(cover.getParentFile().mkdirs());Files.write(cover.toPath(),bytes("original-cover"));
        int starts=AtomicFile.startsFor(index(store)),finishes=AtomicFile.finishesFor(index(store));
        ArrayList<BatchImporter.Source> many=new ArrayList<>();for(int i=0;i<500;i++)many.add(source("批次"+i+".txt","第一章 第 "+i+" 本\n这一册有唯一内容 "+i+"。"));
        long began=System.nanoTime();BatchImporter.Result report=run(store,many);long millis=(System.nanoTime()-began)/1000000;
        check(report.commitError==null&&report.imported==500&&report.processed==500&&report.failures.isEmpty());
        check(AtomicFile.startsFor(index(store))-starts==1);check(AtomicFile.finishesFor(index(store))-finishes==1);check(store.books.size()==501&&raws(store)==501);
        check(store.books.get(0).title.equals("批次499")&&store.books.get(499).title.equals("批次0")&&store.books.get(500)==old);
        LibraryStore reloaded=new LibraryStore(store.directory().getParentFile());check(reloaded.books.size()==501);check(reloaded.books.get(500).offset==6&&reloaded.books.get(500).marks.equals(Collections.singletonList(4)));check(old.categoryIds.contains(category)&&old.pinned);check(new String(Files.readAllBytes(cover.toPath()),StandardCharsets.UTF_8).equals("original-cover"));
        // Re-import duplicates without parsing a forced, invalid encoding; do not write an index.
        starts=AtomicFile.startsFor(index(store));finishes=AtomicFile.finishesFor(index(store));
        ArrayList<BatchImporter.Source> duplicates=new ArrayList<>();for(int i=0;i<500;i++)duplicates.add(source("不同名字"+i+".txt",oldText));
        report=BatchImporter.run(store,duplicates,"","NOT-A-CHARSET",(d,t,n)->{},()->false);
        check(report.imported==0&&report.duplicates==500&&report.failures.isEmpty()&&report.commitError==null);check(AtomicFile.startsFor(index(store))==starts&&AtomicFile.finishesFor(index(store))==finishes);
        check(store.add(bytes(oldText),"不会改名","NOT-A-CHARSET")==old&&old.offset==6);
        // A failed final commit removes every new raw file and restores identity/order and old data.
        ArrayList<LibraryStore.Book> before=new ArrayList<>(store.books);byte[] beforeIndex=indexBytes(store);String beforeMeta=store.metadata().toString();
        File blocked=new File(store.directory(),"index.json.new");check(blocked.mkdir());starts=AtomicFile.startsFor(index(store));finishes=AtomicFile.finishesFor(index(store));
        report=run(store,Arrays.asList(source("新甲.txt","第一章 新甲\n只有失败批次有的甲正文。"),source("旧书.txt",oldText),source("新乙.txt","第一章 新乙\n只有失败批次有的乙正文。"),source("错误.pdf","not txt")));
        check(report.commitError!=null&&report.imported==0&&report.rolledBack==2&&report.duplicates==1&&report.failures.size()==1);check(report.firstImported==null);check(report.summary().contains("已撤回"));
        check(AtomicFile.startsFor(index(store))==starts+1&&AtomicFile.finishesFor(index(store))==finishes);check(store.books.equals(before)&&store.metadata().toString().equals(beforeMeta));check(Arrays.equals(indexBytes(store),beforeIndex));check(raws(store)==501);check(store.file(old).isFile()&&cover.isFile());
        check(new LibraryStore(store.directory().getParentFile()).books.size()==501);Files.delete(blocked.toPath());
        // Aborted identities cannot remain cached; the next import must create and commit the book.
        starts=AtomicFile.startsFor(index(store));report=run(store,Collections.singletonList(source("重新导入甲.txt","第一章 新甲\n只有失败批次有的甲正文。")));check(report.imported==1&&report.commitError==null);check(AtomicFile.startsFor(index(store))==starts+1&&raws(store)==502);
        // Nested scopes and explicit save defer all index I/O until the outer scope completes.
        LibraryStore nested=fresh();LibraryStore.Book original=nested.add(bytes(oldText),"原书",null);starts=AtomicFile.startsFor(index(nested));byte[] originalIndex=indexBytes(nested);
        nested.beginBatch();LibraryStore.Book first=nested.add(bytes("第一章 嵌套甲\n甲正文。"),"甲",null);nested.beginBatch();LibraryStore.Book second=nested.add(bytes("第一章 嵌套乙\n乙正文。"),"乙",null);nested.save();nested.endBatch();
        check(AtomicFile.startsFor(index(nested))==starts&&Arrays.equals(originalIndex,indexBytes(nested)));check(new LibraryStore(nested.directory().getParentFile()).books.size()==1);nested.endBatch();
        check(AtomicFile.startsFor(index(nested))==starts+1);check(nested.books.equals(Arrays.asList(second,first,original)));check(new LibraryStore(nested.directory().getParentFile()).books.size()==3);
        boolean underflow=false;try{nested.endBatch();}catch(IllegalStateException expected){underflow=true;}check(underflow);
        starts=AtomicFile.startsFor(index(nested));nested.beginBatch();nested.endBatch();check(AtomicFile.startsFor(index(nested))==starts);
        // Early stop commits the successful prefix once. Stopping before the first file writes nothing.
        LibraryStore stopped=fresh();final boolean[] stop={false};report=BatchImporter.run(stopped,many,"",null,(d,t,n)->{if(d==2)stop[0]=true;},()->stop[0]);check(report.imported==2&&report.processed==2&&report.total==500&&report.commitError==null);check(AtomicFile.finishesFor(index(stopped))==1&&new LibraryStore(stopped.directory().getParentFile()).books.size()==2);
        LibraryStore untouched=fresh();report=BatchImporter.run(untouched,many,"",null,(d,t,n)->{},()->true);check(report.processed==0&&AtomicFile.startsFor(index(untouched))==0&&raws(untouched)==0);
        report=run(untouched,Arrays.asList(source("坏.pdf","no"),source("空.txt","")));check(report.failures.size()==2&&report.commitError==null&&AtomicFile.startsFor(index(untouched))==0);
        // An unexpected callback exception aborts the transaction, not the UI worker's finally path.
        report=BatchImporter.run(untouched,many,"",null,(d,t,n)->{if(d==1)throw new IllegalStateException("progress unavailable");},()->false);check(report.commitError!=null&&report.rolledBack==1&&report.imported==0);check(untouched.books.isEmpty()&&raws(untouched)==0&&AtomicFile.startsFor(index(untouched))==0);
        // Single-file store calls still commit immediately and remove/re-import keeps the ID map correct.
        LibraryStore single=fresh();LibraryStore.Book book=single.add(bytes(oldText),"单本",null);check(AtomicFile.finishesFor(index(single))==1&&new LibraryStore(single.directory().getParentFile()).books.size()==1);single.remove(book);check(single.books.isEmpty());LibraryStore.Book again=single.add(bytes(oldText),"再次导入",null);check(again!=book&&single.books.size()==1&&single.file(again).isFile());
        System.out.println("BatchTransactionTest: "+checks+" checks passed; 500 files -> one index commit (host "+millis+"ms), rollback, nesting, stop and duplicate lookup");
    }
}
