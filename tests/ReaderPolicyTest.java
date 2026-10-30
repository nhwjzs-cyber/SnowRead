package com.snowread.app;

public final class ReaderPolicyTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    public static void main(String[] args){
        check(ReaderPolicy.brightness(true,.2f)==-1);check(ReaderPolicy.brightness(false,0)==.01f);check(ReaderPolicy.brightness(false,2)==1);check(ReaderPolicy.brightness(false,Float.NaN)==.5f);
        for(int i=0;i<1440;i++){check(ReaderPolicy.nightAt(i,1320,420)==(i>=1320||i<420));check(ReaderPolicy.nightAt(i,420,1320)==(i>=420&&i<1320));check(ReaderPolicy.nightAt(i,0,0));check(ReaderPolicy.palette(1,2,false,i,1320,420)==(i>=1320||i<420?2:1));}
        check(ReaderPolicy.palette(2,0,false,700,0,0)==2);check(ReaderPolicy.palette(2,1,false,700,0,0)==0);check(ReaderPolicy.palette(1,1,true,700,0,0)==2);
        check(!ReaderPolicy.darkAppearance(0,false,false));check(ReaderPolicy.darkAppearance(0,true,false));check(ReaderPolicy.darkAppearance(0,false,true));check(ReaderPolicy.darkAppearance(1,true,false)==false);check(ReaderPolicy.darkAppearance(2,false,false));
        ReaderPolicy.History h=new ReaderPolicy.History();check(h.pop()==-1);for(int i=0;i<50;i++){h.push(i);h.push(i);}check(h.size()==20);for(int i=49;i>=30;i--)check(h.pop()==i);check(h.pop()==-1);h.push(5);h.clear();check(h.size()==0);
        System.out.println("ReaderPolicyTest: "+checks+" checks passed; brightness, overnight schedules, appearance darkness and bounded navigation history");
    }
}
