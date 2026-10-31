package com.snowread.app;
import java.util.*;
public final class ReadingStatusTest {
    public static void main(String[] args){int checks=0;ShelfOrganization.Entry e=new ShelfOrganization.Entry();e.id="a";e.title="书";
        if(ReadingStatus.of(e,0)!=ReadingStatus.UNREAD)throw new AssertionError();checks++;
        e.readingStarted=true;if(ReadingStatus.of(e,0)!=ReadingStatus.READING)throw new AssertionError();checks++;
        if(ReadingStatus.of(e,1)!=ReadingStatus.FINISHED)throw new AssertionError();checks++;
        ArrayList<ShelfOrganization.Entry> list=new ArrayList<>(Collections.singleton(e));
        for(int state=1;state<=3;state++){e.readingState=state;for(float ratio:new float[]{0,.4f,1}){if(ReadingStatus.of(e,ratio)!=state)throw new AssertionError();checks++;}
            for(int show=1;show<=3;show++){int expected=show==1?2:show==2?1:3;if(ShelfOrganization.filterProgress(list,show,b->.5f).size()!=(expected==state?1:0))throw new AssertionError();checks++;}
        }
        if(ReadingStatus.valid(-1)||ReadingStatus.valid(4))throw new AssertionError();checks++;
        System.out.println("ReadingStatusTest: "+checks+" checks passed; legacy inference, opened-at-start, manual overrides and filters");
    }
}
