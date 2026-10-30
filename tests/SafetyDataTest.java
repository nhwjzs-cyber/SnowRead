package com.snowread.app;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class SafetyDataTest {
    static int checks;static Path workspace;
    interface Attempt{void run()throws Exception;}
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    static void reject(Attempt f)throws Exception{boolean failed=false;try{f.run();}catch(Exception e){failed=true;}check(failed);}
    static LibraryStore shelf()throws Exception{return new LibraryStore(Files.createTempDirectory(workspace,"safe-").toFile());}
    static LibraryStore.Book add(LibraryStore s,String name)throws Exception{return s.add(("第一章 "+name+"\n"+name+"的故事。😀").getBytes(StandardCharsets.UTF_8),name,null);}
    static byte[] export(LibraryStore s)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.export(s,out,(d,t,n)->{});return out.toByteArray();}
    static BackupArchive.Prepared prepare(byte[] zip,LibraryStore s)throws Exception{return BackupArchive.prepare(new ByteArrayInputStream(zip),s.directory().getParentFile(),(d,t,n)->{});}
    static byte[] withNotes(byte[] zip,Object notes)throws Exception{LinkedHashMap<String,byte[]> files=BackupAndImportTest.entries(zip);JSONObject index=new JSONObject(new String(files.get("library/index.json"),StandardCharsets.UTF_8));index.getJSONArray("books").getJSONObject(0).put("markNotes",notes);files.put("library/index.json",index.toString().getBytes(StandardCharsets.UTF_8));return BackupAndImportTest.zip(files);}
    static void assertNoStaging(LibraryStore s){for(File f:s.directory().getParentFile().listFiles())check(!f.getName().startsWith("restore-"));}
    public static void main(String[] args)throws Exception{
        workspace=Paths.get(args[0]);LibraryStore source=shelf();LibraryStore.Book a=add(source,"雪");String category=source.organization.add("正在追");a.categoryIds.add(category);a.offset=5;a.marks.add(2);a.markNotes.put(2,"喜欢这里的雪花 🌱");a.pinned=true;source.save();File cover=source.cover(a);cover.getParentFile().mkdirs();Files.write(cover.toPath(),new byte[]{1,2,3,4});byte[] zip=export(source);
        LibraryStore target=shelf();LibraryStore.Book extra=add(target,"本机独有");String cat=target.organization.add("本机分类");extra.categoryIds.add(cat);target.save();
        try(BackupArchive.Prepared prepared=prepare(zip,target)){target=BackupArchive.restore(prepared,target,false,true,(d,t,n)->{});}
        check(target.books.size()==1&&target.find(extra.id)==null);check(!target.file(extra).exists());check(target.organization.categories.equals(source.organization.categories));check(target.metadata().toString().equals(source.metadata().toString()));check(Arrays.equals(Files.readAllBytes(target.cover(target.find(a.id)).toPath()),new byte[]{1,2,3,4}));
        final LibraryStore live=target;String before=live.metadata().toString();
        byte[] badType=withNotes(zip,"invalid");reject(()->prepare(badType,live));byte[] wrongOffset=withNotes(zip,new JSONObject().put("99999","note"));reject(()->prepare(wrongOffset,live));byte[] longNote=withNotes(zip,new JSONObject().put("2",String.join("",Collections.nCopies(1001,"字"))));reject(()->prepare(longNote,live));byte[] numericNote=withNotes(zip,new JSONObject().put("2",5));reject(()->prepare(numericNote,live));check(live.metadata().toString().equals(before));assertNoStaging(live);
        TaskControl cancelled=new TaskControl((d,t,n)->{});cancelled.cancel();reject(()->BackupArchive.prepare(new ByteArrayInputStream(zip),live.directory().getParentFile(),cancelled));assertNoStaging(live);
        TaskControl[] token={null};token[0]=new TaskControl((d,t,n)->token[0].cancel());try(BackupArchive.Prepared p=prepare(zip,live)){reject(()->BackupArchive.restore(p,live,false,true,token[0]));}check(new LibraryStore(live.directory().getParentFile()).metadata().toString().equals(before));assertNoStaging(live);
        // Cancellation on the final commit boundary still leaves the original directory intact.
        try(BackupArchive.Prepared p=prepare(zip,live)){BackupArchive.Progress finalCancel=new BackupArchive.Progress(){public void update(int d,int t,String n){}public void beginCommit(){throw new TaskControl.Cancelled();}};reject(()->BackupArchive.restore(p,live,false,true,finalCancel));}check(live.metadata().toString().equals(before));assertNoStaging(live);
        TaskControl commit=new TaskControl((d,t,n)->{});commit.beginCommit();check(!commit.cancel());commit.checkCancelled();check(commit.committing());
        // Cancel a large copy during the first chunk and stop without writing the remaining bytes.
        ByteArrayOutputStream partial=new ByteArrayOutputStream();TaskControl[] copying={null};copying[0]=new TaskControl((d,t,n)->copying[0].cancel());reject(()->BackupArchive.copyTracked(new ByteArrayInputStream(new byte[700000]),partial,700000,copying[0],0,700000,"copy"));check(partial.size()<700000);
        // Restore an empty snapshot exactly, including removing all categories and covers.
        LibraryStore empty=shelf();empty.save();try(BackupArchive.Prepared p=prepare(export(empty),live)){LibraryStore replaced=BackupArchive.restore(p,live,false,true,(d,t,n)->{});check(replaced.books.isEmpty()&&replaced.organization.categories.isEmpty());check(!replaced.cover(a).exists());}
        // Durable recycle across process recreation, complete reading metadata and cover.
        RecycleBin bin=new RecycleBin(source.directory().getParentFile());long now=System.currentTimeMillis();RecycleBin.Entry entry=bin.remove(source,a,now);check(source.books.isEmpty()&&!source.file(a).exists()&&!source.cover(a).exists());check(bin.list(now).size()==1);source=new LibraryStore(source.directory().getParentFile());bin=new RecycleBin(source.directory().getParentFile());entry=bin.list(now+1000).get(0);check(entry.book.markNotes.get(2).equals("喜欢这里的雪花 🌱"));source.organization.categories.put(category,"同编号不同分类");source.save();LibraryStore.Book restored=bin.restore(source,entry);check(restored.offset==5&&restored.pinned&&restored.marks.equals(a.marks)&&restored.markNotes.equals(a.markNotes));check(restored.categoryIds.size()==1&&!restored.categoryIds.contains(category));check(source.organization.categories.containsValue("正在追"));check(Arrays.equals(Files.readAllBytes(source.cover(restored).toPath()),new byte[]{1,2,3,4}));check(bin.list(now).isEmpty());check(new LibraryStore(source.directory().getParentFile()).find(a.id).markNotes.get(2).equals("喜欢这里的雪花 🌱"));
        // A failed live index write must keep both the live book and its files.
        File block=new File(source.directory(),"index.json.new");check(block.mkdir());final LibraryStore failShelf=source;final RecycleBin failBin=bin;reject(()->failBin.remove(failShelf,restored,now));check(source.find(a.id)==restored&&source.file(restored).isFile()&&source.cover(restored).isFile());check(bin.list(now).isEmpty());check(block.delete());
        entry=bin.remove(source,restored,now);source.add(Files.readAllBytes(entry.snapshot.file(entry.book).toPath()),"重新导入",null);final RecycleBin duplicateBin=bin;final LibraryStore duplicateShelf=source;final RecycleBin.Entry duplicateEntry=entry;reject(()->duplicateBin.restore(duplicateShelf,duplicateEntry));check(bin.list(now).size()==1);check(source.books.size()==1);check(bin.list(now+RecycleBin.RETENTION).isEmpty());
        System.out.println("SafetyDataTest: "+checks+" checks passed; exact restore, cancellation, durable recycle, notes, collisions and write failures");
    }
}
