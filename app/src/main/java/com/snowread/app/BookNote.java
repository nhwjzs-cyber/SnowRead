package com.snowread.app;

import java.util.*;

/** Immutable range annotation; the source TXT is never modified. */
final class BookNote {
    static final int MAX_COUNT=5000,MAX_QUOTE=8000,MAX_NOTE=4000;
    final String id,quote,note;final int start,end,style;final long created;
    final ReadingAnchor beginAnchor,endAnchor;
    BookNote(String id,int start,int end,String quote,String note,int style,long created,ReadingAnchor begin,ReadingAnchor finish){this.id=id;this.start=start;this.end=end;this.quote=quote;this.note=note;this.style=style;this.created=created;beginAnchor=begin;endAnchor=finish;}
    boolean located(){return start>=0;}
    boolean valid(int length){return id!=null&&id.matches("[a-f0-9-]{36}")&&quote!=null&&!quote.isEmpty()&&quote.length()<=MAX_QUOTE&&note!=null&&note.length()<=MAX_NOTE&&style>=0&&style<=1&&created>=0&&beginAnchor!=null&&endAnchor!=null&&((start==-1&&end==-1)||(start>=0&&end>start&&end<=length&&end-start==quote.length()));}
    static BookNote create(TextDocument doc,int start,int end,String note,int style){
        start=ReadingAnchor.safe(doc.text,start);end=Math.min(doc.text.length(),Math.max(start+1,end));if(end<doc.text.length()&&Character.isLowSurrogate(doc.text.charAt(end)))end++;
        ReadingAnchor.Resolver r=new ReadingAnchor.Resolver(doc);
        BookNote n=new BookNote(UUID.randomUUID().toString(),start,end,doc.text.substring(start,end),note,style,System.currentTimeMillis(),r.capture(start),r.capture(end-1));
        if(!n.valid(doc.text.length()))throw new IllegalArgumentException("选段最多8000字，笔记最多4000字");return n;
    }
    BookNote edit(String value,int kind){return new BookNote(id,start,end,quote,value,kind,created,beginAnchor,endAnchor);}
    BookNote rebind(TextDocument doc,int at,int until){BookNote n=create(doc,at,until,note,style);return new BookNote(id,n.start,n.end,n.quote,n.note,style,created,n.beginAnchor,n.endAnchor);}
    static ArrayList<BookNote> remap(Collection<BookNote> notes,TextDocument doc){
        ArrayList<BookNote> result=new ArrayList<>();ReadingAnchor.Resolver r=new ReadingAnchor.Resolver(doc);
        for(BookNote n:notes){int at=r.resolve(n.beginAnchor);boolean exact=at>=0&&at+n.quote.length()<=doc.text.length()&&doc.text.regionMatches(at,n.quote,0,n.quote.length());
            if(!exact){int predicted=ReadingAnchor.safe(doc.text,(int)(n.beginAnchor.wholeRatio*Math.max(0,doc.text.length()-1))),lo=Math.max(0,predicted-4096),hi=Math.min(doc.text.length(),predicted+4096+n.quote.length());String window=doc.text.substring(lo,hi);int hit=window.indexOf(n.quote),next=hit<0?-1:window.indexOf(n.quote,hit+1);if(n.quote.length()>=5&&hit>=0&&next<0){at=lo+hit;exact=true;}}
            result.add(exact?new BookNote(n.id,at,at+n.quote.length(),n.quote,n.note,n.style,n.created,r.capture(at),r.capture(at+n.quote.length()-1)):new BookNote(n.id,-1,-1,n.quote,n.note,n.style,n.created,n.beginAnchor,n.endAnchor));
        }return result;
    }
}
