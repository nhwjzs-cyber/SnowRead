package com.snowread.app;
public final class ReaderStatusTextTest {
    static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("ReaderStatusText check "+checks);}
    public static void main(String[] args){
        check(ReaderStatusText.progress(0,101,"").equals("0.0%"));check(ReaderStatusText.progress(50,101,null).equals("50.0%"));check(ReaderStatusText.progress(100,101,"本节 3/9 页").equals("100.0% · 本节 3/9 页"));
        check(ReaderStatusText.progress(-1,20,"").equals("0.0%"));check(ReaderStatusText.progress(900,20,"").equals("100.0%"));check(ReaderStatusText.progress(4,0,"").equals("0.0%"));check(ReaderStatusText.progress(0,1,"").equals("0.0%"));check(ReaderStatusText.progress(3,10,"正在分页").equals("33.3% · 正在分页"));
        check(ReaderStatusText.batteryPercent(-1,100)==-1);check(ReaderStatusText.batteryPercent(50,0)==-1);check(ReaderStatusText.batteryPercent(1,2)==50);check(ReaderStatusText.batteryPercent(120,100)==100);check(ReaderStatusText.batteryPercent(0,100)==0);check(ReaderStatusText.batteryPercent(1,3)==33);
        check(ReaderStatusText.battery(-1).equals("电量 —"));check(ReaderStatusText.battery(85).equals("电量 85%"));check(ReaderStatusText.battery(1000).equals("电量 100%"));
        for(int level=0;level<=100;level++){int p=ReaderStatusText.batteryPercent(level,100);check(p==level);check(ReaderStatusText.progress(level,101,"").equals(level+".0%"));}
        System.out.println("ReaderStatusTextTest: "+checks+" checks passed; safe progress, section labels, battery scales and unknown data");
    }
}
