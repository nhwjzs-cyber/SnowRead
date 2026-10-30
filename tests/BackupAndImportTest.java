package com.snowread.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

public final class BackupAndImportTest {
    static int checks;static Path workspace;
    interface Attempt{void run()throws Exception;}
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    static void reject(Attempt attempt)throws Exception{boolean failed=false;try{attempt.run();}catch(Exception e){failed=true;}check(failed);}
    static byte[] utf(String text){return text.getBytes(StandardCharsets.UTF_8);}
    static LibraryStore fresh()throws Exception{return new LibraryStore(Files.createTempDirectory(workspace,"shelf-").toFile());}
    static LibraryStore.Book add(LibraryStore shelf,String title,String body)throws Exception{return shelf.add(utf(body),title,null);}
    static LibraryStore.Book byId(LibraryStore shelf,String id){for(LibraryStore.Book b:shelf.books)if(b.id.equals(id))return b;throw new AssertionError("Missing book "+id);}
    static byte[] export(LibraryStore shelf)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();BackupArchive.export(shelf,out,(d,t,n)->{});return out.toByteArray();}
    static BackupArchive.Prepared prepare(byte[] zip,LibraryStore target)throws Exception{return BackupArchive.prepare(new ByteArrayInputStream(zip),target.directory().getParentFile(),(d,t,n)->{});}
    static LinkedHashMap<String,byte[]> entries(byte[] bytes)throws Exception{
        LinkedHashMap<String,byte[]> files=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;while((n=zip.read(buffer))!=-1)out.write(buffer,0,n);files.put(entry.getName(),out.toByteArray());}}return files;
    }
    static byte[] zip(Map<String,byte[]> entries)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){for(Map.Entry<String,byte[]> e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}return out.toByteArray();}
    static BatchImporter.Source source(String name,byte[] bytes){return new BatchImporter.Source(){public String name(){return name;}public InputStream open(){return new ByteArrayInputStream(bytes);}};}
    public static void main(String[] args)throws Exception {
        workspace=Paths.get(args[0]);LibraryStore original=fresh();String fiction=original.organization.add("小说"),empty=original.organization.add("稍后读");
        String body="第一章 雪落\n雪花落在窗前，这是一本用于恢复进度和书签的小说。";
        LibraryStore.Book a=add(original,"原始书名",body),b=add(original,"第二本","第一卷 初春\n第一章 萌芽\n这是另一本独立的小说。");a.categoryIds.add(fiction);a.pinned=true;a.offset=10;a.marks.add(4);a.marks.add(a.length-1);original.save();
        File cover=original.cover(a);check(cover.getParentFile().mkdirs());byte[] picture={(byte)255,(byte)216,1,2,(byte)255,(byte)217};Files.write(cover.toPath(),picture);
        String tracking=original.organization.add("正在追");a.categoryIds.add(tracking);original.save();
        byte[] archive=export(original);check(entries(archive).size()==5);
        LibraryStore target=fresh();String localCategory=target.organization.add("小说");LibraryStore.Book local=add(target,"本地书名",body);local.offset=2;local.categoryIds.add(localCategory);local.pinned=false;target.save();LibraryStore.Book onlyLocal=add(target,"只在本机","第一章 本机\n这是本机独有的一本书。");
        try(BackupArchive.Prepared p=prepare(archive,target)) {
            check(p.store.books.size()==2&&p.store.organization.categories.containsKey(empty));
            target=BackupArchive.restore(p,target,false,(d,t,n)->{});
        }
        check(target.books.size()==3);LibraryStore.Book kept=byId(target,a.id);check(kept.title.equals("本地书名")&&kept.offset==2&&!kept.pinned&&kept.marks.isEmpty());check(kept.categoryIds.contains(localCategory));check(!target.cover(kept).exists());check(byId(target,onlyLocal.id)!=null);check(target.organization.categories.containsValue("稍后读"));check(Arrays.equals(Files.readAllBytes(target.file(byId(target,b.id)).toPath()),utf("第一卷 初春\n第一章 萌芽\n这是另一本独立的小说。")));
        try(BackupArchive.Prepared p=prepare(archive,target)){target=BackupArchive.restore(p,target,true,(d,t,n)->{});}
        LibraryStore.Book replaced=byId(target,a.id);check(replaced.title.equals(a.title)&&replaced.offset==10&&replaced.pinned&&replaced.marks.equals(a.marks));check(replaced.categoryIds.contains(localCategory));check(replaced.categoryIds.size()==2);check(target.organization.categories.get(replaced.categoryIds.toArray(new String[0])[1]).equals("正在追"));check(Arrays.equals(Files.readAllBytes(target.cover(replaced).toPath()),picture));check(target.books.size()==3);
        target=new LibraryStore(target.directory().getParentFile());check(byId(target,a.id).offset==10);check(byId(target,onlyLocal.id)!=null);
        // Version 1 backup with a v2 single-category index must still restore into v3.
        LinkedHashMap<String,byte[]> legacyEntries=entries(archive);JSONObject legacyManifest=new JSONObject(new String(legacyEntries.get("manifest.json"),StandardCharsets.UTF_8));legacyManifest.put("version",1);legacyEntries.put("manifest.json",utf(legacyManifest.toString()));
        JSONObject legacyIndex=new JSONObject(new String(legacyEntries.get("library/index.json"),StandardCharsets.UTF_8));legacyIndex.put("version",2);JSONArray legacyBooks=legacyIndex.getJSONArray("books");for(int i=0;i<legacyBooks.length();i++){JSONObject item=legacyBooks.getJSONObject(i);JSONArray memberships=item.getJSONArray("categories");item.put("category",memberships.length()==0?"":memberships.getString(0));item.remove("categories");}legacyEntries.put("library/index.json",utf(legacyIndex.toString()));
        LibraryStore legacyTarget=fresh();try(BackupArchive.Prepared p=prepare(zip(legacyEntries),legacyTarget)){legacyTarget=BackupArchive.restore(p,legacyTarget,true,(d,t,n)->{});}check(byId(legacyTarget,a.id).categoryIds.size()==1);check(byId(legacyTarget,a.id).offset==10);check(legacyTarget.metadata().getInt("version")==3);
        // A colliding category ID with a different name must not relabel local books.
        LibraryStore collision=fresh();collision.organization.categories.put(fiction,"本地分类");LibraryStore.Book localOnly=add(collision,"本机分类书","第一章 本地\n这一册属于本机分类。");localOnly.categoryIds.add(fiction);collision.save();
        try(BackupArchive.Prepared p=prepare(archive,collision)){collision=BackupArchive.restore(p,collision,false,(d,t,n)->{});}
        check(byId(collision,localOnly.id).categoryIds.contains(fiction));check(collision.organization.categories.get(fiction).equals("本地分类"));check(!byId(collision,a.id).categoryIds.contains(fiction));check(collision.organization.categories.get(byId(collision,a.id).categoryIds.iterator().next()).equals("小说"));
        // Empty shelves still preserve empty categories and can be backed up.
        LibraryStore emptyShelf=fresh();emptyShelf.organization.add("想读");emptyShelf.save();try(BackupArchive.Prepared p=prepare(export(emptyShelf),emptyShelf)){check(p.store.books.isEmpty());check(p.store.organization.categories.containsValue("想读"));}
        final LibraryStore safe=target;String before=safe.metadata().toString();
        LinkedHashMap<String,byte[]> bad=entries(archive);bad.put("../outside.txt",utf("no"));byte[] attack=zip(bad);reject(()->prepare(attack,safe));check(!new File(safe.directory().getParentFile(),"outside.txt").exists());
        bad=entries(archive);bad.put("library/"+a.id+".txt",utf("tampered"));byte[] tampered=zip(bad);reject(()->prepare(tampered,safe));
        bad=entries(archive);bad.remove("library/"+a.id+".txt");byte[] missing=zip(bad);reject(()->prepare(missing,safe));
        bad=entries(archive);JSONObject manifest=new JSONObject(new String(bad.get("manifest.json"),StandardCharsets.UTF_8));manifest.put("version",99);bad.put("manifest.json",utf(manifest.toString()));byte[] future=zip(bad);reject(()->prepare(future,safe));
        bad=entries(archive);JSONObject meta=new JSONObject(new String(bad.get("library/index.json"),StandardCharsets.UTF_8));meta.getJSONArray("books").getJSONObject(0).put("offset",Integer.MAX_VALUE);bad.put("library/index.json",utf(meta.toString()));byte[] badPosition=zip(bad);reject(()->prepare(badPosition,safe));
        bad=entries(archive);bad.put("library/index.json",new byte[BackupArchive.MAX_INDEX+1]);byte[] oversized=zip(bad);reject(()->prepare(oversized,safe));
        reject(()->prepare(Arrays.copyOf(archive,30),safe));check(safe.metadata().toString().equals(before));
        try(BackupArchive.Prepared p=prepare(archive,safe)) {check(p.store.file(p.store.books.get(0)).delete());reject(()->BackupArchive.restore(p,safe,true,(d,t,n)->{}));check(safe.metadata().toString().equals(before));check(new File(safe.directory(),"index.json").isFile());}
        File previous=new File(safe.directory().getParentFile(),"library.previous");check(safe.directory().renameTo(previous));LibraryStore recovered=new LibraryStore(safe.directory().getParentFile());check(recovered.books.size()==3);check(byId(recovered,a.id).offset==10);check(!previous.exists());
        // Mixed batch: duplicate, valid TXT, wrong extension, empty file, inaccessible provider.
        LibraryStore batch=fresh();String category=batch.organization.add("批量分类");LibraryStore.Book existing=add(batch,"旧名字",body);existing.offset=7;batch.save();
        ArrayList<BatchImporter.Source> inputs=new ArrayList<>(Arrays.asList(source("重复.TXT",utf(body)),source("新书.txt",utf("第一章 新书\n这是批量导入的新书。")),source("不是文本.pdf",utf("pdf")),source("空文件.txt",new byte[0]),new BatchImporter.Source(){public String name(){return "失效文件.txt";}public InputStream open()throws Exception{throw new IOException("provider unavailable");}}));
        final int[] progress={0};BatchImporter.Result report=BatchImporter.run(batch,inputs,category,null,(d,t,n)->progress[0]=d,()->false);
        check(report.imported==1&&report.duplicates==1&&report.failures.size()==3&&report.processed==5);check(report.firstImported!=null&&report.firstImported.title.equals("新书"));check(progress[0]==5);check(existing.offset==7&&existing.categoryIds.isEmpty()&&existing.title.equals("旧名字"));check(batch.books.size()==2);check(batch.books.get(0).categoryIds.contains(category));
        batch=new LibraryStore(batch.directory().getParentFile());check(batch.books.size()==2&&byId(batch,existing.id).offset==7);
        final boolean[] stopped={false};BatchImporter.Result partial=BatchImporter.run(batch,Arrays.asList(source("一.txt",utf("第一章 一\n停止前的正文。")),source("二.txt",utf("第二章 二\n这一本不应该导入。"))),category,null,(d,t,n)->{if(d==1)stopped[0]=true;},()->stopped[0]);check(partial.imported==1&&partial.processed==1&&partial.total==2);
        System.out.println("BackupAndImportTest: "+checks+" checks passed; backup roundtrip, merge policies, corruption, interruption and batch import");
    }
}
