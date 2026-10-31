package com.snowread.app;
import android.content.SharedPreferences;
import java.lang.reflect.*;
import java.util.*;

/** Executes production accounting with a deterministic clock and a transactional preference adapter. */
public final class ReadingStatsLedgerTest {
    static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Ledger "+checks);}
    static final class Clock implements ReadingStats.Clock {long wall,elapsed=1000;public long wall(){return wall;}public long elapsed(){return elapsed;}void advance(long ms){wall+=ms;elapsed+=ms;}}
    static SharedPreferences preferences(Map<String,Object> values){
        return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(object,method,args)->{
            String name=method.getName();if(name.equals("getAll"))return new HashMap<>(values);if(name.startsWith("get"))return values.containsKey(args[0])?values.get(args[0]):args[1];
            if(name.equals("edit")){Map<String,Object> pending=new HashMap<>();boolean[] clear={false};return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(editor,m,a)->{
                if(m.getName().startsWith("put")){pending.put((String)a[0],a[1]);return editor;}if(m.getName().equals("clear")){clear[0]=true;return editor;}if(m.getName().equals("apply")||m.getName().equals("commit")){if(clear[0])values.clear();values.putAll(pending);return m.getName().equals("commit")?true:null;}throw new UnsupportedOperationException(m.getName());});}
            throw new UnsupportedOperationException(name);
        });
    }
    public static void main(String[] args){TimeZone old=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Map<String,Object> values=new HashMap<>();values.put("day:2020-01-01",123000L);Clock clock=new Clock();Calendar calendar=Calendar.getInstance();calendar.set(2026,9,31,23,59,55);calendar.set(Calendar.MILLISECOND,0);clock.wall=calendar.getTimeInMillis();
        String a=String.join("",Collections.nCopies(64,"a")),b=String.join("",Collections.nCopies(64,"b"));ReadingStats stats=new ReadingStats(preferences(values),clock);
        stats.pulse(true,clock.elapsed,a);clock.advance(15000);stats.pulse(false,clock.elapsed,a);
        check(stats.day("2026-10-31")==5000);check(stats.day("2026-11-01")==10000);check(stats.books("2026-10-31").get(a)==5000);check(stats.books("2026-11-01").get(a)==10000);
        check(stats.first("2026-10-31")==clock.wall-15000);check(stats.last("2026-10-31")==clock.wall-10000);check(stats.first("2026-11-01")==clock.wall-10000);check(stats.last("2026-11-01")==clock.wall);
        check(stats.day("2020-01-01")==123000&&stats.books("2020-01-01").isEmpty());
        stats.pulse(true,clock.elapsed,a);clock.advance(10000);stats.pulse(true,clock.elapsed,b);clock.advance(10000);stats.pulse(false,clock.elapsed,b);
        check(stats.books("2026-11-01").get(a)==20000);check(stats.books("2026-11-01").get(b)==10000);
        long before=stats.total();clock.advance(15000);stats.pulse(false,clock.elapsed,"");clock.advance(15000);stats.pulse(false,clock.elapsed,"");check(stats.total()==before);
        stats.pulse(true,clock.elapsed,a);clock.advance(120000);stats.pulse(false,clock.elapsed,a);check(stats.total()-before==20000);
        before=stats.total();stats.enabled(false);stats.pulse(true,clock.elapsed,b);clock.advance(15000);stats.pulse(true,clock.elapsed,b);check(stats.total()==before);
        stats.clear();check(!stats.enabled()&&stats.total()==0&&stats.books("2026-11-01").isEmpty());
        System.out.println("ReadingStatsLedgerTest: "+checks+" checks passed; production midnight split, book ownership, idle cap, disabled and nonreading intervals, legacy totals and clear");
    }finally{TimeZone.setDefault(old);}}
}
