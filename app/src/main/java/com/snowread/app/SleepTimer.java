package com.snowread.app;

/** Wall-clock deadline survives process recreation; evaluated only at sentence boundaries. */
final class SleepTimer {
    static final int NONE=0,TIME=1,CHAPTER=2,SENTENCE=3;
    int mode;long deadline;
    void minutes(int minutes,long now){mode=TIME;deadline=now+minutes*60000L;}
    void setMode(int value){mode=value;deadline=0;}
    boolean due(long now,boolean chapterEnd){return mode==SENTENCE||mode==CHAPTER&&chapterEnd||mode==TIME&&now>=deadline;}
    void clear(){mode=NONE;deadline=0;}
    void restore(int value,long savedDeadline,long now){mode=value;deadline=savedDeadline;if(mode==TIME&&deadline<=now)clear();}
    String label(long now){if(mode==CHAPTER)return "本章结束";if(mode==SENTENCE)return "当前句结束";if(mode==TIME)return Math.max(0,(deadline-now+59999)/60000)+" 分钟后";return "未开启";}
}
