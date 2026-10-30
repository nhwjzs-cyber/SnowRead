package com.snowread.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Durable copy before removal: a crash before the live commit leaves a harmless duplicate. */
final class RecycleBin {
    static final long RETENTION=7L*24*60*60*1000;
    static final class Entry {
        final File home;final long removedAt;final LibraryStore snapshot;final LibraryStore.Book book;
        Entry(File home)throws Exception{this.home=home;removedAt=Long.parseLong(home.getName().substring(0,home.getName().indexOf('-')));snapshot=new LibraryStore(home);if(snapshot.books.size()!=1)throw new IOException("回收记录不完整");book=snapshot.books.get(0);}
    }
    private final File directory;
    RecycleBin(File files)throws IOException{directory=new File(files,"recycle");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建回收站");}
    synchronized Entry remove(LibraryStore store,LibraryStore.Book book,long now)throws Exception{
        return removeAll(store,Collections.singletonList(book),now).get(0);
    }
    /** Prepare every recoverable copy before removing any live book. A failed commit keeps the shelf intact. */
    synchronized ArrayList<Entry> removeAll(LibraryStore store,Collection<LibraryStore.Book> selected,long now)throws Exception{
        ArrayList<Entry> copies=new ArrayList<>();boolean committed=false;
        synchronized(store){
            LinkedHashSet<LibraryStore.Book> wanted=new LinkedHashSet<>(selected);
            for(LibraryStore.Book book:wanted)if(store.find(book.id)!=book)throw new IOException("所选书籍已不在书架中，请刷新后重试");
            if(wanted.isEmpty())return copies;
            JSONObject metadata=store.metadata();JSONArray books=metadata.getJSONArray("books"),categories=metadata.getJSONArray("categories");
            HashMap<String,JSONObject> records=new HashMap<>();for(int i=0;i<books.length();i++){JSONObject record=books.getJSONObject(i);records.put(record.getString("id"),record);}
            try{
                for(LibraryStore.Book book:wanted){JSONObject one=new JSONObject().put("version",3).put("categories",categories).put("books",new JSONArray().put(records.get(book.id)));copies.add(snapshot(store,book,now,one));}
                store.removeAll(wanted);committed=true;return copies;
            }finally{if(!committed)for(Entry entry:copies)BackupArchive.clean(entry.home);}
        }
    }
    synchronized Entry archive(LibraryStore store,LibraryStore.Book book,long now)throws Exception{return snapshot(store,book,now,null);}
    private Entry snapshot(LibraryStore store,LibraryStore.Book book,long now,JSONObject prepared)throws Exception{
        File staging=new File(directory,"pending-"+UUID.randomUUID()),home=new File(directory,now+"-"+UUID.randomUUID());
        boolean removed=false;
        try{
            synchronized(store){
                if(store.find(book.id)!=book)throw new IOException("这本书已不在书架中");
                File library=new File(staging,"library");if(!new File(library,"covers").mkdirs())throw new IOException("无法创建回收副本");
                JSONObject root=prepared==null?store.metadata():prepared;JSONArray all=root.getJSONArray("books"),one=new JSONArray();
                for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(book.id))one.put(all.getJSONObject(i));root.put("books",one);
                JSONArray categories=root.getJSONArray("categories"),used=new JSONArray();for(int i=0;i<categories.length();i++)if(book.categoryIds.contains(categories.getJSONObject(i).getString("id")))used.put(categories.getJSONObject(i));root.put("categories",used);
                try(FileOutputStream out=new FileOutputStream(new File(library,"index.json"))){out.write(root.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                copy(store.file(book),new File(library,book.id+".txt"),TextDocument.MAX_BYTES);
                if(store.cover(book).isFile())copy(store.cover(book),new File(new File(library,"covers"),book.id+".jpg"),BackupArchive.MAX_COVER);
                if(!staging.renameTo(home))throw new IOException("无法保存回收记录，书籍未移出");
                Entry entry=new Entry(home);removed=true;return entry;
            }
        }finally{BackupArchive.clean(staging);if(!removed)BackupArchive.clean(home);}
    }
    synchronized ArrayList<Entry> list(long now){ArrayList<Entry> entries=new ArrayList<>();File[] files=directory.listFiles();if(files!=null)for(File home:files){if(!home.isDirectory()||home.getName().startsWith("pending-"))continue;try{Entry entry=new Entry(home);if(now-entry.removedAt>=RETENTION)BackupArchive.clean(home);else entries.add(entry);}catch(Exception ignored){/* Preserve unreadable data for recovery, rather than silently deleting it. */}}Collections.sort(entries,(a,b)->Long.compare(b.removedAt,a.removedAt));return entries;}
    synchronized LibraryStore.Book restore(LibraryStore store,Entry entry)throws Exception{LibraryStore.Book book=store.restoreFrom(entry.snapshot,entry.book);BackupArchive.clean(entry.home);return book;}
    synchronized void delete(Entry entry)throws IOException{BackupArchive.clean(entry.home);if(entry.home.exists())throw new IOException("未能完全清理，请重试");}
    synchronized long bytes(){return size(directory);}
    private static long size(File file){if(!file.isDirectory())return file.length();long total=0;File[] children=file.listFiles();if(children!=null)for(File child:children)total+=size(child);return total;}
    synchronized void clear()throws IOException{File[] files=directory.listFiles();if(files!=null)for(File child:files){BackupArchive.clean(child);if(child.exists())throw new IOException("部分回收记录未能清理，请重试");}}
    private static void copy(File from,File to,long limit)throws IOException{try(InputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(to)){BackupArchive.copy(in,out,limit);out.getFD().sync();}}
}
