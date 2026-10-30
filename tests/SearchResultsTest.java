package com.snowread.app;

import java.util.*;

public final class SearchResultsTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    static void verify(String text,String term) {
        SearchResults r=SearchResults.find(text,term,()->false);int previous=-1;
        for(SearchResults.Hit h:r.hits){check(h.offset>previous);previous=h.offset;check(text.startsWith(term,h.offset));check(h.preview.substring(h.matchStart,h.matchEnd).equals(term.replace('\n',' ').replace('\r',' ')));
            for(int i=0;i<h.preview.length();i++){char c=h.preview.charAt(i);if(Character.isHighSurrogate(c)){check(i+1<h.preview.length()&&Character.isLowSurrogate(h.preview.charAt(i+1)));i++;}else check(!Character.isLowSurrogate(c));}}
    }
    public static void main(String[] args) {
        check(SearchResults.find("abc","",()->false).total==0);check(SearchResults.find("abc","z",()->false).total==0);
        check(SearchResults.find("aaaa","aa",()->false).total==2);check(SearchResults.find("雪雪雪","雪",()->false).total==3);check(SearchResults.find("Snow snow","Snow",()->false).total==1);
        SearchResults capped=SearchResults.find(String.join("",Collections.nCopies(2500,"雪")),"雪",()->false);check(capped.total==2500);check(capped.hits.size()==1000);
        check(SearchResults.find("雪雪雪","雪",()->true)==null);final int[] visits={0};check(SearchResults.find("雪雪雪","雪",()->++visits[0]>1)==null);
        String odd="x"+String.join("",Collections.nCopies(20000,"a"));final int[] polled={0};
        check(SearchResults.find(odd,"aa",1,odd.length(),false,()->++polled[0]>=3)==null);check(polled[0]==3);
        verify("雪","雪");verify("第一章 春天\n雪融了，雪又落下。","雪");verify("abc\ndef","c\nd");
        for(int i=0;i<80;i++){String text=String.join("",Collections.nCopies(i,"😀"))+"融雪"+String.join("",Collections.nCopies(90,"🌱"));verify(text,"融雪");verify(text,"😀");}
        System.out.println("SearchResultsTest: "+checks+" checks passed; positions, context, Unicode, limits and cancellation");
    }
}
