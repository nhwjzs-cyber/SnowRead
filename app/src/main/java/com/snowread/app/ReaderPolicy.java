package com.snowread.app;

import java.util.ArrayDeque;

/** Pure rules shared by settings, lifecycle and navigation. */
final class ReaderPolicy {
    static float brightness(boolean follow,float value){return follow?-1f:Math.max(.01f,Math.min(1f,Float.isNaN(value)?.5f:value));}
    static boolean nightAt(int minute,int start,int end){
        minute=(minute%1440+1440)%1440;start=(start%1440+1440)%1440;end=(end%1440+1440)%1440;
        return start==end||start<end&&minute>=start&&minute<end||start>end&&(minute>=start||minute<end);
    }
    static int palette(int manual,int mode,boolean systemNight,int minute,int start,int end){
        if(mode==0)return manual;boolean night=mode==1?systemNight:nightAt(minute,start,end);
        return night?2:manual==2?0:manual;
    }
    /** Shelf chrome and dialog darkness: manual light/dark overrides win, else follow system night or the resolved reading palette. */
    static boolean darkAppearance(int pref,boolean systemNight,boolean paletteNight){return pref==2||pref!=1&&(systemNight||paletteNight);}
    static final class History {
        private final ArrayDeque<Integer> positions=new ArrayDeque<>();
        void push(int offset){if(!positions.isEmpty()&&positions.peekLast()==offset)return;if(positions.size()==20)positions.removeFirst();positions.addLast(offset);}
        int pop(){return positions.isEmpty()?-1:positions.removeLast();}
        void clear(){positions.clear();}
        int size(){return positions.size();}
    }
}
