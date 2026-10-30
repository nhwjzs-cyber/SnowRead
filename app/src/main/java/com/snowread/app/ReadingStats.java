package com.snowread.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.*;

/** Only local day totals, never novel text or background time. */
final class ReadingStats {
    private final SharedPreferences prefs;private ReadingTimeCounter counter=new ReadingTimeCounter();
    ReadingStats(Context c){prefs=c.getSharedPreferences("reading-stats",0);}
    boolean enabled(){return prefs.getBoolean("enabled",true);}
    private static String key(Date date){return "day:"+new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(date);}
    void pulse(boolean eligible,long interaction){long delta=counter.update(SystemClock.elapsedRealtime(),enabled()&&eligible,interaction);if(delta>0&&enabled()){String key=key(new Date());prefs.edit().putLong(key,prefs.getLong(key,0)+delta).apply();}}
    long today(){return prefs.getLong(key(new Date()),0);}
    long week(){Calendar c=Calendar.getInstance();int back=(c.get(Calendar.DAY_OF_WEEK)+5)%7;c.add(Calendar.DATE,-back);long sum=0;for(int i=0;i<7;i++){sum+=prefs.getLong(key(c.getTime()),0);c.add(Calendar.DATE,1);}return sum;}
    void enabled(boolean value){prefs.edit().putBoolean("enabled",value).apply();counter=new ReadingTimeCounter();}
    void clear(){boolean on=enabled();prefs.edit().clear().putBoolean("enabled",on).apply();counter=new ReadingTimeCounter();}
    static String duration(long ms){long seconds=ms/1000;return seconds<60?seconds+" 秒":seconds/3600>0?seconds/3600+" 小时 "+seconds/60%60+" 分钟":seconds/60+" 分钟";}
}
