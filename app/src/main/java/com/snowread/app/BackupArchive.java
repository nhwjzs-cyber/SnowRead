package com.snowread.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Portable ZIP backup; validate in isolation, merge in staging, then swap directories. */
final class BackupArchive {
    static final long MAX_TOTAL=1024L*1024*1024;
    static final int MAX_SETTINGS=2*1024*1024;
    static final int MAX_BOOKS=5000,MAX_INDEX=8*1024*1024,MAX_COVER=3*1024*1024;
    interface Progress {void update(int done,int total,String title);default void checkCancelled(){} default void beginCommit(){checkCancelled();}}
    static final class Prepared implements Closeable {
        final File home;final LibraryStore store;final JSONObject settings;
        Prepared(File home,LibraryStore store){this(home,store,null);}
        Prepared(File home,LibraryStore store,JSONObject settings){this.home=home;this.store=store;this.settings=settings;}
        public void close(){clean(home);}
    }
    static void export(LibraryStore store,OutputStream output,Progress progress)throws Exception {export(store,null,output,progress);}
    /** settings (nullable) carries the app's global preferences as settings.json; restore asks whether to apply them. */
    static void export(LibraryStore store,byte[] settings,OutputStream output,Progress progress)throws Exception {
        progress.checkCancelled();
        if(settings!=null&&settings.length>MAX_SETTINGS)throw new IOException("设置与统计记录过大");
        if(store.books.size()>MAX_BOOKS)throw new IOException("每份备份最多支持 5000 本书");
        byte[] index=store.metadata().toString().getBytes(StandardCharsets.UTF_8);
        if(index.length>MAX_INDEX)throw new IOException("书库记录过大，无法生成这份备份");
        LinkedHashMap<String,File> fonts=PortableFonts.files(store.directory());
        JSONObject manifest=new JSONObject().put("format","snowread.backup").put("version",hasCleanup(store)?5:!fonts.isEmpty()||(settings!=null&&settings.length>65536)?4:hasEpub(store)?3:2).put("createdAt",System.currentTimeMillis()).put("bookCount",store.books.size());
        long total=index.length+(settings==null?0:settings.length);long planned=index.length;for(LibraryStore.Book b:store.books){planned+=store.file(b).length();if(store.cover(b).isFile())planned+=store.cover(b).length();}for(File f:fonts.values())planned+=f.length();if(planned>MAX_TOTAL)throw new IOException("备份解压后最多支持 1 GB");final int expected=(int)Math.max(1,planned);
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output))) {
            zip.setLevel(1);put(zip,"manifest.json",manifest.toString().getBytes(StandardCharsets.UTF_8));put(zip,"library/index.json",index);
            if(settings!=null)put(zip,"settings.json",settings);
            for(Map.Entry<String,File> font:fonts.entrySet()){PortableFonts.validate(font.getValue(),font.getKey());total+=putFileTracked(zip,"library/fonts/"+font.getKey(),font.getValue(),PortableFonts.LIMIT,progress,(int)total,expected,"正在备份自定义字体");}
            int done=0;
            for(LibraryStore.Book book:store.books) {
                progress.update((int)total,expected,"正在备份「"+book.title+"」");
                if(!sha256(store.file(book),progress).equals(book.id))throw new IOException("「"+book.title+"」正文已损坏，无法备份");
                total+=putFileTracked(zip,"library/"+book.id+".txt",store.file(book),TextDocument.MAX_BYTES,progress,(int)total,expected,book.title);
                if(store.cover(book).isFile())total+=putFileTracked(zip,"library/covers/"+book.id+".jpg",store.cover(book),MAX_COVER,progress,(int)total,expected,book.title);
                if(total>MAX_TOTAL)throw new IOException("备份解压后最多支持 1 GB，请先精简书库");
                ++done;progress.update((int)total,expected,book.title);
            }
        }
    }
    static Prepared prepare(InputStream input,File files,Progress progress)throws Exception {
        File home=temporary(files,"restore-incoming-");boolean ready=false;
        try {
            HashSet<String> names=new HashSet<>();long total=0;
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))) {
                ZipEntry entry;int count=0;
                while((entry=zip.getNextEntry())!=null) {
                    String name=entry.getName();
                    if(entry.isDirectory()) {if(!name.equals("library/")&&!name.equals("library/covers/")&&!name.equals("library/fonts/"))throw new IOException("备份中含有无法识别的目录");continue;}
                    if(!name.matches("library/fonts/[a-f0-9]{64}\\.font")&&!name.equals("manifest.json")&&!name.equals("settings.json")&&!name.equals("library/index.json")&&!name.matches("library/[a-f0-9]{64}\\.txt")&&!name.matches("library/covers/[a-f0-9]{64}\\.jpg"))throw new IOException("这不是有效的融雪阅读备份：文件路径不正确");
                    if(!names.add(name)||++count>MAX_BOOKS*2+3+PortableFonts.MAX)throw new IOException("备份中存在重复文件或文件过多");
                    File target=new File(home,name);
                    if(!target.getCanonicalPath().startsWith(home.getCanonicalPath()+File.separator))throw new IOException("备份路径无效");
                    File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("无法创建恢复临时目录");
                    int limit=name.equals("manifest.json")?16384:name.equals("settings.json")?MAX_SETTINGS:name.equals("library/index.json")?MAX_INDEX:name.endsWith(".font")?PortableFonts.LIMIT:name.endsWith(".jpg")?MAX_COVER:TextDocument.MAX_BYTES;
                    try(OutputStream out=new FileOutputStream(target)){total+=copyTracked(zip,out,Math.min(limit,MAX_TOTAL-total),progress,0,0,"正在检查备份文件");}
                    if(total>MAX_TOTAL)throw new IOException("备份解压后超过 1 GB");zip.closeEntry();progress.update(count,0,"正在检查备份文件");
                }
            }
            if(!names.contains("manifest.json")||!names.contains("library/index.json"))throw new IOException("未找到完整备份，请选择应用导出的 ZIP 文件");
            JSONObject manifest=new JSONObject(read(new File(home,"manifest.json"),16384));
            if(!"snowread.backup".equals(manifest.getString("format"))||(manifest.getInt("version")!=1&&manifest.getInt("version")!=2&&manifest.getInt("version")!=3&&manifest.getInt("version")!=4&&manifest.getInt("version")!=5))throw new IOException("不支持这个备份版本，请更新应用后再试");
            JSONObject metadata=new JSONObject(read(new File(home,"library/index.json"),MAX_INDEX));
            validateMetadata(metadata);
            LibraryStore incoming=new LibraryStore(home);
            if(manifest.getInt("bookCount")!=incoming.books.size())throw new IOException("备份书籍数量不一致");
            HashSet<String> expected=new HashSet<>();expected.add("manifest.json");expected.add("library/index.json");
            if(names.contains("settings.json"))expected.add("settings.json");
            int done=0;
            for(LibraryStore.Book b:incoming.books) {
                progress.checkCancelled();if(!names.contains("library/"+b.id+".txt")||!sha256(incoming.file(b),progress).equals(b.id))throw new IOException("「"+b.title+"」正文缺失或校验失败");
                TextDocument doc=incoming.load(b);
                progress.checkCancelled();
                if(doc.text.length()!=b.length)throw new IOException("「"+b.title+"」的文本长度与记录不一致");
                expected.add("library/"+b.id+".txt");if(incoming.cover(b).isFile())expected.add("library/covers/"+b.id+".jpg");
                progress.update(++done,incoming.books.size(),b.title);
            }
            File fontFolder=new File(home,"library/fonts");File[] fontFiles=fontFolder.listFiles();if(fontFiles!=null){if(fontFiles.length>PortableFonts.MAX)throw new IOException("备份字体数量过多");for(File font:fontFiles){PortableFonts.validate(font,font.getName());expected.add("library/fonts/"+font.getName());}}
            if(!expected.equals(names))throw new IOException("备份中含有未登记的书籍或封面");
            JSONObject settings=names.contains("settings.json")?new JSONObject(read(new File(home,"settings.json"),MAX_SETTINGS)):null;
            if(settings!=null&&settings.has("readingStats"))ReadingStats.validateSnapshot(settings.getJSONObject("readingStats"));
            ready=true;return new Prepared(home,incoming,settings);
        }finally{if(!ready)clean(home);}
    }
    static LibraryStore restore(Prepared backup,LibraryStore current,boolean useBackup,Progress progress)throws Exception {
        return restore(backup,current,true,useBackup,progress);
    }
    static LibraryStore restore(Prepared backup,LibraryStore current,boolean merge,boolean useBackup,Progress progress)throws Exception {
        progress.checkCancelled();
        File files=current.directory().getParentFile(),home=temporary(files,"restore-merge-");
        try {
            JSONObject merged=merge?current.metadata():new JSONObject().put("version",3).put("categories",new JSONArray()).put("books",new JSONArray());JSONArray categories=merged.getJSONArray("categories");
            LinkedHashMap<String,String> categoryNames=merge?new LinkedHashMap<>(current.organization.categories):new LinkedHashMap<>();HashMap<String,String> remap=new HashMap<>();
            for(Map.Entry<String,String> entry:backup.store.organization.categories.entrySet()) {
                String mapped=null;for(Map.Entry<String,String> old:categoryNames.entrySet())if(old.getValue().equalsIgnoreCase(entry.getValue())){mapped=old.getKey();break;}
                if(mapped==null){mapped=categoryNames.containsKey(entry.getKey())?UUID.randomUUID().toString():entry.getKey();categoryNames.put(mapped,entry.getValue());categories.put(new JSONObject().put("id",mapped).put("name",entry.getValue()));}
                remap.put(entry.getKey(),mapped);
            }
            LinkedHashMap<String,JSONObject> books=new LinkedHashMap<>();HashMap<String,LibraryStore> owners=new HashMap<>();HashMap<String,LibraryStore.Book> originals=new HashMap<>();
            JSONArray old=merged.getJSONArray("books");
            for(int i=0;i<old.length();i++){JSONObject b=old.getJSONObject(i);books.put(b.getString("id"),b);}
            if(merge)for(LibraryStore.Book b:current.books){owners.put(b.id,current);originals.put(b.id,b);}
            JSONArray imported=backup.store.metadata().getJSONArray("books");
            for(int i=0;i<imported.length();i++) {
                JSONObject b=imported.getJSONObject(i);String id=b.getString("id");
                if(!books.containsKey(id)||useBackup){JSONArray memberships=b.getJSONArray("categories"),mapped=new JSONArray();for(int j=0;j<memberships.length();j++){String cat=remap.get(memberships.getString(j));if(cat!=null)mapped.put(cat);}b.put("categories",mapped);books.put(id,b);owners.put(id,backup.store);}
            }
            for(LibraryStore.Book b:backup.store.books)if(owners.get(b.id)==backup.store)originals.put(b.id,b);
            if(books.size()>MAX_BOOKS)throw new IOException("合并后超过 5000 本，当前书库未更改");
            JSONArray combined=new JSONArray();for(JSONObject b:books.values())combined.put(b);merged.put("books",combined);
            File library=new File(home,"library");if(!new File(library,"covers").mkdirs())throw new IOException("无法创建恢复目录");
            byte[] metadata=merged.toString().getBytes(StandardCharsets.UTF_8);if(metadata.length>MAX_INDEX)throw new IOException("合并后的书库记录过大");
            try(OutputStream out=new FileOutputStream(new File(library,"index.json"))){out.write(metadata);}
            long bytes=metadata.length;int done=0;long planned=bytes;for(String id:books.keySet()){LibraryStore owner=owners.get(id);LibraryStore.Book b=originals.get(id);planned+=owner.file(b).length();if(owner.cover(b).isFile())planned+=owner.cover(b).length();}if(planned>MAX_TOTAL)throw new IOException("合并后的书库超过 1 GB");final int expected=(int)Math.max(1,planned);
            for(String id:books.keySet()) {
                LibraryStore owner=owners.get(id);LibraryStore.Book book=originals.get(id);
                bytes+=copyFileTracked(owner.file(book),new File(library,id+".txt"),TextDocument.MAX_BYTES,progress,(int)bytes,expected,book.title);
                if(owner.cover(book).isFile())bytes+=copyFileTracked(owner.cover(book),new File(new File(library,"covers"),id+".jpg"),MAX_COVER,progress,(int)bytes,expected,book.title);
                if(bytes>MAX_TOTAL)throw new IOException("合并后的书库超过 1 GB，当前书库未更改");++done;progress.update((int)bytes,expected,book.title);
            }
            LinkedHashMap<String,File> fonts=new LinkedHashMap<>();if(merge)fonts.putAll(PortableFonts.files(current.directory()));
            for(Map.Entry<String,File> e:PortableFonts.files(backup.store.directory()).entrySet())fonts.put(e.getKey(),e.getValue());
            if(fonts.size()>PortableFonts.MAX)throw new IOException("合并后的字体超过32款，当前书库未更改");
            if(!fonts.isEmpty()){File folder=new File(library,"fonts");if(!folder.mkdirs())throw new IOException("无法创建字体恢复目录");for(Map.Entry<String,File> e:fonts.entrySet()){PortableFonts.validate(e.getValue(),e.getKey());bytes+=copyFileTracked(e.getValue(),new File(folder,e.getKey()),PortableFonts.LIMIT,progress,0,0,"正在恢复字体");if(bytes>MAX_TOTAL)throw new IOException("恢复数据超过1 GB");}}
            new LibraryStore(home); // Read the completed staged index before touching the live shelf.
            progress.beginCommit(); // Cancellation cannot interleave the two directory renames.
            File live=current.directory(),previous=new File(files,"library.previous");
            clean(previous);if(previous.exists())throw new IOException("无法整理上次恢复的临时文件，请重启应用");
            if(!live.renameTo(previous))throw new IOException("无法切换书库，当前数据未更改");
            if(!library.renameTo(live)) {if(!previous.renameTo(live))throw new IOException("恢复中断，请重启应用以恢复原书库");throw new IOException("无法写入新书库，已保留原书库");}
            LibraryStore restored;
            try{restored=new LibraryStore(files);}catch(Exception e){if(live.renameTo(library))previous.renameTo(live);throw e;}
            clean(previous);return restored;
        }finally{clean(home);}
    }
    private static boolean hasCleanup(LibraryStore s){for(LibraryStore.Book b:s.books)if(b.cleanupEnabled||!b.cleanupRules.isEmpty())return true;return false;}
    private static boolean hasEpub(LibraryStore store){for(LibraryStore.Book b:store.books)if(b.epub())return true;return false;}
    private static void validateMetadata(JSONObject root)throws Exception {
        int version=root.getInt("version");if(version!=2&&version!=3)throw new IOException("备份中的书库版本不受支持");
        JSONArray categories=root.getJSONArray("categories"),books=root.getJSONArray("books");
        if(books.length()>MAX_BOOKS||categories.length()>5000)throw new IOException("备份中的书籍或分类过多");
        HashSet<String> ids=new HashSet<>();ShelfOrganization names=new ShelfOrganization();
        for(int i=0;i<categories.length();i++){JSONObject c=categories.getJSONObject(i);String id=c.getString("id");if(!id.matches("[a-fA-F0-9-]{36}")||!ids.add(id))throw new IOException("备份分类编号无效");names.add(c.getString("name"));}
        HashSet<String> bookIds=new HashSet<>();
        for(int i=0;i<books.length();i++) {
            JSONObject b=books.getJSONObject(i);String id=b.getString("id");int length=b.getInt("length"),offset=b.getInt("offset");
            if(!id.matches("[a-f0-9]{64}")||!bookIds.add(id)||b.getString("title").trim().isEmpty()||b.getString("title").length()>4096||length<1||length>TextDocument.MAX_BYTES||offset<0||offset>length)throw new IOException("备份中的书籍记录无效");
            JSONArray memberships=version==3?b.getJSONArray("categories"):new JSONArray();if(version==2&&!b.optString("category","").isEmpty())memberships.put(b.getString("category"));
            HashSet<String> seen=new HashSet<>();for(int j=0;j<memberships.length();j++)if(!ids.contains(memberships.getString(j))||!seen.add(memberships.getString(j)))throw new IOException("备份中的分类关联无效");
            JSONArray marks=b.getJSONArray("marks");if(marks.length()>100000)throw new IOException("备份中的书签过多");
            for(int j=0;j<marks.length();j++)if(marks.getInt(j)<0||marks.getInt(j)>length)throw new IOException("备份中的书签位置无效");
            LibraryStore.Book checked=new LibraryStore.Book(id,b.getString("title"),b.getString("encoding"),length);for(int j=0;j<marks.length();j++)checked.marks.add(marks.getInt(j));BookExtras.read(checked,b);
            JSONObject notes=b.optJSONObject("markNotes");if(b.has("markNotes")&&notes==null)throw new IOException("备份中的书签备注格式无效");if(notes!=null){HashSet<String> positions=new HashSet<>();for(int j=0;j<marks.length();j++)positions.add(Integer.toString(marks.getInt(j)));for(Iterator<String> it=notes.keys();it.hasNext();){String key=it.next();Object value=notes.get(key);if(!positions.contains(key)||!(value instanceof String)||((String)value).length()>1000)throw new IOException("备份中的书签备注无效");}}
        }
    }
    private static File temporary(File files,String prefix)throws IOException {File home=new File(files,prefix+UUID.randomUUID());if(!home.mkdir())throw new IOException("无法创建临时目录，请检查剩余空间");return home;}
    private static void put(ZipOutputStream zip,String name,byte[] bytes)throws IOException {zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
    private static long putFile(ZipOutputStream zip,String name,File file,long limit)throws IOException {zip.putNextEntry(new ZipEntry(name));long count;try(InputStream in=new FileInputStream(file)){count=copy(in,zip,limit);}zip.closeEntry();return count;}
    private static long copyFile(File from,File to,long limit)throws IOException {try(InputStream in=new FileInputStream(from);OutputStream out=new FileOutputStream(to)){return copy(in,out,limit);}}
    private static long putFileTracked(ZipOutputStream zip,String name,File file,long limit,Progress progress,int before,int total,String title)throws IOException {zip.putNextEntry(new ZipEntry(name));long count;try(InputStream in=new FileInputStream(file)){count=copyTracked(in,zip,limit,progress,before,total,title);}zip.closeEntry();return count;}
    private static long copyFileTracked(File from,File to,long limit,Progress progress,int before,int total,String title)throws IOException {try(InputStream in=new FileInputStream(from);OutputStream out=new FileOutputStream(to)){return copyTracked(in,out,limit,progress,before,total,title);}}
    static long copyTracked(InputStream in,OutputStream out,long limit,Progress progress,int before,int total,String title)throws IOException {if(in==null||out==null)throw new IOException("无法打开所选文件");byte[] buffer=new byte[32768];long count=0,reported=0;int n;progress.checkCancelled();while((n=in.read(buffer))!=-1){progress.checkCancelled();count+=n;if(count>limit)throw new IOException("文件超过允许的大小");out.write(buffer,0,n);if(count-reported>=131072){progress.update(before+(int)count,total,title);reported=count;}}progress.update(before+(int)count,total,title);return count;}
    static long copy(InputStream in,OutputStream out,long limit)throws IOException {if(in==null||out==null)throw new IOException("无法打开所选文件");byte[] buffer=new byte[32768];long count=0;int n;while((n=in.read(buffer))!=-1){count+=n;if(count>limit)throw new IOException("文件超过允许的大小");out.write(buffer,0,n);}return count;}
    private static String read(File file,int limit)throws IOException {try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){copy(in,out,limit);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static String sha256(File file,Progress progress)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){progress.checkCancelled();count+=n;if(count>TextDocument.MAX_BYTES)throw new IOException("书籍超过 12 MB");digest.update(buffer,0,n);}}StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.US,"%02x",b&255));return hex.toString();}
    static void clean(File file){if(file==null||!file.exists())return;File[] children=file.listFiles();if(children!=null)for(File child:children)clean(child);file.delete();}
}
