package com.snowread.app;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

public final class EpubStorageTest {
    static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("EPUB storage "+checks);}
    static LibraryStore fresh(Path root)throws Exception{return new LibraryStore(Files.createTempDirectory(root,"epub-").toFile());}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]);byte[] bytes=EpubDocumentTest.zip(EpubDocumentTest.files(false));LibraryStore store=fresh(root);
        LibraryStore.Book book=store.add(bytes,"file.epub",null);check(book.epub());check(book.title.equals("融雪测试书"));check(store.cover(book).isFile());check(Arrays.equals(bytes,Files.readAllBytes(store.file(book).toPath())));
        TextDocument doc=store.load(book);book.offset=doc.chapters.get(1).offset;book.marks.add(book.offset);book.scriptMode=1;book.annotations.add(BookNote.create(doc,0,3,"注释",0));store.save();
        LibraryStore reloaded=new LibraryStore(store.directory().getParentFile());LibraryStore.Book record=reloaded.find(book.id);check(record.epub()&&record.offset==book.offset&&record.scriptMode==1);check(reloaded.load(record).text.equals(doc.text));check(reloaded.add(bytes,"again.epub",null)==record);
        ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.export(reloaded,out,(a,b,c)->{});LibraryStore target=fresh(root);
        try(BackupArchive.Prepared backup=BackupArchive.prepare(new ByteArrayInputStream(out.toByteArray()),target.directory().getParentFile(),(a,b,c)->{})){
            LibraryStore restored=BackupArchive.restore(backup,target,true,(a,b,c)->{});LibraryStore.Book copy=restored.find(book.id);
            check(copy.epub()&&copy.offset==book.offset&&copy.marks.equals(book.marks));check(copy.annotations.size()==1);check(restored.cover(copy).isFile());check(Arrays.equals(bytes,Files.readAllBytes(restored.file(copy).toPath())));check(restored.load(copy).text.equals(doc.text));
        }
        RecycleBin bin=new RecycleBin(store.directory().getParentFile());RecycleBin.Entry removed=bin.remove(store,book,System.currentTimeMillis());check(store.find(book.id)==null);LibraryStore.Book returned=bin.restore(store,removed);check(returned.epub()&&store.load(returned).text.equals(doc.text));check(returned.annotations.size()==1&&store.cover(returned).isFile());
        LibraryStore aborted=fresh(root);aborted.beginBatch();LibraryStore.Book staged=aborted.add(bytes,"stage.epub",null);check(aborted.cover(staged).isFile());aborted.abortBatch();check(!aborted.file(staged).exists()&&!aborted.cover(staged).exists()&&aborted.books.isEmpty());
        List<BatchImporter.Source> sources=Arrays.asList(new BatchImporter.Source(){public String name(){return "valid.epub";}public InputStream open(){return new ByteArrayInputStream(bytes);}},new BatchImporter.Source(){public String name(){return "broken.epub";}public InputStream open(){return new ByteArrayInputStream(new byte[]{1,2});}});
        BatchImporter.Result result=BatchImporter.run(aborted,sources,"",null,(a,b,c)->{},()->false);check(result.imported==1&&result.failures.size()==1&&result.commitError==null);check(aborted.books.get(0).epub());
        try{BookExtras.read(new LibraryStore.Book("a","x","UTF-8",1),new JSONObject().put("format","pdf"));throw new AssertionError();}catch(IOException expected){checks++;}
        System.out.println("EpubStorageTest: "+checks+" checks passed; original bytes, covers, dedup, notes/positions, backup, recycle and batch rollback");
    }
}
