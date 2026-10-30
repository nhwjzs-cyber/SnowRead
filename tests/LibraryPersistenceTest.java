package com.snowread.app;

import android.content.ContextWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Real library serialization and Android JSON, with a JVM file adapter (not a device test). */
public final class LibraryPersistenceTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    private static void write(File path,String value)throws Exception{Files.write(path.toPath(),value.getBytes(StandardCharsets.UTF_8));}
    private static String read(File path)throws Exception{return new String(Files.readAllBytes(path.toPath()),StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception {
        File home=Files.createTempDirectory(Paths.get(args[0]),"library-").toFile();File dir=new File(home,"library");check(dir.mkdir());
        ContextWrapper context=new ContextWrapper(null){@Override public File getFilesDir(){return home;}};
        File index=new File(dir,"index.json");String id=String.join("",Collections.nCopies(64,"a"));
        JSONObject old=new JSONObject().put("id",id).put("title","旧书架的雪").put("encoding","UTF-8").put("length",100).put("opened",12345).put("offset",17).put("marks",new JSONArray(Arrays.asList(4,23)));
        String legacy=new JSONArray().put(old).toString();write(index,legacy);write(new File(dir,id+".txt"),"第一章 冰雪\n这是保存的正文。");
        File covers=new File(dir,"covers");check(covers.mkdir());File cover=new File(covers,id+".jpg");write(cover,"cover-marker");
        LibraryStore store=new LibraryStore(context);check(store.books.size()==1);LibraryStore.Book b=store.books.get(0);
        check(b.offset==17&&b.opened==12345&&b.marks.equals(Arrays.asList(4,23)));check(!b.pinned&&b.categoryIds.isEmpty());check(read(index).equals(legacy));
        String category=store.organization.add("小说"),empty=store.organization.add("稍后读");
        store.organize(()->{store.organization.assign(Collections.singletonList(b),category);store.organization.pin(Collections.singletonList(b),true);});
        JSONObject saved=new JSONObject(read(index));check(saved.getInt("version")==3);check(saved.getJSONArray("books").length()==1);
        LibraryStore reloaded=new LibraryStore(context);LibraryStore.Book restored=reloaded.books.get(0);
        check(restored.id.equals(id)&&restored.title.equals("旧书架的雪")&&restored.encoding.equals("UTF-8"));check(restored.pinned&&restored.categoryIds.contains(category));check(restored.offset==17&&restored.marks.equals(Arrays.asList(4,23)));check(reloaded.organization.categories.containsKey(empty));check(read(cover).equals("cover-marker"));check(reloaded.load(restored).text.contains("这是保存的正文"));
        final LibraryStore renameStore=reloaded;renameStore.organize(()->renameStore.organization.rename(category,"想读"));reloaded=new LibraryStore(context);check(reloaded.books.get(0).categoryIds.contains(category));check(reloaded.organization.categories.get(category).equals("想读"));
        final LibraryStore current=reloaded;
        try{current.organize(()->{current.organization.delete(category,current.books);current.organization.pin(current.books,false);throw new IllegalStateException("abort");});throw new AssertionError("Expected rollback");}catch(IllegalStateException expected){}
        check(current.books.get(0).pinned&&current.books.get(0).categoryIds.contains(category));check(current.organization.categories.containsKey(category));
        File blocked=new File(dir,"index.json.new");check(blocked.mkdir());String beforeFailure=read(index);
        try{current.organize(()->{current.organization.delete(category,current.books);current.organization.pin(current.books,false);});throw new AssertionError("Expected write failure");}catch(IOException expected){}
        check(current.books.get(0).pinned&&current.books.get(0).categoryIds.contains(category));check(current.organization.categories.containsKey(category));check(read(index).equals(beforeFailure));Files.delete(blocked.toPath());
        current.organize(()->current.organization.delete(category,current.books));reloaded=new LibraryStore(context);check(reloaded.books.get(0).categoryIds.isEmpty());check(reloaded.books.get(0).pinned);check(reloaded.books.get(0).marks.equals(Arrays.asList(4,23)));
        current.add("第一章 另一册\n这是另一本书。".getBytes(StandardCharsets.UTF_8),"另一册",null);current.organize(()->current.organization.pin(current.books,true));
        reloaded=new LibraryStore(context);check(reloaded.books.size()==2);check(reloaded.organization.visible(reloaded.books,ShelfOrganization.PINNED,"").size()==2);
        // A real v2 single-category index upgrades to two memberships and rolls back both on failure.
        String v2id=UUID.randomUUID().toString();JSONObject v2book=new JSONObject(old.toString()).put("category",v2id).put("pinned",true);
        JSONObject v2=new JSONObject().put("version",2).put("categories",new JSONArray().put(new JSONObject().put("id",v2id).put("name","旧分类"))).put("books",new JSONArray().put(v2book));
        write(index,v2.toString());final LibraryStore upgraded=new LibraryStore(context);check(upgraded.books.get(0).categoryIds.equals(Collections.singleton(v2id)));
        String addedCategory=upgraded.organization.add("正在追");upgraded.organize(()->upgraded.organization.updateMembership(upgraded.books,Collections.singleton(addedCategory),Collections.emptySet()));
        LibraryStore multi=new LibraryStore(context);check(multi.books.get(0).categoryIds.equals(new LinkedHashSet<>(Arrays.asList(v2id,addedCategory))));check(multi.books.get(0).offset==17&&multi.books.get(0).marks.equals(Arrays.asList(4,23)));
        check(new JSONObject(read(index)).getInt("version")==3);
        File blockedMulti=new File(dir,"index.json.new");check(blockedMulti.mkdir());try{upgraded.organize(()->upgraded.organization.assignAll(upgraded.books,Collections.emptySet()));throw new AssertionError("Expected write failure");}catch(IOException expected){}
        check(upgraded.books.get(0).categoryIds.size()==2);Files.delete(blockedMulti.toPath());upgraded.organize(()->upgraded.organization.delete(v2id,upgraded.books));
        multi=new LibraryStore(context);check(multi.books.get(0).categoryIds.equals(Collections.singleton(addedCategory)));check(multi.books.get(0).pinned);
        // Unsupported or corrupt metadata must fail closed without rewriting the index.
        String future=new JSONObject().put("version",999).toString();write(index,future);
        try{new LibraryStore(context);throw new AssertionError("Expected unsupported version");}catch(IOException expected){}check(read(index).equals(future));
        write(index,"{broken");try{new LibraryStore(context);throw new AssertionError("Expected corrupt index");}catch(JSONException expected){}check(read(index).equals("{broken"));
        System.out.println("LibraryPersistenceTest: "+checks+" checks passed; legacy upgrade, reload, rollback and data preservation");
    }
}
