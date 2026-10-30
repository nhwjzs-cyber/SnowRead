package com.snowread.app;

/** Single-utterance state machine; owns offsets and rejects stale engine callbacks. */
final class SpeechQueue {
    enum State {IDLE,PREPARING,PLAYING,PAUSED,ERROR}
    enum Completion {NEXT,CHAPTER_END,TIMER,END}
    final TextDocument document;
    final int maxChars;
    State state=State.PAUSED;
    TextChunker.SpeechChunk current,next;
    int generation,scopeEnd;
    boolean autoNext=true;
    private final java.util.ArrayDeque<Integer> history=new java.util.ArrayDeque<>();
    SpeechQueue(TextDocument doc,int offset,int maxChars){document=doc;this.maxChars=Math.max(2,Math.min(2800,maxChars));scopeEnd=doc.text.length();seek(offset);}
    void seek(int offset){generation++;history.clear();current=TextChunker.next(document.text,offset,scopeEnd,maxChars);prefetch();}
    void setScope(int end){scopeEnd=Math.max(0,Math.min(document.text.length(),end));seek(position());}
    int position(){return current==null?Math.max(0,scopeEnd-1):current.start;}
    String issue(){generation++;state=State.PLAYING;return generation+":"+position();}
    boolean accepts(String id){return id!=null&&id.equals(generation+":"+position())&&state==State.PLAYING;}
    void pause(){generation++;state=State.PAUSED;}
    void error(){generation++;state=State.ERROR;}
    void stop(){generation++;state=State.IDLE;}
    int chapter(){return document.chapterAt(position());}
    boolean title(){if(current==null)return false;TextDocument.Chapter heading=document.chapters.get(chapter());if(heading.title.equals("开始阅读"))return false;int end=document.text.indexOf('\n',heading.offset);return current.start>=heading.offset&&current.start<(end<0?document.text.length():end);}
    boolean atChapterEnd(){return current!=null&&(next==null||document.chapterAt(next.start)!=document.chapterAt(current.start));}
    boolean advance(){if(current!=null){if(history.size()>=1024)history.removeFirst();history.addLast(current.start);}current=next;generation++;prefetch();return current!=null;}
    Completion finish(boolean autoContinue,boolean timerDue){boolean boundary=atChapterEnd();advance();if(timerDue){pause();return Completion.TIMER;}if(boundary&&!autoContinue){pause();return Completion.CHAPTER_END;}if(current==null){pause();return Completion.END;}return Completion.NEXT;}
    void previous(){int target=0;if(!history.isEmpty())target=history.removeLast();else if(current!=null&&current.start>0){int section=document.chapterAt(current.start);int start=document.chapters.get(section).offset;if(start==current.start&&section>0)start=document.chapters.get(section-1).offset;TextChunker.SpeechChunk candidate=TextChunker.next(document.text,start,current.start,maxChars),previous=null;while(candidate!=null&&candidate.start<current.start){previous=candidate;candidate=TextChunker.next(document.text,candidate.end,current.start,maxChars);}if(previous!=null)target=previous.start;}generation++;current=TextChunker.next(document.text,target,scopeEnd,maxChars);prefetch();}
    void previous(boolean readTitles,boolean skipNumbers,boolean skipUrls){int before;do{before=position();previous();}while(current!=null&&position()<before&&(!readTitles&&title()||skipNumbers&&TextChunker.pageNumber(current)||skipUrls&&TextChunker.withoutUrls(current)==null));}
    void chapter(int direction){int index=Math.max(0,Math.min(document.chapters.size()-1,chapter()+direction));scopeEnd=document.text.length();seek(document.chapters.get(index).offset);}
    private void prefetch(){next=current==null?null:TextChunker.next(document.text,current.end,scopeEnd,maxChars);}
}
