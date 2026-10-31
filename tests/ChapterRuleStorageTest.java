package com.snowread.app;

import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Storage half of the chapter-rule tests: per-book rule survives write/read/copy, invalid
 *  rules are rejected, defaults fall back to AUTO, and LibraryStore.load applies the rule. */
public final class ChapterRuleStorageTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    private static String hash64(){StringBuilder builder=new StringBuilder();for(int i=0;i<64;i++)builder.append('a');return builder.toString();}
    public static void main(String[] args)throws Exception{
        LibraryStore.Book book=new LibraryStore.Book(hash64(),"样本","UTF-8",100);
        book.chapterRule=ChapterRule.ENGLISH;
        JSONObject json=new JSONObject();BookExtras.write(book,json);
        check(json.getInt("chapterRule")==ChapterRule.ENGLISH);
        LibraryStore.Book reloaded=new LibraryStore.Book(hash64(),"样本","UTF-8",100);
        BookExtras.read(reloaded,json);check(reloaded.chapterRule==ChapterRule.ENGLISH);
        LibraryStore.Book copied=new LibraryStore.Book(hash64(),"副本","UTF-8",100);BookExtras.copy(reloaded,copied);
        check(copied.chapterRule==ChapterRule.ENGLISH);
        BookExtras.read(reloaded,new JSONObject());check(reloaded.chapterRule==ChapterRule.AUTO);
        boolean rejected=false;try{BookExtras.read(reloaded,new JSONObject("{\"chapterRule\":7}"));}catch(IOException expected){rejected=true;}
        check(rejected);
        // LibraryStore.load applies the stored rule to the decoded text.
        File home=Files.createTempDirectory(Paths.get(args[0]),"rule-").toFile();
        LibraryStore store=new LibraryStore(home);
        String text="第一章 甲\n内容\n第二章 乙\n内容\n";
        LibraryStore.Book saved=store.add(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),"标题",null);
        check(saved!=null&&store.load(saved).numberedChapters.size()==2);
        saved.chapterRule=ChapterRule.NUMBER_LINES;
        TextDocument ruledLoad=store.load(saved);
        check(ruledLoad.numberedChapters.isEmpty()&&ruledLoad.chapters.size()==1&&"开始阅读".equals(ruledLoad.chapters.get(0).title));
        System.out.println("ChapterRuleStorageTest: "+checks+" checks passed; rule persistence, strict validation, default fallback and load application");
    }
}
