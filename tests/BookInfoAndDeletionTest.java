package com.snowread.app;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class BookInfoAndDeletionTest {
    static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("Book info/delete "+checks);}
    public static void main(String[] args)throws Exception{
        File home=Files.createTempDirectory(Paths.get(args[0]),"info-").toFile();LibraryStore store=new LibraryStore(home);byte[] raw="第一章 雪\n这是测试正文，用于备注、状态和划线删除。".getBytes(StandardCharsets.UTF_8);LibraryStore.Book b=store.add(raw,"notes.txt",null);TextDocument doc=store.load(b);b.offset=10;
        store.updateBookInfo(b,"推荐理由\n阅读计划",ReadingStatus.FINISHED);check(b.offset==10&&b.memo.contains("阅读计划"));LibraryStore reloaded=new LibraryStore(home);check(reloaded.find(b.id).memo.equals(b.memo)&&reloaded.find(b.id).readingState==ReadingStatus.FINISHED);
        BookNote underline=BookNote.create(doc,0,3,"下划线备注",1),highlight=BookNote.create(doc,2,6,"保留高亮",0);store.updateNote(b,null,underline);store.updateNote(b,null,highlight);check(BookNote.overlapping(b.annotations,2,3).size()==2);check(BookNote.overlapping(b.annotations,6,8).isEmpty());
        store.updateNote(b,underline,null);check(b.annotations.size()==1&&b.annotations.get(0)==highlight);reloaded=new LibraryStore(home);check(reloaded.find(b.id).annotations.size()==1&&reloaded.find(b.id).annotations.get(0).style==0);check(Arrays.equals(raw,Files.readAllBytes(store.file(b).toPath())));
        File blocked=new File(store.directory(),"index.json.new");check(blocked.mkdir());
        try{store.updateNote(b,highlight,null);throw new AssertionError();}catch(IOException expected){checks++;}check(b.annotations.size()==1&&b.annotations.get(0)==highlight);
        try{store.updateBookInfo(b,"不得留下的半成品",ReadingStatus.UNREAD);throw new AssertionError();}catch(IOException expected){checks++;}check(b.memo.equals("推荐理由\n阅读计划")&&b.readingState==ReadingStatus.FINISHED&&b.offset==10);check(blocked.delete());
        byte[] font=new byte[16];font[1]=1;java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");StringBuilder fontId=new StringBuilder();for(byte x:hash.digest(font))fontId.append(String.format(Locale.ROOT,"%02x",x&255));
        File fonts=new File(home,"user-fonts");check(fonts.mkdir());File fontFile=new File(fonts,fontId+".font");Files.write(fontFile.toPath(),font);PortableFonts.validate(fontFile,fontFile.getName());
        ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.export(store,out,(d,t,n)->{});File destination=Files.createTempDirectory(Paths.get(args[0]),"info-restore-").toFile();
        try(BackupArchive.Prepared backup=BackupArchive.prepare(new ByteArrayInputStream(out.toByteArray()),destination,(d,t,n)->{})){LibraryStore restored=BackupArchive.restore(backup,new LibraryStore(destination),true,(d,t,n)->{});LibraryStore.Book copy=restored.find(b.id);check(copy.memo.equals(b.memo)&&copy.readingState==b.readingState&&copy.offset==10);check(new File(restored.directory(),"fonts/"+fontFile.getName()).isFile());check(Arrays.equals(font,Files.readAllBytes(new File(restored.directory(),"fonts/"+fontFile.getName()).toPath())));check(copy.annotations.size()==1&&copy.annotations.get(0).id.equals(highlight.id));}
        RecycleBin bin=new RecycleBin(home);RecycleBin.Entry removed=bin.remove(store,b,System.currentTimeMillis());LibraryStore.Book back=bin.restore(store,removed);check(back.memo.equals(b.memo)&&back.readingState==ReadingStatus.FINISHED);store.updateNote(back,back.annotations.get(0),null);check(new LibraryStore(home).find(back.id).annotations.isEmpty());
        for(int state:new int[]{-1,4})try{BookExtras.read(new LibraryStore.Book("a","x","UTF-8",20),new JSONObject().put("readingState",state));throw new AssertionError();}catch(IOException expected){checks++;}
        try{store.updateBookInfo(back,String.join("",Collections.nCopies(2001,"x")),0);throw new AssertionError();}catch(IOException expected){checks++;}
        System.out.println("BookInfoAndDeletionTest: "+checks+" checks passed; memo/status persistence, original progress, note removal/reload, overlaps, write rollback, backup and recycle");
    }
}
