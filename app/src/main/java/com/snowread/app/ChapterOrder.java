package com.snowread.app;

/** Projection keeps chapter identity and the current anchor stable under either order. */
final class ChapterOrder {
    final int count,current;
    boolean descending;
    ChapterOrder(int count,int current,boolean descending){this.count=Math.max(0,count);this.current=Math.max(0,Math.min(current,count-1));this.descending=descending;}
    int chapter(int row){if(row<0||row>=count)throw new IndexOutOfBoundsException();return descending?count-1-row:row;}
    int row(int chapter){if(chapter<0||chapter>=count)throw new IndexOutOfBoundsException();return descending?count-1-chapter:chapter;}
    int currentRow(){return count==0?-1:row(current);}
    int state(int row){return Integer.compare(chapter(row),current);}
}
