package com.snowread.app;

import java.util.*;

/** Literal full-text search with bounded result storage and Unicode-safe context. */
final class SearchResults {
    interface Cancellation {boolean cancelled();}
    static final int LIMIT=1000;
    static final class Hit {
        final int offset,matchStart,matchEnd;
        final String preview;
        Hit(String text,int offset,int length) {
            this.offset=offset;
            int start=Math.max(0,offset-38),end=Math.min(text.length(),offset+length+62);
            if(start>0&&Character.isLowSurrogate(text.charAt(start)))start--;
            if(end<text.length()&&Character.isLowSurrogate(text.charAt(end)))end++;
            String prefix=start>0?"…":"";
            preview=prefix+text.substring(start,end).replace('\n',' ').replace('\r',' ')+(end<text.length()?"…":"");
            matchStart=prefix.length()+offset-start;matchEnd=matchStart+length;
        }
    }
    final ArrayList<Hit> hits=new ArrayList<>();
    int total;
    static SearchResults find(String text,String term,Cancellation cancellation) {
        return find(text,term,0,text.length(),false,cancellation);
    }
    static SearchResults find(String text,String term,int begin,int limit,boolean ignoreCase,Cancellation cancellation){
        SearchResults result=new SearchResults();if(term.isEmpty())return result;int end=Math.min(text.length(),limit),from=Math.max(0,begin);char first=term.charAt(0),upper=Character.toUpperCase(first),lower=Character.toLowerCase(first);
        int scanned=0;if(cancellation.cancelled())return null;while(from+term.length()<=end){if((++scanned&1023)==0&&cancellation.cancelled())return null;boolean matches;if(ignoreCase){char c=text.charAt(from);matches=(c==first||Character.toUpperCase(c)==upper||Character.toLowerCase(c)==lower)&&text.regionMatches(true,from,term,0,term.length());}else matches=text.regionMatches(from,term,0,term.length());if(matches){result.total++;if(result.hits.size()<LIMIT)result.hits.add(new Hit(text,from,term.length()));from+=term.length();}else from++;}
        return cancellation.cancelled()?null:result;
    }
}
