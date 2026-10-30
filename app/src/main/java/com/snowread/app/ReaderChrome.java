package com.snowread.app;

/** Deterministic reader-toolbar state, using a monotonic clock supplied by the activity. */
public final class ReaderChrome {
    public static final long IDLE_MS=4000;
    private boolean reading,visible,focused,touching;
    private long deadline;
    public void enter(long now,boolean focused) {reading=true;visible=true;this.focused=focused;touching=false;deadline=now+IDLE_MS;}
    public void leave() {reading=false;visible=false;touching=false;}
    public void focus(boolean value,long now) {focused=value;touching=false;if(value) deadline=now+IDLE_MS;}
    public void touchDown(long now) {touching=true;deadline=now+IDLE_MS;}
    public void touchUp(long now) {touching=false;deadline=now+IDLE_MS;}
    public void toggle(long now) {if(reading){visible=!visible;deadline=now+IDLE_MS;}}
    public boolean hideIfDue(long now) {
        if(remaining(now)==0) {visible=false;return true;}return false;
    }
    public long remaining(long now) {
        if(!reading||!visible||!focused||touching) return Long.MAX_VALUE;
        return Math.max(0,deadline-now);
    }
    public boolean isVisible() {return reading&&visible;}
}
