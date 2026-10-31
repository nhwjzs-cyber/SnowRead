package com.snowread.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

final class LibraryStore {
    static final class Book extends ShelfOrganization.Entry {
        final ArrayList<BookNote> annotations=new ArrayList<>();
        String encoding;
        volatile int offset;
        int length;
        final ArrayList<Integer> marks = new ArrayList<>();
        final LinkedHashMap<Integer,String> markNotes=new LinkedHashMap<>();
        Typeset typeset;
        volatile ReadingAnchor anchor;
        final LinkedHashMap<Integer,ReadingAnchor> markAnchors=new LinkedHashMap<>();
        final ArrayList<ReadingAnchor.Unmapped> unmappedMarks=new ArrayList<>();
        String sourceName="",encodingConfidence="",format="txt",memo="";
        boolean epub(){return "epub".equals(format);}
        boolean cleanupEnabled;String cleanupRules="";
        int chapterRule,scriptMode;
        Book(String id, String title, String encoding, int length) {
            this.id=id; this.title=title; this.encoding=encoding; this.length=length;
            this.opened=this.added=System.currentTimeMillis();
        }
    }
    final ArrayList<Book> books = new ArrayList<>();
    final ShelfOrganization organization = new ShelfOrganization();
    private final File directory;
    private final AtomicFile index;
    private final HashMap<String,Book> byId=new HashMap<>();
    private int batchDepth;
    private boolean batchDirty;
    private ArrayList<Book> batchBefore;
    private final ArrayList<Book> batchAdded=new ArrayList<>();
    LibraryStore(Context context) throws Exception {this(context.getFilesDir());}
    LibraryStore(File files) throws Exception {
        directory = new File(files, "library");
        File previous=new File(files,"library.previous");
        if(!directory.exists()&&previous.exists()&&!previous.renameTo(directory))throw new IOException("无法恢复上一次书库，请重启应用重试");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("无法创建本地书库");
        index = new AtomicFile(new File(directory, "index.json"));
        if (!index.getBaseFile().exists() && !new File(directory,"index.json.bak").exists()) return;
        try (InputStream in = index.openRead()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
            while ((n=in.read(buffer))!=-1) out.write(buffer,0,n);
            Object data=new JSONTokener(new String(out.toByteArray(), StandardCharsets.UTF_8)).nextValue();
            JSONArray array;
            if(data instanceof JSONArray) array=(JSONArray)data; // Before 0.6: preserve the entire legacy shelf.
            else {
                JSONObject root=(JSONObject)data;
                if(root.getInt("version")!=2&&root.getInt("version")!=3) throw new IOException("书库格式来自更新的版本，请使用新版应用");
                JSONArray categories=root.getJSONArray("categories");
                for(int i=0;i<categories.length();i++) {
                    JSONObject category=categories.getJSONObject(i);
                    organization.categories.put(category.getString("id"),category.getString("name"));
                }
                array=root.getJSONArray("books");
            }
            for (int i=0;i<array.length();i++) {
                JSONObject item=array.getJSONObject(i);
                Book book=new Book(item.getString("id"),item.getString("title"),item.getString("encoding"),item.getInt("length"));
                if (!book.id.matches("[a-f0-9]{64}")) continue;
                book.offset=item.optInt("offset"); book.opened=item.optLong("opened");book.added=item.optLong("added",book.opened);
                book.pinned=item.optBoolean("pinned",false);
                JSONArray memberships=item.optJSONArray("categories");
                if(memberships!=null){for(int j=0;j<memberships.length();j++){String id=memberships.getString(j);if(organization.categories.containsKey(id))book.categoryIds.add(id);}}
                else {String id=item.optString("category","");if(organization.categories.containsKey(id))book.categoryIds.add(id);}
                JSONArray marks=item.optJSONArray("marks");
                if (marks!=null) for (int j=0;j<marks.length();j++) book.marks.add(marks.getInt(j));
                JSONObject notes=item.optJSONObject("markNotes");if(notes!=null)for(Integer pos:book.marks){String note=notes.optString(pos.toString(),"");if(!note.isEmpty())book.markNotes.put(pos,note);}
                BookExtras.read(book,item);books.add(book);if(!byId.containsKey(book.id))byId.put(book.id,book);
            }
        }
    }
    synchronized Book find(String id){return byId.get(id);}
    synchronized Book add(byte[] bytes, String title, String forced) throws Exception {
        return add(bytes,title,forced,"");
    }
    synchronized Book add(byte[] bytes,String title,String forced,String category) throws Exception {
        return addPrepared(bytes,title,null,forced,category,null);
    }
    synchronized Book addPrepared(byte[] bytes,String title,String display,String forced,String category,TextDocument prepared)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] hash=digest.digest(bytes); StringBuilder id=new StringBuilder();
        for (byte b:hash) id.append(String.format(Locale.US,"%02x",b&255));
        Book existing=byId.get(id.toString());if(existing!=null)return existing;
        EpubDocument epub=EpubDocument.name(title)?EpubDocument.parse(bytes):null;
        TextDocument doc=epub!=null?epub.document:prepared==null?TextDocument.decode(bytes,forced):prepared;
        Book book=new Book(id.toString(),display==null?(epub!=null&&!epub.title.equals("未命名章节")?epub.title:TitleCleaner.clean(title)):display,doc.encoding,doc.text.length());book.format=epub==null?"txt":"epub";book.sourceName=EpubDocument.supported(title)?title:title+".txt";book.encodingConfidence=doc.encodingConfidence;
        if(organization.categories.containsKey(category))book.categoryIds.add(category);
        AtomicFile raw=new AtomicFile(file(book)); FileOutputStream out=raw.startWrite();
        try { out.write(bytes); raw.finishWrite(out); } catch (Exception e) { raw.failWrite(out); throw e; }
        if(epub!=null&&epub.cover!=null){
            AtomicFile art=new AtomicFile(cover(book));FileOutputStream image=null;
            try{File parent=cover(book).getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建封面目录");image=art.startWrite();image.write(epub.cover);art.finishWrite(image);}
            catch(Exception e){if(image!=null)art.failWrite(image);raw.delete();art.delete();throw e;}
        }
        byId.put(book.id,book);
        if(batchDepth>0){books.add(book);batchAdded.add(book);batchDirty=true;}
        else {
            books.add(0,book);
            try {save();}catch(Exception e){books.remove(book);byId.remove(book.id);raw.delete();new AtomicFile(cover(book)).delete();throw e;}
        }
        return book;
    }
    /** Import-only transaction. Nested scopes share one snapshot and one outer commit. */
    synchronized void beginBatch() {
        if(batchDepth==0){batchBefore=new ArrayList<>(books);batchAdded.clear();batchDirty=false;}
        batchDepth++;
    }
    synchronized void endBatch()throws Exception {
        if(batchDepth<=0)throw new IllegalStateException("批量导入尚未开始");
        if(--batchDepth>0)return;
        try {
            if(batchDirty){
                // Build newest-first order once; do not shift the entire shelf for every addition.
                books.clear();for(int i=batchAdded.size()-1;i>=0;i--)books.add(batchAdded.get(i));books.addAll(batchBefore);
                save();
            }
        }catch(Exception e){rollbackBatch();throw e;}
        finally{clearBatch();}
    }
    synchronized void abortBatch() {if(batchBefore!=null)rollbackBatch();clearBatch();}
    private void rollbackBatch() {
        books.clear();books.addAll(batchBefore);byId.clear();for(Book book:books)byId.put(book.id,book);
        for(Book book:batchAdded){new AtomicFile(file(book)).delete();new AtomicFile(cover(book)).delete();}
    }
    private void clearBatch(){batchDepth=0;batchDirty=false;batchBefore=null;batchAdded.clear();}
    private void requireNoBatch(){if(batchDepth>0)throw new IllegalStateException("请等待批量导入结束后再修改书架");}
    TextDocument load(Book book) throws Exception {
        if(book.epub())return EpubDocument.parse(TextDocument.readLimited(new FileInputStream(file(book)))).document;
        TextDocument doc=TextDocument.decode(TextDocument.readLimited(new FileInputStream(file(book))),book.encoding);
        if(book.cleanupEnabled)doc=new TextDocument(TextCleanup.apply(doc.text,book.cleanupRules).text,doc.encoding,book.chapterRule);
        if(book.chapterRule!=ChapterRule.AUTO){
            TextDocument ruled=new TextDocument(doc.text,doc.encoding,book.chapterRule);
            ruled.encodingConfidence=doc.encodingConfidence;
            return ruled;
        }
        return doc;
    }
    File file(Book book) { return new File(directory,book.id+".txt"); }
    File cover(Book book) {return new File(new File(directory,"covers"),book.id+".jpg");}
    File directory(){return directory;}
    synchronized void remove(Book book)throws Exception{removeAll(Collections.singletonList(book));}
    /** One durable index commit; original local copies are removed only after it succeeds. */
    synchronized void removeAll(Collection<Book> selected)throws Exception{
        requireNoBatch();LinkedHashSet<Book> wanted=new LinkedHashSet<>(selected);
        for(Book book:wanted)if(find(book.id)!=book)throw new IOException("所选书籍已不在书架中，请刷新后重试");
        if(wanted.isEmpty())return;
        ArrayList<Book> before=new ArrayList<>(books);books.removeAll(wanted);for(Book book:wanted)byId.remove(book.id);
        try{save();}catch(Exception e){books.clear();books.addAll(before);byId.clear();for(Book book:before)byId.put(book.id,book);throw e;}
        for(Book book:wanted){try{new AtomicFile(file(book)).delete();new AtomicFile(cover(book)).delete();}catch(SecurityException ignored){/* Index committed; a recoverable copy already exists. */}}
    }
    synchronized Book restoreFrom(LibraryStore snapshot,Book b)throws Exception {
        requireNoBatch();if(find(b.id)!=null)throw new IOException("这本书已在书架中，已有记录保留");
        Book book=new Book(b.id,b.title,b.encoding,b.length);book.offset=b.offset;book.opened=b.opened;book.pinned=b.pinned;book.marks.addAll(b.marks);book.markNotes.putAll(b.markNotes);BookExtras.copy(b,book);
        LinkedHashMap<String,String> before=new LinkedHashMap<>(organization.categories);boolean added=false;
        try{
            for(String id:b.categoryIds){String name=snapshot.organization.categories.get(id);if(name==null)continue;String mapped=null;for(Map.Entry<String,String> old:organization.categories.entrySet())if(old.getValue().equalsIgnoreCase(name)){mapped=old.getKey();break;}if(mapped==null){mapped=organization.categories.containsKey(id)?UUID.randomUUID().toString():id;organization.categories.put(mapped,name);}book.categoryIds.add(mapped);}
            atomicCopy(snapshot.file(b),file(book),TextDocument.MAX_BYTES);if(snapshot.cover(b).isFile())atomicCopy(snapshot.cover(b),cover(book),BackupArchive.MAX_COVER);
            books.add(0,book);byId.put(book.id,book);added=true;save();return book;
        }catch(Exception e){if(added){books.remove(book);byId.remove(book.id);}organization.categories.clear();organization.categories.putAll(before);new AtomicFile(file(book)).delete();new AtomicFile(cover(book)).delete();throw e;}
    }
    private static void atomicCopy(File from,File to,long limit)throws Exception{File parent=to.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法恢复文件");AtomicFile target=new AtomicFile(to);FileOutputStream out=target.startWrite();try(InputStream in=new FileInputStream(from)){BackupArchive.copy(in,out,limit);target.finishWrite(out);}catch(Exception e){target.failWrite(out);throw e;}}
    private static Book snapshot(Book b){Book copy=new Book(b.id,b.title,b.encoding,b.length);copy.offset=b.offset;copy.opened=b.opened;copy.pinned=b.pinned;copy.categoryIds.addAll(b.categoryIds);copy.marks.addAll(b.marks);copy.markNotes.putAll(b.markNotes);BookExtras.copy(b,copy);return copy;}
    private static void migration(Book b,TextDocument doc,PositionMigration plan){ArrayList<BookNote> mapped=BookNote.remap(b.annotations,doc);b.annotations.clear();b.annotations.addAll(mapped);b.encoding=doc.encoding;b.encodingConfidence=doc.encodingConfidence;b.length=doc.text.length();b.offset=plan.offset;b.anchor=plan.anchor;b.marks.clear();b.marks.addAll(plan.marks);b.markNotes.clear();b.markNotes.putAll(plan.notes);b.markAnchors.clear();b.markAnchors.putAll(plan.anchors);b.unmappedMarks.clear();b.unmappedMarks.addAll(plan.unresolved);}
    synchronized void updateBookInfo(Book book,String memo,int state)throws Exception{
        requireNoBatch();if(find(book.id)!=book)throw new IOException("这本书已不在书架中");if(memo==null||memo.length()>ReadingStatus.MAX_MEMO||!ReadingStatus.valid(state))throw new IOException("备注最多2000字，请选择有效阅读状态");
        String before=book.memo;int old=book.readingState;book.memo=memo;book.readingState=state;try{save();}catch(Exception e){book.memo=before;book.readingState=old;throw e;}
    }
    synchronized void updateNote(Book book,BookNote previous,BookNote replacement)throws Exception{
        requireNoBatch();if(find(book.id)!=book)throw new IOException("书籍已发生变化，请重新打开");
        ArrayList<BookNote> before=new ArrayList<>(book.annotations);
        try{if(previous!=null&&!book.annotations.remove(previous))throw new IOException("笔记已变化，请重新打开列表");
            if(replacement!=null){if(!replacement.valid(book.length)||book.annotations.size()>=BookNote.MAX_COUNT)throw new IOException("笔记无效或超过每本5000条限制");book.annotations.add(replacement);}save();
        }catch(Exception e){book.annotations.clear();book.annotations.addAll(before);throw e;}
    }
    synchronized void cleanup(Book b,boolean enabled,String rules,TextDocument doc,PositionMigration plan)throws Exception{
        requireNoBatch();if(find(b.id)!=b||b.epub())throw new IOException("仅支持当前书架中的 TXT");TextCleanup.parse(rules);
        boolean old=b.cleanupEnabled;String previous=b.cleanupRules;b.cleanupEnabled=enabled;b.cleanupRules=rules;
        try{reencode(b,doc,plan);}catch(Exception e){b.cleanupEnabled=old;b.cleanupRules=previous;throw e;}
    }
    synchronized void reencode(Book b,TextDocument doc,PositionMigration plan)throws Exception{if(b.epub())throw new IOException("EPUB 不支持切换 TXT 编码");requireNoBatch();Book before=snapshot(b);migration(b,doc,plan);try{save();}catch(Exception e){b.encoding=before.encoding;b.length=before.length;b.offset=before.offset;b.marks.clear();b.marks.addAll(before.marks);b.markNotes.clear();b.markNotes.putAll(before.markNotes);b.markAnchors.clear();b.unmappedMarks.clear();BookExtras.copy(before,b);throw e;}}
    synchronized Book replaceContent(Book old,byte[] bytes,String source,TextDocument doc,PositionMigration plan)throws Exception{
        if(old.cleanupEnabled)throw new IOException("请先关闭正文净化再替换正文");
        if(old.epub())throw new IOException("EPUB 暂不支持替换 TXT 正文");
        requireNoBatch();if(find(old.id)!=old)throw new IOException("这本书已不在书架中");byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder hex=new StringBuilder();for(byte n:digest)hex.append(String.format(Locale.US,"%02x",n&255));String id=hex.toString();
        if(id.equals(old.id)){reencode(old,doc,plan);return old;}if(find(id)!=null)throw new IOException("所选正文已在书架中，为保留两本记录，本次未替换");
        Book next=snapshot(old);next.id=id;next.sourceName=source;migration(next,doc,plan);int at=books.indexOf(old);boolean switched=false;AtomicFile raw=new AtomicFile(file(next));
        try{FileOutputStream output=raw.startWrite();try{output.write(bytes);raw.finishWrite(output);}catch(Exception e){raw.failWrite(output);throw e;}if(cover(old).isFile())atomicCopy(cover(old),cover(next),BackupArchive.MAX_COVER);books.set(at,next);byId.remove(old.id);byId.put(next.id,next);switched=true;save();}
        catch(Exception e){if(switched){books.set(at,old);byId.remove(next.id);byId.put(old.id,old);}raw.delete();new AtomicFile(cover(next)).delete();throw e;}
        new AtomicFile(file(old)).delete();new AtomicFile(cover(old)).delete();return next;
    }
    private final Object indexWriteLock=new Object();
    private volatile long indexRevision;
    private volatile boolean progressWritesAllowed=true;
    static final class ProgressWrite {final long revision;final byte[] bytes;ProgressWrite(long r,byte[] b){revision=r;bytes=b;}}
    synchronized void dirtyProgress(){indexRevision++;}
    synchronized void retireProgress(){progressWritesAllowed=false;indexRevision++;}
    synchronized ProgressWrite progressSnapshot()throws Exception{return new ProgressWrite(indexRevision,metadata().toString().getBytes(StandardCharsets.UTF_8));}
    boolean writeProgress(ProgressWrite snapshot)throws Exception{
        synchronized(indexWriteLock){if(!progressWritesAllowed)return true;if(snapshot.revision!=indexRevision)return false;writeIndex(snapshot.bytes);return true;}
    }
    private void writeIndex(byte[] bytes)throws Exception{
        FileOutputStream out=index.startWrite();
        try{out.write(bytes);index.finishWrite(out);}catch(Exception e){index.failWrite(out);throw e;}
    }
    synchronized void save() throws Exception {
        if(batchDepth>0){batchDirty=true;return;}
        indexRevision++;
        byte[] bytes=metadata().toString().getBytes(StandardCharsets.UTF_8);
        synchronized(indexWriteLock){writeIndex(bytes);}
    }
    synchronized JSONObject metadata() throws Exception {
        JSONArray array=new JSONArray();
        for (Book b:books) {
            JSONObject item=new JSONObject(); item.put("id",b.id); item.put("title",b.title);
            item.put("encoding",b.encoding); item.put("offset",b.offset); item.put("length",b.length);
            item.put("opened",b.opened); item.put("added",b.added); item.put("marks",new JSONArray(b.marks)); array.put(item);
            JSONObject notes=new JSONObject();for(Map.Entry<Integer,String> note:b.markNotes.entrySet())if(b.marks.contains(note.getKey()))notes.put(note.getKey().toString(),note.getValue());if(notes.length()>0)item.put("markNotes",notes);
            item.put("pinned",b.pinned);item.put("categories",new JSONArray(b.categoryIds));BookExtras.write(b,item);
        }
        JSONArray categories=new JSONArray();
        for(Map.Entry<String,String> category:organization.categories.entrySet())
            categories.put(new JSONObject().put("id",category.getKey()).put("name",category.getValue()));
        return new JSONObject().put("version",3).put("categories",categories).put("books",array);
    }
    /** All organization metadata is committed together; a failed disk write restores memory. */
    synchronized void organize(Runnable edit) throws Exception {
        requireNoBatch();
        LinkedHashMap<String,String> before=new LinkedHashMap<>(organization.categories);
        ArrayList<LinkedHashSet<String>> categories=new ArrayList<>();ArrayList<Boolean> pins=new ArrayList<>();
        for(Book book:books){categories.add(new LinkedHashSet<>(book.categoryIds));pins.add(book.pinned);}
        try {edit.run();save();} catch(Exception e) {
            organization.categories.clear();organization.categories.putAll(before);
            for(int i=0;i<books.size();i++){books.get(i).categoryIds.clear();books.get(i).categoryIds.addAll(categories.get(i));books.get(i).pinned=pins.get(i);}
            throw e;
        }
    }
}
