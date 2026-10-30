package com.snowread.app;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class FeatureStorageTest {
    static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("FeatureStorage check "+checks);}
    interface Attempt{void run()throws Exception;}static void reject(Attempt attempt)throws Exception{boolean failed=false;try{attempt.run();}catch(Exception e){failed=true;}check(failed);}
    public static void main(String[] args)throws Exception{
        progressWrites(args[0]);noteTransactions(args[0]);
        File home=Files.createTempDirectory(Paths.get(args[0]),"features-").toFile();LibraryStore store=new LibraryStore(home);String body="Chapter 1 Snow\nThe snow falls at the window. A saved position here.\nChapter 2 Spring\nThe story grows in spring.";byte[] bytes=body.getBytes(StandardCharsets.UTF_8);LibraryStore.Book book=store.add(bytes,"[完结]《The Story》(1-1000章)作者：A.txt",null);check(book.title.equals("The Story")&&book.sourceName.endsWith("作者：A.txt"));TextDocument doc=store.load(book);int p=body.indexOf("saved position");book.annotations.add(BookNote.create(doc,p,p+14,"长久保留的笔记",0));book.offset=p;book.anchor=new ReadingAnchor.Resolver(doc).capture(p);book.marks.add(p);book.markNotes.put(p,"A note");book.markAnchors.put(p,book.anchor);book.typeset=new Typeset();book.typeset.customFont=String.join("",Collections.nCopies(64,"a"));book.typeset.font=28;book.typeset.margin=44;book.typeset.awake=true;book.typeset.indent=true;ReadingAnchor missing=new ReadingAnchor.Resolver(new TextDocument("第一章 缺失\n这是未定位书签原文，完整保留。","UTF-8")).capture(10);book.unmappedMarks.add(new ReadingAnchor.Unmapped(missing,"待定位备注"));store.save();String expected=store.metadata().toString();store=new LibraryStore(home);book=store.books.get(0);check(store.metadata().toString().equals(expected));check(book.annotations.size()==1&&book.annotations.get(0).note.equals("长久保留的笔记"));check(book.typeset.font==28&&book.typeset.awake&&book.typeset.indent);check(book.typeset.customFont.equals(String.join("",Collections.nCopies(64,"a"))));check(book.typeset.copy().customFont.equals(book.typeset.customFont));Typeset invalidFont=book.typeset.copy();invalidFont.customFont="../font";check(!invalidFont.valid());check(book.anchor.offset==p&&book.markAnchors.get(p).chapter.equals("Chapter 1 Snow")&&book.unmappedMarks.size()==1);
        ByteArrayOutputStream output=new ByteArrayOutputStream();BackupArchive.export(store,output,(d,t,n)->{});byte[] archive=output.toByteArray();LibraryStore empty=new LibraryStore(Files.createTempDirectory(Paths.get(args[0]),"features-restored-").toFile());try(BackupArchive.Prepared prepared=BackupArchive.prepare(new ByteArrayInputStream(archive),empty.directory().getParentFile(),(d,t,n)->{})){empty=BackupArchive.restore(prepared,empty,false,true,(d,t,n)->{});}check(empty.metadata().toString().equals(expected));
        LinkedHashMap<String,byte[]> bad=BackupAndImportTest.entries(archive);JSONObject index=new JSONObject(new String(bad.get("library/index.json"),StandardCharsets.UTF_8));index.getJSONArray("books").getJSONObject(0).getJSONObject("typeset").put("font",500);bad.put("library/index.json",index.toString().getBytes(StandardCharsets.UTF_8));byte[] attack=BackupAndImportTest.zip(bad);final File target=empty.directory().getParentFile();reject(()->BackupArchive.prepare(new ByteArrayInputStream(attack),target,(d,t,n)->{}));check(new LibraryStore(target).metadata().toString().equals(expected));
        TextDocument decoded=TextDocument.decode(bytes,"GB18030");PositionMigration plan=new PositionMigration(doc,decoded,p,book.anchor,book.marks,book.markNotes,book.markAnchors,book.unmappedMarks);File blocked=new File(store.directory(),"index.json.new");check(blocked.mkdir());final LibraryStore shelf=store;final LibraryStore.Book original=book;reject(()->shelf.reencode(original,decoded,plan));check(book.encoding.equals("UTF-8")&&book.offset==p&&book.markNotes.get(p).equals("A note")&&book.unmappedMarks.size()==1);check(blocked.delete());store.reencode(book,decoded,plan);check(new LibraryStore(home).books.get(0).encoding.equals("GB18030"));
        String changed="A new preface.\n\n"+body;TextDocument replacement=new TextDocument(changed,"UTF-8");PositionMigration replaced=new PositionMigration(decoded,replacement,book.offset,book.anchor,book.marks,book.markNotes,book.markAnchors,book.unmappedMarks);check(blocked.mkdir());reject(()->shelf.replaceContent(original,changed.getBytes(StandardCharsets.UTF_8),"new.txt",replacement,replaced));check(store.find(book.id)==book&&Arrays.equals(Files.readAllBytes(store.file(book).toPath()),bytes));check(blocked.delete());LibraryStore.Book next=store.replaceContent(book,changed.getBytes(StandardCharsets.UTF_8),"new.txt",replacement,replaced);check(!next.id.equals(book.id)&&store.find(book.id)==null&&store.books.size()==1);check(next.offset==changed.indexOf("saved position")&&next.typeset.font==28&&next.markNotes.containsValue("A note"));check(next.annotations.size()==1&&next.annotations.get(0).start==changed.indexOf("saved position")&&next.annotations.get(0).note.equals("长久保留的笔记"));check(next.sourceName.equals("new.txt")&&!store.file(book).exists());
        RecycleBin bin=new RecycleBin(home);bin.remove(store,next,System.currentTimeMillis());check(bin.bytes()>0&&bin.list(System.currentTimeMillis()).size()==1);LibraryStore.Book back=bin.restore(store,bin.list(System.currentTimeMillis()).get(0));check(back.annotations.size()==1&&back.annotations.get(0).quote.equals("saved position"));check(back.typeset.font==28&&back.unmappedMarks.size()==1);bin.remove(store,back,System.currentTimeMillis());File leftover=new File(new File(home,"recycle"),"pending-unfinished");check(leftover.mkdir());Files.write(new File(leftover,"raw-part").toPath(),new byte[]{1,2,3});check(bin.bytes()>3);bin.clear();check(bin.bytes()==0&&bin.list(System.currentTimeMillis()).isEmpty()&&store.books.isEmpty());
        testBulkCategory(store);
        testBulkRecycle(Files.createTempDirectory(Paths.get(args[0]),"bulk-").toFile());
        System.out.println("FeatureStorageTest: "+checks+" checks passed; optional metadata/backup, validation, encoding/replace rollback, recycle, bulk additive category commit/rollback");
    }
    private static void testBulkRecycle(File home)throws Exception{
        LibraryStore store=new LibraryStore(home);RecycleBin bin=new RecycleBin(home);
        File external=new File(home,"phone-original.txt");byte[] bytes="第一章 雪\n要保留原文件、书签和封面。".getBytes(StandardCharsets.UTF_8);Files.write(external.toPath(),bytes);
        LibraryStore.Book a=store.add(bytes,"甲.txt",null),b=store.add("第一章 春\n乙书正文".getBytes(StandardCharsets.UTF_8),"乙.txt",null),other=store.add("第一章 其他\n未选择的书".getBytes(StandardCharsets.UTF_8),"其他.txt",null);
        String category=store.organization.add("正在读");a.categoryIds.add(category);a.offset=6;a.marks.add(4);a.markNotes.put(4,"雪花备注");a.pinned=true;a.typeset=new Typeset();a.typeset.font=27;
        store.cover(a).getParentFile().mkdirs();byte[] cover={3,1,4,1,5};Files.write(store.cover(a).toPath(),cover);store.save();
        List<LibraryStore.Book> chosen=Arrays.asList(a,b,a);String before=store.metadata().toString();File index=new File(store.directory(),"index.json");byte[] disk=Files.readAllBytes(index.toPath());int commits=android.util.AtomicFile.finishesFor(index);
        File blocked=new File(store.directory(),"index.json.new");check(blocked.mkdir());reject(()->bin.removeAll(store,chosen,System.currentTimeMillis()));
        check(store.metadata().toString().equals(before)&&Arrays.equals(disk,Files.readAllBytes(index.toPath())));check(store.find(a.id)==a&&store.find(b.id)==b&&store.file(a).exists()&&store.cover(a).exists());check(bin.list(System.currentTimeMillis()).isEmpty()&&bin.bytes()==0);check(blocked.delete());
        File hidden=new File(store.directory(),"held.txt");check(store.file(b).renameTo(hidden));reject(()->bin.removeAll(store,chosen,System.currentTimeMillis()));check(store.metadata().toString().equals(before)&&bin.bytes()==0);check(store.find(a.id)==a&&store.file(a).exists()&&hidden.renameTo(store.file(b)));check(android.util.AtomicFile.finishesFor(index)==commits);
        ArrayList<RecycleBin.Entry> entries=bin.removeAll(store,chosen,System.currentTimeMillis());check(entries.size()==2&&bin.list(System.currentTimeMillis()).size()==2);check(android.util.AtomicFile.finishesFor(index)==commits+1);check(store.books.size()==1&&store.find(other.id)==other&&store.find(a.id)==null&&store.find(b.id)==null);check(!store.file(a).exists()&&!store.cover(a).exists()&&!store.file(b).exists());check(Arrays.equals(bytes,Files.readAllBytes(external.toPath())));check(new LibraryStore(home).books.size()==1&&new LibraryStore(home).find(other.id)!=null);
        for(RecycleBin.Entry entry:entries)bin.restore(store,entry);LibraryStore.Book restored=store.find(a.id);check(restored!=null&&restored.offset==6&&restored.pinned&&restored.typeset.font==27&&restored.markNotes.get(4).equals("雪花备注"));check(restored.categoryIds.contains(category)&&Arrays.equals(bytes,Files.readAllBytes(store.file(restored).toPath()))&&Arrays.equals(cover,Files.readAllBytes(store.cover(restored).toPath())));check(bin.bytes()==0&&store.find(b.id)!=null&&store.find(other.id)==other);
        commits=android.util.AtomicFile.finishesFor(index);check(bin.removeAll(store,Collections.emptyList(),System.currentTimeMillis()).isEmpty()&&android.util.AtomicFile.finishesFor(index)==commits);reject(()->bin.removeAll(store,Arrays.asList(a,store.find(b.id)),System.currentTimeMillis()));check(store.books.size()==3&&bin.bytes()==0);
    }
    private static void testBulkCategory(LibraryStore store)throws Exception{
        LibraryStore.Book a=store.add("第一章 甲\n甲书正文。".getBytes(StandardCharsets.UTF_8),"甲.txt",null);
        LibraryStore.Book b=store.add("第一章 乙\n乙书正文。".getBytes(StandardCharsets.UTF_8),"乙.txt",null);
        LibraryStore.Book other=store.add("第一章 丙\n丙书正文。".getBytes(StandardCharsets.UTF_8),"丙.txt",null);
        String old=store.organization.add("原分类"),target=store.organization.add("拖入分类"),retry=store.organization.add("失败分类");
        List<LibraryStore.Book> chosen=Arrays.asList(a,b);
        store.organization.assign(chosen,old);a.offset=3;a.marks.add(2);a.markNotes.put(2,"原备注");b.pinned=true;store.save();
        File index=new File(store.directory(),"index.json");int commits=android.util.AtomicFile.finishesFor(index);
        store.organize(()->store.organization.updateMembership(chosen,Collections.singleton(target),Collections.emptySet()));
        check(android.util.AtomicFile.finishesFor(index)==commits+1);
        check(a.categoryIds.equals(new LinkedHashSet<>(Arrays.asList(old,target)))&&b.categoryIds.equals(a.categoryIds));
        check(other.categoryIds.isEmpty());check(a.offset==3&&a.marks.equals(Collections.singletonList(2))&&a.markNotes.get(2).equals("原备注")&&b.pinned);
        LibraryStore restored=new LibraryStore(store.directory().getParentFile());check(restored.find(a.id).categoryIds.equals(a.categoryIds)&&restored.find(b.id).categoryIds.equals(b.categoryIds));
        byte[] before=Files.readAllBytes(index.toPath());String metadata=store.metadata().toString();File blocked=new File(store.directory(),"index.json.new");check(blocked.mkdir());
        reject(()->store.organize(()->store.organization.updateMembership(chosen,Collections.singleton(retry),Collections.emptySet())));
        check(store.metadata().toString().equals(metadata));check(Arrays.equals(before,Files.readAllBytes(index.toPath())));check(blocked.delete());
    }
    private static void noteTransactions(String root)throws Exception{
        File home=Files.createTempDirectory(Paths.get(root),"notes-").toFile();LibraryStore store=new LibraryStore(home);LibraryStore.Book book=store.add("第一章 春\n雪融之后，新的故事正在生长。".getBytes(StandardCharsets.UTF_8),"春.txt",null);TextDocument doc=store.load(book);BookNote note=BookNote.create(doc,6,12,"摘录感想",0);store.updateNote(book,null,note);check(new LibraryStore(home).find(book.id).annotations.size()==1);
        BookNote edited=note.edit("修改后的备注",1);String before=store.metadata().toString();File blocked=new File(store.directory(),"index.json.new");check(blocked.mkdir());reject(()->store.updateNote(book,note,edited));check(store.metadata().toString().equals(before));reject(()->store.updateNote(book,note,null));check(book.annotations.get(0)==note);check(blocked.delete());store.updateNote(book,note,edited);check(new LibraryStore(home).find(book.id).annotations.get(0).style==1);
        JSONObject duplicate=store.metadata().getJSONArray("books").getJSONObject(0);duplicate.getJSONArray("annotations").put(BookExtras.json(edited));LibraryStore.Book fake=new LibraryStore.Book(book.id,book.title,book.encoding,book.length);reject(()->BookExtras.read(fake,duplicate));JSONObject invalid=BookExtras.json(edited).put("end",99999);reject(()->BookExtras.annotation(invalid,book.length));
        store.updateNote(book,edited,null);check(new LibraryStore(home).find(book.id).annotations.isEmpty());reject(()->store.updateNote(book,edited,note));check(book.annotations.isEmpty());
    }
    private static void progressWrites(String root)throws Exception{
        File home=Files.createTempDirectory(Paths.get(root),"progress-").toFile();LibraryStore store=new LibraryStore(home);
        LibraryStore.Book b=store.add("第一章 测试\n这是用来验证多个保存世代的正文。".getBytes(StandardCharsets.UTF_8),"进度.txt",null);
        b.offset=3;store.dirtyProgress();LibraryStore.ProgressWrite stale=store.progressSnapshot();
        b.offset=9;store.dirtyProgress();store.writeProgress(stale);check(new LibraryStore(home).find(b.id).offset==0);
        store.writeProgress(store.progressSnapshot());check(new LibraryStore(home).find(b.id).offset==9);
        LibraryStore.ProgressWrite older=store.progressSnapshot();b.title="新标题";b.offset=12;store.save();store.writeProgress(older);
        check(new LibraryStore(home).find(b.id).title.equals("新标题"));check(new LibraryStore(home).find(b.id).offset==12);
        b.offset=18;store.dirtyProgress();LibraryStore.ProgressWrite beforeFailure=store.progressSnapshot();
        File blocked=new File(store.directory(),"index.json.new");check(blocked.mkdir());reject(()->store.save());check(blocked.delete());
        check(!store.writeProgress(beforeFailure));check(store.writeProgress(store.progressSnapshot()));check(new LibraryStore(home).find(b.id).offset==18);
        b.offset=15;store.dirtyProgress();LibraryStore.ProgressWrite retired=store.progressSnapshot();store.retireProgress();store.writeProgress(retired);check(new LibraryStore(home).find(b.id).offset==18);
    }

}
