package com.snowread.app;

import org.json.*;

public final class ScriptModeStorageTest {
    public static void main(String[] args)throws Exception {
        int checks=0;LibraryStore.Book original=new LibraryStore.Book("a","测试","UTF-8",100);
        for(int mode=0;mode<=2;mode++){
            original.scriptMode=mode;JSONObject json=new JSONObject();BookExtras.write(original,json);
            LibraryStore.Book loaded=new LibraryStore.Book("a","测试","UTF-8",100);BookExtras.read(loaded,json);
            if(loaded.scriptMode!=mode)throw new AssertionError();checks++;
            LibraryStore.Book copied=new LibraryStore.Book("a","测试","UTF-8",100);BookExtras.copy(original,copied);
            if(copied.scriptMode!=mode)throw new AssertionError();checks++;
        }
        LibraryStore.Book legacy=new LibraryStore.Book("a","测试","UTF-8",100);BookExtras.read(legacy,new JSONObject());if(legacy.scriptMode!=0)throw new AssertionError();checks++;
        for(int mode:new int[]{-1,3,100})try{BookExtras.read(legacy,new JSONObject().put("scriptMode",mode));throw new AssertionError();}catch(java.io.IOException expected){checks++;}
        java.io.File home=java.nio.file.Files.createTempDirectory(java.nio.file.Paths.get(args[0]),"script-").toFile();
        LibraryStore store=new LibraryStore(home);byte[] bytes="第一章 故事\n理发师发现头发，故事继续。".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        LibraryStore.Book book=store.add(bytes,"简繁测试.txt",null);book.scriptMode=2;book.offset=9;book.marks.add(12);store.save();
        LibraryStore reloaded=new LibraryStore(home);LibraryStore.Book loaded=reloaded.find(book.id);
        if(loaded.scriptMode!=2||loaded.offset!=9||!loaded.marks.contains(12))throw new AssertionError();checks++;
        if(!java.util.Arrays.equals(bytes,java.nio.file.Files.readAllBytes(reloaded.file(loaded).toPath())))throw new AssertionError();checks++;
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();BackupArchive.export(reloaded,output,(a,b,c)->{});
        java.io.File target=java.nio.file.Files.createTempDirectory(java.nio.file.Paths.get(args[0]),"script-restore-").toFile();
        try(BackupArchive.Prepared backup=BackupArchive.prepare(new java.io.ByteArrayInputStream(output.toByteArray()),target,(a,b,c)->{})){
            LibraryStore restored=BackupArchive.restore(backup,new LibraryStore(target),true,(a,b,c)->{});
            LibraryStore.Book record=restored.find(book.id);if(record.scriptMode!=2||record.offset!=9||!record.marks.contains(12))throw new AssertionError();checks++;
        }
        System.out.println("ScriptModeStorageTest: "+checks+" checks passed; optional field, copy, legacy and invalid restore records");
    }
}
