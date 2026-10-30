package com.snowread.app;
import java.io.*;
import java.nio.charset.*;

public final class TextDocumentTest {
    static int checks=0;
    static void check(boolean ok,String message) { checks++;if(!ok) throw new AssertionError(message); }
    static void reject(byte[] data,String charset) throws Exception {
        try {TextDocument.decode(data,charset);throw new AssertionError("Expected invalid document");}
        catch(IOException expected) {checks++;}
    }
    public static void main(String[] args) throws Exception {
        String novel="一封来信\r\n第一章 山间\r\n你好，世界。\r\n第二十章 夜色\r\n再见。";
        TextDocument utf8=TextDocument.decode(novel.getBytes("UTF-8"),null);
        check(utf8.encoding.equals("UTF-8"),"UTF-8 detection");
        check(!utf8.text.contains("\r"),"CRLF normalization");
        check(utf8.chapters.size()==3,"Chinese chapter recognition and preface");
        check(utf8.chapters.get(1).title.equals("第一章 山间"),"chapter title");
        check(utf8.text.substring(utf8.chapters.get(2).offset).startsWith("第二十章"),"chapter global offset");
        TextDocument gb=TextDocument.decode(novel.getBytes("GB18030"),null);
        check(gb.encoding.equals("GB18030"),"GB18030 detection");check(gb.text.equals(utf8.text),"Chinese GB18030 roundtrip");
        for (String encoding:new String[]{"UTF-16LE","UTF-16BE","UTF-8"}) {
            byte[] encoded=("\uFEFF"+novel).getBytes(encoding);
            TextDocument bom=TextDocument.decode(encoded,null);
            check(bom.encoding.equals(encoding),"BOM detection "+encoding);check(bom.text.equals(utf8.text),"BOM stripped "+encoding);
            check(TextDocument.decode(encoded,encoding).text.equals(utf8.text),"manual BOM "+encoding);
        }
        check(TextDocument.decode("abc text\nchapter 12 Evening\nstory\nChapter IV Dawn\nend".getBytes("UTF-8"),null).chapters.size()==3,"English chapters");
        check(TextDocument.decode("序章 风来\n正文\n尾声 日落\n结束".getBytes("UTF-8"),null).chapters.size()==2,"special headings");
        check(TextDocument.decode("abc long enough for UTF16 without BOM".getBytes("UTF-16LE"),null).encoding.equals("UTF-16LE"),"UTF16 without BOM");
        StringBuilder longText=new StringBuilder("第一章 长篇\n");
        for(int i=0;i<15000;i++) longText.append("山🌿间的风与信。");
        TextDocument longDoc=new TextDocument(longText.toString(),"UTF-8");
        StringBuilder rejoined=new StringBuilder();
        for(int i=0;i<longDoc.starts.size();i++) {
            String row=longDoc.row(i);rejoined.append(row);check(row.length()<=1400,"bounded rendering rows");
            check(!Character.isHighSurrogate(row.charAt(row.length()-1)),"no split surrogate pair");
            check(longDoc.rowAt(longDoc.starts.get(i))==i,"row lookup exact");
            check(longDoc.rowAt(longDoc.starts.get(i)+row.length()-1)==i,"row lookup interior");
        }
        check(rejoined.toString().equals(longDoc.text),"chunking never loses or duplicates text");
        check(longDoc.rowAt(Integer.MAX_VALUE)==longDoc.starts.size()-1,"end offset clamps");
        check(longDoc.chapterAt(9999)==0,"chapter lookup");
        reject(new byte[]{0,0,0,0},null);
        reject(new byte[]{(byte)0xff},"UTF-8");
        reject("  \r\n \t".getBytes("UTF-8"),null);
        try {TextDocument.readLimited(new ByteArrayInputStream(new byte[TextDocument.MAX_BYTES+1]));throw new AssertionError("size cap");}
        catch(IOException expected){checks++;}
        try {TextDocument.readLimited(new ByteArrayInputStream(new byte[0]));throw new AssertionError("empty file");}
        catch(IOException expected){checks++;}
        String grouped="前言\n写在前面\n第一卷 山间\n第一章 信\n一段正文\n第 十二 章 林\n十二章正文\n第二卷 海边\n第１章 潮\n海潮带来新的故事\n第十二章 云\n结尾\n后记\n感谢阅读";
        TextDocument groupedDoc=new TextDocument(grouped,"UTF-8");
        check(groupedDoc.volumes.size()==2,"volume heading count");
        check(groupedDoc.numberedChapters.size()==4,"prefaces and volumes excluded from chapter count");
        check(groupedDoc.numberedChapters.get(0).volumeIndex==0,"first volume membership");
        check(groupedDoc.numberedChapters.get(2).volumeIndex==1,"second volume membership");
        check(groupedDoc.numberedChapters.get(3).ordinal==4,"global numbered chapter order");
        check(groupedDoc.findChapters(1,-1,false).size()==2,"duplicate chapter number across volumes");
        check(groupedDoc.findChapters(1,1,true).get(0).title.equals("第１章 潮"),"fullwidth chapter number, volume-scoped jump");
        check(groupedDoc.findChapters(12,0,true).get(0).title.equals("第 十二 章 林"),"spaced Chinese chapter number");
        check(groupedDoc.findChapters(999,-1,false).isEmpty(),"missing chapter is not silently redirected");
        check(groupedDoc.readingTitle(groupedDoc.numberedChapters.get(2).offset).equals("第二卷 海边 · 第１章 潮"),"reading title includes parent volume");
        check(groupedDoc.numberedOrdinalAt(0)==0,"preface is not chapter one");
        check(groupedDoc.numberedOrdinalAt(groupedDoc.numberedChapters.get(2).offset)==3,"numbered progress excludes volume headings");
        for(int i=0;i<groupedDoc.chapters.size();i++) {
            int start=groupedDoc.chapters.get(i).offset,end=groupedDoc.sectionEnd(i);
            check(end>start,"sections never empty");
            check(groupedDoc.chapterAt(start)==i,"heading offset maps to its own section");
            check(groupedDoc.chapterAt(end-1)==i,"section boundary remains in preceding section");
        }
        String[][] numbers={{"零","0"},{"十二","12"},{"十","10"},{"一百零二","102"},{"两千三百一十五","2315"},
            {"十万零三","100003"},{"一亿零三万零二","100030002"},{"二〇二四","2024"},{"００１２","12"},
            {"壹佰贰拾叁","123"},{"IV","4"},{"XII","12"},{"一千零一","1001"},{"九十九","99"}};
        for(String[] example:numbers) check(TextDocument.parseNumber(example[0])==Integer.parseInt(example[1]),"chapter number parser "+example[0]);
        check(TextDocument.parseNumber("")==-1,"empty number rejected");
        check(TextDocument.parseNumber("不是章号")==-1,"invalid number rejected");
        check(TextDocument.parseNumber("999999999999999999999")==-1,"number overflow rejected");
        TextDocument noHeading=new TextDocument("普通正文\n没有章节标题","UTF-8");
        check(noHeading.chapters.size()==1 && noHeading.numberedChapters.isEmpty(),"chapterless file remains readable");
        TextDocument falsePositive=new TextDocument("我们将在第十二章再见。\nChapter civilization is prose, not a number.\n第1章 有效标题\n正文","UTF-8");
        check(falsePositive.numberedChapters.size()==1,"prose and English words are not chapter headings");
        TextDocument traditional=new TextDocument("第一部 開始\n第貳節 故事\n正文","UTF-8");
        check(traditional.volumes.size()==1 && traditional.numberedChapters.get(0).number==2,"traditional numeral and section heading");
        final int[] fits={0};
        PageIndex.Fitter ten=(start,end)-> {fits[0]++;return Math.min(start+10,end);};
        PageIndex pages=PageIndex.extend(null,0,105,0,ten,()->false);
        check(pages.pageCount()==1&&!pages.complete(),"first page indexed lazily");
        check(pages.start(0)==0&&pages.end(0)==10,"first page boundaries");
        check(pages.contains(9)&&!pages.contains(10),"exclusive page end");
        pages=PageIndex.extend(pages,0,105,10,ten,()->false);
        check(pages.pageCount()==2 && pages.pageAt(10)==1,"turning page extends exactly one page");
        int before=fits[0];pages=PageIndex.extend(pages,0,105,2,ten,()->false);
        check(fits[0]==before && pages.pageAt(2)==0,"going back reuses boundaries");
        pages=PageIndex.extend(pages,0,105,104,ten,()->false);
        check(pages.complete()&&pages.pageCount()==11,"jump to end completes index");
        check(pages.start(10)==100&&pages.end(10)==105,"short final page");
        check(pages.pageAt(105)==10,"end sentinel clamps to final page");
        check(PageIndex.extend(null,0,105,90,ten,()->true)==null,"cancel obsolete layout work");
        try {PageIndex.extend(null,0,105,0,(start,end)->start,()->false);throw new AssertionError("zero page advance");}
        catch(IllegalStateException expected){checks++;}
        try {PageIndex.extend(null,0,105,0,(start,end)->end+1,()->false);throw new AssertionError("out of section page");}
        catch(IllegalStateException expected){checks++;}
        try {PageIndex.extend(pages,5,105,5,ten,()->false);throw new AssertionError("wrong section cache");}
        catch(IllegalArgumentException expected){checks++;}
        for(int capacity:new int[]{1,7,31,53}) {
            PageIndex map=PageIndex.extend(null,13,249,248,(start,end)->Math.min(end,start+capacity),()->false);
            int cursor=13;
            for(int i=0;i<map.pageCount();i++) {
                check(map.start(i)==cursor,"no missing or repeated characters between pages");
                check(map.end(i)>cursor && map.end(i)<=249,"page stays within chapter");
                for(int pos=map.start(i);pos<map.end(i);pos++) check(map.pageAt(pos)==i,"character-to-page lookup");
                cursor=map.end(i);
            }
            check(cursor==249,"pagination covers full section");
        }
        System.out.println("PASS: "+checks+" checks; decoding, volumes, chapter jumps, pagination, Unicode and import limits.");
    }
}
