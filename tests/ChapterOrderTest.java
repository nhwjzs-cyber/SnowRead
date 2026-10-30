package com.snowread.app;

public final class ChapterOrderTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    public static void main(String[] args){
        check(new ChapterOrder(0,0,false).currentRow()==-1);
        for(int count:new int[]{1,2,50,500})for(int anchor:new int[]{0,count/2,count-1}){
            ChapterOrder order=new ChapterOrder(count,anchor,false);
            for(int pass=0;pass<4;pass++){
                check(order.chapter(order.currentRow())==anchor);check(order.state(order.currentRow())==0);
                for(int row=0;row<count;row++){int chapter=order.chapter(row);check(order.row(chapter)==row);check(order.state(row)==Integer.compare(chapter,anchor));check(chapter==(order.descending?count-1-row:row));}
                order.descending=!order.descending;
            }
        }
        System.out.println("ChapterOrderTest: "+checks+" checks passed; reversed identity, progress states and return-to-current anchor");
    }
}
