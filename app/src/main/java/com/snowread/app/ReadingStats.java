package com.snowread.app;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.util.*;

/** Local foreground totals and daily book durations. Historical day totals are retained. */
final class ReadingStats {
    interface Clock {long elapsed();long wall();}
    private final Clock clock;private final SharedPreferences prefs;private ReadingTimeCounter counter=new ReadingTimeCounter();
    private String owner="";private long deadline;
    ReadingStats(Context c){this(c.getSharedPreferences("reading-stats",0),new Clock(){public long elapsed(){return SystemClock.elapsedRealtime();}public long wall(){return System.currentTimeMillis();}});}
    ReadingStats(SharedPreferences prefs,Clock clock){this.prefs=prefs;this.clock=clock;}
    boolean enabled(){return prefs.getBoolean("enabled",true);}
    void pulse(boolean eligible,long interaction){pulse(eligible,interaction,owner);}
    void pulse(boolean eligible,long interaction,String bookId){
        long now=clock.elapsed(),delta=counter.update(now,enabled()&&eligible,interaction);
        if(delta>0&&enabled()){
            long end=clock.wall()-Math.max(0,now-deadline),cursor=end-delta;SharedPreferences.Editor edit=prefs.edit();
            for(Map.Entry<String,Long> part:ReadingStatsData.split(end,delta).entrySet()){
                String day=part.getKey(),key="day:"+day;long amount=part.getValue(),stop=cursor+amount;edit.putLong(key,prefs.getLong(key,0)+amount);
                if(owner.matches("[a-f0-9]{64}")){String detail="book:"+day+":"+owner;edit.putLong(detail,prefs.getLong(detail,0)+amount);}
                long first=prefs.getLong("first:"+day,0),last=prefs.getLong("last:"+day,0);edit.putLong("first:"+day,first==0?cursor:Math.min(first,cursor));edit.putLong("last:"+day,Math.max(last,stop));cursor=stop;
            }edit.apply();
        }
        owner=bookId==null?"":bookId;deadline=interaction+90000;
    }
    long day(String day){return Math.max(0,prefs.getLong("day:"+day,0));}
    long today(){return day(ReadingStatsData.day(clock.wall()));}
    long week(){Calendar c=Calendar.getInstance();c.setTimeInMillis(clock.wall());int back=(c.get(Calendar.DAY_OF_WEEK)+5)%7;c.add(Calendar.DATE,-back);long sum=0;for(int i=0;i<7;i++){sum+=day(ReadingStatsData.day(c.getTimeInMillis()));c.add(Calendar.DATE,1);}return sum;}
    long first(String day){return prefs.getLong("first:"+day,0);}long last(String day){return prefs.getLong("last:"+day,0);}
    Map<String,Long> books(String day){LinkedHashMap<String,Long> found=new LinkedHashMap<>();String prefix="book:"+day+":";for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(e.getKey().startsWith(prefix)&&e.getValue() instanceof Long){String id=e.getKey().substring(prefix.length());long ms=(Long)e.getValue();if(id.matches("[a-f0-9]{64}")&&ms>0)found.put(id,ms);}return found;}
    long total(){long sum=0;for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(e.getKey().matches("day:\\d{4}-\\d{2}-\\d{2}")&&e.getValue() instanceof Long)sum+=Math.max(0,(Long)e.getValue());return sum;}
    void enabled(boolean value){prefs.edit().putBoolean("enabled",value).apply();counter=new ReadingTimeCounter();owner="";}
    void clear(){boolean on=enabled();prefs.edit().clear().putBoolean("enabled",on).apply();counter=new ReadingTimeCounter();owner="";}
    static final int MAX_BACKUP_RECORDS=20000;
    org.json.JSONObject snapshot()throws Exception {
        org.json.JSONObject data=new org.json.JSONObject();
        for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(validRecord(e.getKey(),e.getValue()))data.put(e.getKey(),e.getValue());
        validateSnapshot(data);return data;
    }
    private static boolean validRecord(String key,Object value){
        if("enabled".equals(key))return value instanceof Boolean;
        if(!(value instanceof Long)&&!(value instanceof Integer))return false;
        if(!key.matches("(?:day|first|last):\\d{4}-\\d{2}-\\d{2}|book:\\d{4}-\\d{2}-\\d{2}:[a-f0-9]{64}"))return false;
        String day=key.substring(key.indexOf(':')+1,key.indexOf(':')+11);
        try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setLenient(false);if(!f.format(f.parse(day)).equals(day))return false;}catch(Exception ex){return false;}
        long n=((Number)value).longValue();return n>=0&&n<=(key.startsWith("first:")||key.startsWith("last:")?253402300799999L:90000000L);
    }
    static void validateSnapshot(org.json.JSONObject data)throws Exception {
        if(data.length()>MAX_BACKUP_RECORDS)throw new java.io.IOException("阅读统计记录过多，暂时无法备份");
        for(Iterator<String> it=data.keys();it.hasNext();){String key=it.next();if(!validRecord(key,data.get(key)))throw new java.io.IOException("备份中的阅读统计记录无效");}
    }
    void restoreSnapshot(org.json.JSONObject data)throws Exception {
        validateSnapshot(data);SharedPreferences.Editor edit=prefs.edit().clear();
        for(Iterator<String> it=data.keys();it.hasNext();){String key=it.next();Object value=data.get(key);if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else edit.putLong(key,((Number)value).longValue());}
        if(!edit.commit())throw new java.io.IOException("未能保存阅读统计，请重试");
        counter=new ReadingTimeCounter();owner="";
    }
    static String duration(long ms){long seconds=Math.max(0,ms)/1000;return seconds<60?seconds+" 秒":seconds/3600>0?seconds/3600+" 小时 "+seconds/60%60+" 分钟":seconds/60+" 分钟";}
}
