package com.snowread.app;
public final class ReadingTimeCounterTest {
 static int count;static void equal(long actual,long expected){count++;if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args){ReadingTimeCounter c=new ReadingTimeCounter();equal(c.update(0,true,0),0);equal(c.update(15000,true,0),15000);equal(c.update(30000,false,0),15000);equal(c.update(90000,false,0),0);equal(c.update(100000,true,100000),0);equal(c.update(115000,true,100000),15000);equal(c.update(200000,true,100000),20000);equal(c.update(215000,true,100000),0);equal(c.update(230000,true,230000),0);equal(c.update(245000,false,230000),15000);equal(c.update(260000,false,260000),0);equal(c.update(275000,true,275000),0);equal(c.update(290000,true,275000),15000);equal(c.update(280000,true,280000),0);System.out.println("ReadingTimeCounterTest: "+count+" checks passed; focus/pause/idle/gap/clock rollback");}
}
