package com.snowread.app;

import java.util.*;

/** Immutable, lazily extended page boundaries; independent of Android text measurement. */
public final class PageIndex {
    public interface Fitter {int fit(int start,int sectionEnd);}
    public interface Cancellation {boolean cancelled();}
    public final int sectionStart,sectionEnd;
    private final int[] boundaries;
    private PageIndex(int start,int end,int[] boundaries) {sectionStart=start;sectionEnd=end;this.boundaries=boundaries;}
    public static PageIndex extend(PageIndex previous,int start,int end,int target,Fitter fitter,Cancellation cancellation) {
        if(start<0 || end<=start) throw new IllegalArgumentException("Invalid section");
        if(previous!=null && (previous.sectionStart!=start || previous.sectionEnd!=end)) throw new IllegalArgumentException("Wrong section cache");
        ArrayList<Integer> points=new ArrayList<>();
        if(previous==null) points.add(start);else for(int p:previous.boundaries) points.add(p);
        int wanted=Math.max(start,Math.min(target,end-1));
        while(points.get(points.size()-1)<=wanted && points.get(points.size()-1)<end) {
            if(cancellation.cancelled()) return null;
            int at=points.get(points.size()-1),next=fitter.fit(at,end);
            if(next<=at || next>end) throw new IllegalStateException("Page measurement did not advance within section");
            points.add(next);
        }
        if(cancellation.cancelled()) return null;
        int[] result=new int[points.size()];for(int i=0;i<result.length;i++) result[i]=points.get(i);
        return new PageIndex(start,end,result);
    }
    public boolean contains(int offset) {return offset>=sectionStart && offset<boundaries[boundaries.length-1];}
    public boolean complete() {return boundaries[boundaries.length-1]==sectionEnd;}
    public int pageCount() {return boundaries.length-1;}
    public int pageAt(int offset) {
        int at=Arrays.binarySearch(boundaries,offset);
        return Math.max(0,Math.min(at>=0?at:-at-2,pageCount()-1));
    }
    public int start(int page) {return boundaries[page];}
    public int end(int page) {return boundaries[page+1];}
}
