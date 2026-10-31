package com.snowread.app;
import java.text.SimpleDateFormat;
import java.util.*;

/** Calendar days, including DST, rather than fixed 24-hour buckets. */
final class ReadingStatsData {
    static String day(long wall){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(wall));}
    static long midnight(long wall){Calendar c=Calendar.getInstance();c.setTimeInMillis(wall);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    static long nextDay(long wall){Calendar c=Calendar.getInstance();c.setTimeInMillis(midnight(wall));c.add(Calendar.DATE,1);return c.getTimeInMillis();}
    static List<String> days(int weeks,long now){Calendar c=Calendar.getInstance();c.setTimeInMillis(midnight(now));int weekday=(c.get(Calendar.DAY_OF_WEEK)+5)%7;c.add(Calendar.DATE,-weekday-(weeks-1)*7);ArrayList<String> days=new ArrayList<>();for(int i=0;i<weeks*7;i++){days.add(day(c.getTimeInMillis()));c.add(Calendar.DATE,1);}return days;}
    static int level(long ms){return ms<=0?0:ms<5*60000?1:ms<20*60000?2:ms<60*60000?3:4;}
    static LinkedHashMap<String,Long> split(long end,long duration){LinkedHashMap<String,Long> parts=new LinkedHashMap<>();long start=end-Math.max(0,Math.min(20000,duration));while(start<end){long stop=Math.min(end,nextDay(start));parts.put(day(start),stop-start);start=stop;}return parts;}
    private ReadingStatsData(){}
}
