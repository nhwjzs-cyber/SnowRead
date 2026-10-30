package com.snowread.app;

import java.util.*;

public final class ListenCoreTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    static void verify(String text,int max){
        List<TextChunker.SpeechChunk> chunks=TextChunker.split(text,0,max);int last=0;StringBuilder covered=new StringBuilder();
        for(TextChunker.SpeechChunk c:chunks){check(c.start>=last&&c.end>c.start&&c.end<=text.length());check(!c.text.isEmpty()&&c.text.length()<=Math.max(2,max));check(c.start==0||!Character.isLowSurrogate(text.charAt(c.start)));check(c.end==text.length()||!Character.isLowSurrogate(text.charAt(c.end)));for(int i=last;i<c.start;i++)check(Character.isWhitespace(text.charAt(i))||Character.isSpaceChar(text.charAt(i)));covered.append(text.substring(c.start,c.end));last=c.end;
            for(int i=0;i<c.text.length();i++){int a=c.absoluteStart(i),b=c.absoluteEnd(i+1);check(a>=c.start&&b<=c.end&&a<b);check(a==0||!Character.isLowSurrogate(text.charAt(a)));check(b==text.length()||!Character.isLowSurrogate(text.charAt(b)));}
        }
        for(int i=last;i<text.length();i++)check(Character.isWhitespace(text.charAt(i))||Character.isSpaceChar(text.charAt(i)));
        check(covered.toString().replaceAll("[\\s\\p{Z}]+","").equals(text.replaceAll("[\\s\\p{Z}]+","")));
    }
    public static void main(String[] args){
        check(TextChunker.split("",0,300).isEmpty());check(TextChunker.split(" \n　\t",0,300).isEmpty());
        String[] samples={"雪融了。春天来了！你听见了吗？慢一点；别急。","他说：‘你好！’\n她答：\"好。\"", "Dr. Smith reads e.g. novels. The value is 3.14. Next sentence!", "😀🌱雪😀，风🌱。\n再见😀！","没有标点也不能丢掉文字", "一　　片\t\t雪\n另 一 句。"};
        for(String sample:samples)for(int max:new int[]{2,3,7,40,300})verify(sample,max);
        for(int i=0;i<80;i++)verify(String.join("",Collections.nCopies(i,"😀雪🌱"))+"；”下一句。",17);
        TextChunker.SpeechChunk mapped=TextChunker.next("\t甲  \t乙。",0,7,300);check(mapped.text.equals("甲 乙。"));check(mapped.absoluteStart(2)==5&&mapped.absoluteEnd(3)==6);check(mapped.absoluteStart(1)==2&&mapped.absoluteEnd(2)==5);
        check(TextChunker.next("😀",0,1,300)==null);check(TextChunker.next("😀雪",1,4,2).text.equals("😀"));
        String withUrl="甲 https://example.com/x 乙。";TextChunker.SpeechChunk filtered=TextChunker.withoutUrls(TextChunker.next(withUrl,0,withUrl.length(),300));check(filtered.text.equals("甲 乙。"));check(filtered.absoluteStart(2)==withUrl.indexOf("乙"));check(TextChunker.withoutUrls(TextChunker.next("https://example.com",0,19,300))==null);check(TextChunker.pageNumber(TextChunker.next("第123页",0,5,300)));check(!TextChunker.pageNumber(TextChunker.next("第123章 故事",0,8,300)));
        List<TextChunker.SpeechChunk> quoted=TextChunker.split("他说：“听见了！”然后走开。",0,300);check(quoted.get(0).text.endsWith("！”"));
        TextDocument doc=new TextDocument("第一章 雪\n甲。乙。\n第二章 春\n丙。丁。","UTF-8");SpeechQueue queue=new SpeechQueue(doc,doc.text.indexOf("甲"),300);String first=queue.issue();check(queue.accepts(first));queue.pause();check(!queue.accepts(first));check(queue.position()==doc.text.indexOf("甲"));String resumed=queue.issue();check(!first.equals(resumed)&&queue.accepts(resumed));queue.advance();check(queue.position()==doc.text.indexOf("乙"));check(!queue.accepts(resumed));queue.previous();check(queue.position()==doc.text.indexOf("甲"));
        queue.seek(doc.text.indexOf("乙"));queue.issue();check(queue.atChapterEnd());check(queue.finish(false,false)==SpeechQueue.Completion.CHAPTER_END);check(queue.state==SpeechQueue.State.PAUSED&&queue.chapter()==1);
        queue.seek(doc.text.indexOf("乙"));queue.issue();check(queue.finish(true,false)==SpeechQueue.Completion.NEXT);check(queue.chapter()==1);queue.seek(doc.text.indexOf("甲"));queue.issue();check(queue.finish(true,true)==SpeechQueue.Completion.TIMER);check(queue.position()==doc.text.indexOf("乙")&&queue.state==SpeechQueue.State.PAUSED);
        queue.chapter(1);check(queue.position()==doc.chapters.get(1).offset);queue.chapter(-1);check(queue.position()==0);queue.seek(doc.text.indexOf("甲"));queue.setScope(doc.text.indexOf("第二章"));queue.issue();queue.finish(true,false);queue.issue();check(queue.finish(true,false)==SpeechQueue.Completion.END);
        SpeechQueue restored=new SpeechQueue(doc,doc.text.indexOf("乙"),300);check(restored.state==SpeechQueue.State.PAUSED);check(restored.position()==doc.text.indexOf("乙"));restored.error();check(!restored.accepts(restored.generation+":"+restored.position()));restored.stop();check(restored.state==SpeechQueue.State.IDLE);
        TextDocument cleanedDoc=new TextDocument("第一章 雪\n甲。\n123\n乙。\n第二章 春\n丙。","UTF-8");SpeechQueue cleaned=new SpeechQueue(cleanedDoc,cleanedDoc.text.indexOf("乙"),300);cleaned.previous(false,true,true);check(cleaned.position()==cleanedDoc.text.indexOf("甲"));cleaned.seek(cleanedDoc.text.indexOf("丙"));cleaned.previous(false,true,true);check(cleaned.position()==cleanedDoc.text.indexOf("乙"));cleaned.seek(cleanedDoc.chapters.get(1).offset);check(cleaned.title());
        SleepTimer timer=new SleepTimer();timer.minutes(15,1000);check(!timer.due(900000,false));check(timer.due(901000,false));timer.setMode(SleepTimer.CHAPTER);check(!timer.due(Long.MAX_VALUE,false)&&timer.due(0,true));timer.setMode(SleepTimer.SENTENCE);check(timer.due(0,false));timer.restore(SleepTimer.TIME,50,51);check(timer.mode==SleepTimer.NONE);timer.restore(SleepTimer.TIME,500,10);check(timer.mode==SleepTimer.TIME&&timer.deadline==500);timer.clear();check(!timer.due(Long.MAX_VALUE,true));
        System.out.println("ListenCoreTest: "+checks+" checks passed; chunk offsets, Unicode, stale callbacks, pause/seek, chapter completion and sleep timer");
    }
}
