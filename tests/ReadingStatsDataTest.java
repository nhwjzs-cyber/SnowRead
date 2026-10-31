package com.snowread.app;
import java.util.*;
public final class ReadingStatsDataTest {
    public static void main(String[] args){int checks=0;TimeZone old=TimeZone.getDefault();try{
        for(String zone:new String[]{"Asia/Shanghai","America/New_York","Europe/Berlin"}){TimeZone.setDefault(TimeZone.getTimeZone(zone));Calendar c=Calendar.getInstance();c.set(2026,Calendar.MARCH,8,0,0,5);c.set(Calendar.MILLISECOND,0);long end=c.getTimeInMillis();Map<String,Long> split=ReadingStatsData.split(end,12000);long sum=0;for(long ms:split.values())sum+=ms;if(sum!=12000||split.size()!=2)throw new AssertionError();checks++;
            List<String> days=ReadingStatsData.days(16,end);if(days.size()!=112||new HashSet<>(days).size()!=112)throw new AssertionError();checks++;
            for(int i=0;i<366;i++){c.add(Calendar.DATE,1);long now=c.getTimeInMillis();if(ReadingStatsData.nextDay(now)<=ReadingStatsData.midnight(now))throw new AssertionError();checks++;}
        }
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));Calendar dst=Calendar.getInstance();
        for(int[] date:new int[][]{{2026,2,8,23},{2026,10,1,25}}){dst.set(date[0],date[1],date[2],12,0,0);long wall=dst.getTimeInMillis();if(ReadingStatsData.nextDay(wall)-ReadingStatsData.midnight(wall)!=date[3]*3600000L)throw new AssertionError("DST day length");checks++;}
        long[] thresholds={0,1,299999,300000,1199999,1200000,3599999,3600000};int[] levels={0,1,1,2,2,3,3,4};for(int i=0;i<levels.length;i++){if(ReadingStatsData.level(thresholds[i])!=levels[i])throw new AssertionError();checks++;}
        if(!ReadingStatsData.split(100000,0).isEmpty())throw new AssertionError();checks++;
        System.out.println("ReadingStatsDataTest: "+checks+" checks passed; calendar boundaries, DST, heatmap levels and midnight split");
    }finally{TimeZone.setDefault(old);}}
}
