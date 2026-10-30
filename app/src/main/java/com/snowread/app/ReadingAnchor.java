package com.snowread.app;

import java.util.*;

/** Stable semantic location alongside the legacy UTF-16 offset. No edits to source text. */
final class ReadingAnchor {
    final String chapter,volume,context;
    final int occurrence,contextOffset,offset,length;
    final double ratio,wholeRatio;
    ReadingAnchor(String chapter,String volume,int occurrence,String context,int contextOffset,int offset,int length,double ratio,double wholeRatio){this.chapter=chapter;this.volume=volume;this.occurrence=occurrence;this.context=context;this.contextOffset=contextOffset;this.offset=offset;this.length=length;this.ratio=ratio;this.wholeRatio=wholeRatio;}
    static String normal(String s){return s.replaceAll("[\\s\u3000\uFEFF]+","").toLowerCase(Locale.ROOT);}
    static final class Resolver {
        final TextDocument doc;
        final HashMap<String,ArrayList<Integer>> chapters=new HashMap<>();
        Resolver(TextDocument doc){this.doc=doc;for(int i=0;i<doc.chapters.size();i++){TextDocument.Chapter c=doc.chapters.get(i);String key=key(c);ArrayList<Integer> rows=chapters.get(key);if(rows==null){rows=new ArrayList<>();chapters.put(key,rows);}rows.add(i);}}
        private String key(TextDocument.Chapter c){return normal(c.title)+"\n"+(c.volumeIndex<0?"":normal(doc.volumeTitle(c)));}
        ReadingAnchor capture(int requested){int at=safe(doc.text,requested),index=doc.chapterAt(at);TextDocument.Chapter c=doc.chapters.get(index);int end=doc.sectionEnd(index);int headingEnd=c.title.equals("开始阅读")?-1:doc.text.indexOf('\n',c.offset);int bodyStart=headingEnd>=0&&at>headingEnd?headingEnd+1:c.offset;int from=safe(doc.text,Math.max(bodyStart,at-16)),until=Math.min(end,at+32);if(until<doc.text.length()&&Character.isLowSurrogate(doc.text.charAt(until)))until++;String context=doc.text.substring(from,until);return new ReadingAnchor(c.title,c.volumeIndex<0?"":doc.volumeTitle(c),chapters.get(key(c)).indexOf(index),context,at-from,at,doc.text.length(),(at-c.offset)/(double)Math.max(1,end-c.offset-1),at/(double)Math.max(1,doc.text.length()-1));}
        int resolve(ReadingAnchor a){
            if(a==null)return -1;ArrayList<Integer> candidates=chapters.get(normal(a.chapter)+"\n"+normal(a.volume));
            if(candidates!=null&&!candidates.isEmpty()){
                // Duplicate chapter names are disambiguated by occurrence within the same volume.
                if(a.occurrence>=candidates.size())return -1;int index=candidates.get(Math.max(0,a.occurrence));TextDocument.Chapter c=doc.chapters.get(index);int end=doc.sectionEnd(index),expected=safe(doc.text,c.offset+(int)Math.round(a.ratio*Math.max(0,end-c.offset-1)));
                int exact=context(a,expected,c.offset,end);if(exact>=0)return exact;return expected;
            }
            int predicted=safe(doc.text,(int)Math.round(a.wholeRatio*Math.max(0,doc.text.length()-1)));
            // No matching heading: accept exact nearby context only. Never invent bookmark positions.
            return context(a,predicted,Math.max(0,predicted-4096),Math.min(doc.text.length(),predicted+4096));
        }
        private int context(ReadingAnchor a,int expected,int from,int end){if(a.context.length()<5)return -1;int begin=expected-a.contextOffset;if(begin>=from&&begin+a.context.length()<=end&&doc.text.regionMatches(begin,a.context,0,a.context.length()))return safe(doc.text,expected);int hit=doc.text.indexOf(a.context,from);if(hit<0||hit+a.context.length()>end)return -1;int next=doc.text.indexOf(a.context,hit+1);if(next>=0&&next+a.context.length()<=end)return -1;return safe(doc.text,hit+a.contextOffset);}
    }
    static int safe(String text,int at){at=Math.max(0,Math.min(at,Math.max(0,text.length()-1)));if(at>0&&at<text.length()&&Character.isLowSurrogate(text.charAt(at))&&Character.isHighSurrogate(text.charAt(at-1)))at--;return at;}
    static final class Unmapped {
        final ReadingAnchor anchor;final String note;
        Unmapped(ReadingAnchor anchor,String note){this.anchor=anchor;this.note=note==null?"":note;}
    }
}
