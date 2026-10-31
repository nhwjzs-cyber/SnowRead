package com.snowread.app;

import java.util.Locale;

/** Display-only formatting. Never changes the document's reading offset. */
final class ReaderStatusText {
    static String progress(int offset,int length,String page){
        double ratio=Math.max(0,Math.min(Math.max(0,length-1),offset))/(double)Math.max(1,length-1);
        return String.format(Locale.CHINA,"%.1f%%",ratio*100)+(page==null||page.isEmpty()?"":" · "+page);
    }
    static int batteryPercent(int level,int scale){
        if(level<0||scale<=0)return -1;
        return (int)Math.round(Math.min(1,level/(double)scale)*100);
    }
    static String battery(int percent){return percent<0?"电量 —":"电量 "+Math.max(0,Math.min(100,percent))+"%";}
}
