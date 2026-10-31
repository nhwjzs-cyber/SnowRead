package com.snowread.app;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;
public final class CleanupStorageTest {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Cleanup "+checks);}
 static PositionMigration plan(LibraryStore s,LibraryStore.Book b,TextDocument next)throws Exception{return new PositionMigration(s.load(b),next,b.offset,b.anchor,b.marks,b.markNotes,b.markAnchors,b.unmappedMarks,true);}
 public static void main(String[] args)throws Exception {
  String raw="第一章 雪\n这是保留的第一段。\nhttps://example.org/ad\n本章未完，请点击下一页继续阅读\n错字与普通正文，还有😀。\n第二章 春\n这是第二章完整的内容。";
  TextCleanup.Result result=TextCleanup.apply(raw,"错字 => 正字");check(result.removed==2&&result.changed==3);check(!result.text.contains("example.org")&&result.text.contains("正字")&&result.text.contains("😀"));
  check(TextCleanup.apply("角色说本章未完，请点击下一页继续阅读。\n还有正文","").removed==0);
  check(TextCleanup.apply("这是网站广告xx\n正文", "删除整行:网站广告").text.equals("正文"));
  for(String bad:new String[]{"无分隔符"," => 字","删除整行:",String.join("\n",Collections.nCopies(21,"a => b"))})try{TextCleanup.parse(bad);throw new AssertionError();}catch(IOException expected){checks++;}
  try{TextCleanup.apply("https://example.org","");throw new AssertionError();}catch(IOException expected){checks++;}
  File home=Files.createTempDirectory(Paths.get(args[0]),"cleanup-").toFile();LibraryStore store=new LibraryStore(home);byte[] bytes=raw.getBytes(StandardCharsets.UTF_8);LibraryStore.Book b=store.add(bytes,"sample.txt",null);TextDocument original=store.load(b);b.offset=raw.indexOf("这是第二章");b.marks.add(b.offset);b.markNotes.put(b.offset,"原始书签备注");b.marks.add(raw.indexOf("https://"));b.markNotes.put(raw.indexOf("https://"),"被移除行的书签");store.updateNote(b,null,BookNote.create(original,raw.indexOf("错字"),raw.indexOf("错字")+2,"保留笔记",1));
  TextDocument next=new TextDocument(result.text,b.encoding,b.chapterRule);store.cleanup(b,true,"错字 => 正字",next,plan(store,b,next));check(Arrays.equals(bytes,Files.readAllBytes(store.file(b).toPath())));check(b.offset==next.text.indexOf("这是第二章"));check(b.unmappedMarks.size()==1&&b.unmappedMarks.get(0).note.equals("被移除行的书签"));check(b.annotations.size()==1&&!b.annotations.get(0).located()&&b.annotations.get(0).note.equals("保留笔记"));
  LibraryStore loaded=new LibraryStore(home);LibraryStore.Book copy=loaded.find(b.id);check(copy.cleanupEnabled&&loaded.load(copy).text.equals(result.text));
  File blocked=new File(store.directory(),"index.json.new");check(blocked.mkdir());int length=b.length;try{store.cleanup(b,false,"",original,plan(store,b,original));throw new AssertionError();}catch(IOException expected){check(b.cleanupEnabled&&b.length==length&&b.cleanupRules.equals("错字 => 正字"));}check(blocked.delete());
  ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.export(store,out,(d,t,n)->{});File target=Files.createTempDirectory(Paths.get(args[0]),"cleanup-restored-").toFile();try(BackupArchive.Prepared p=BackupArchive.prepare(new ByteArrayInputStream(out.toByteArray()),target,(d,t,n)->{})){LibraryStore restored=BackupArchive.restore(p,new LibraryStore(target),false,true,(d,t,n)->{});check(restored.load(restored.find(b.id)).text.equals(result.text));check(restored.find(b.id).annotations.size()==1);}
  store.cleanup(b,false,b.cleanupRules,original,plan(store,b,original));check(store.load(b).text.equals(raw));check(b.annotations.get(0).located()&&b.annotations.get(0).quote.equals("错字"));check(b.marks.size()+b.unmappedMarks.size()==2);check(Arrays.equals(bytes,Files.readAllBytes(store.file(b).toPath())));
  System.out.println("CleanupStorageTest: "+checks+" checks passed; rules, source preservation, offset/notes migration, reload, failed save rollback, backup and restoration");
 }
}
