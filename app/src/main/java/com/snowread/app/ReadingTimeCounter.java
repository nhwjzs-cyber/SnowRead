package com.snowread.app;

/** Monotonic foreground accounting, with a bounded sampling gap and idle cutoff. */
final class ReadingTimeCounter {
    private long last=-1,deadline;private boolean running;
    long update(long now,boolean eligible,long interaction){
        long counted=last>=0&&running?Math.max(0,Math.min(20000,Math.min(now,deadline)-last)):0;
        last=now;deadline=interaction+90000;running=eligible&&now<deadline;return counted;
    }
}
