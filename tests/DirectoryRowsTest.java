package com.snowread.app;

public final class DirectoryRowsTest {
    static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    public static void main(String[] args){
        TextDocument doc=new TextDocument("第一卷 初雪\n第一章 冬夜\n正文😀\n第二章 梅花\n花开\n第二卷 融雪\n第一章 春天\n正文\n第二章 新芽\n生长","UTF-8");DirectoryRows m=new DirectoryRows(doc,false);check(m.rows.size()==6);m.toggle(0);check(m.rows.size()==4);check(m.chapter(1)==3);m.query="冬夜";m.rebuild();check(m.rows.size()==1&&m.chapter(0)==1);m.descending=true;m.rebuild();check(m.chapter(0)==1);int current=m.locate(2);check(m.query.isEmpty()&&m.folded.isEmpty());check(current==3&&m.chapter(current)==2);m.query="融雪";m.rebuild();check(m.rows.size()==3&&m.chapter(0)==5&&m.chapter(2)==3);
        for(int c=0;c<doc.chapters.size();c++){m.folded.add(0);m.folded.add(1);int p=m.locate(c);check(p>=0&&m.chapter(p)==c);}
        StringBuilder many=new StringBuilder();for(int i=1;i<=10000;i++)many.append("第").append(i).append("章 标题\n正文\n");TextDocument full=new TextDocument(many.toString(),"UTF-8");check(!full.chaptersTruncated&&full.numberedChapters.size()==10000);many.append("第一万零一章 最后\n末尾😀");TextDocument truncated=new TextDocument(many.toString(),"UTF-8");check(truncated.chaptersTruncated&&truncated.numberedChapters.size()==10000);check(truncated.text.endsWith("末尾😀"));check(truncated.rowAt(truncated.text.length()-1)<truncated.starts.size());
        System.out.println("DirectoryRowsTest: "+checks+" checks passed; filtering, folded volumes, descending identity and explicit chapter cap");
    }
}
