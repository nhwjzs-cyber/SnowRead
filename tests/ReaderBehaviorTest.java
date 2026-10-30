package com.snowread.app;

public final class ReaderBehaviorTest {
    private static int checks;
    private static void check(boolean condition,String message) {checks++;if(!condition) throw new AssertionError(message);}
    public static void main(String[] args) {
        ReaderChrome chrome=new ReaderChrome();
        check(!chrome.isVisible(),"outside the reader has no toolbar");
        check(chrome.remaining(0)==Long.MAX_VALUE,"no timer outside reading");
        chrome.toggle(0);check(!chrome.isVisible(),"toggle outside reading is ignored");
        chrome.enter(100,true);
        check(chrome.isVisible(),"enter reading with visible controls");
        check(chrome.remaining(100)==4000,"initial idle countdown");
        check(!chrome.hideIfDue(4099),"controls remain until the deadline");
        check(chrome.hideIfDue(4100),"controls hide at idle deadline");
        check(!chrome.isVisible(),"idle hiding updates visibility");
        check(chrome.remaining(5000)==Long.MAX_VALUE,"hidden controls do not spin a timer");
        chrome.toggle(6000);check(chrome.isVisible(),"center tap reveals controls");
        check(chrome.remaining(6000)==4000,"revealing restarts countdown");
        chrome.touchDown(7000);
        check(chrome.remaining(20000)==Long.MAX_VALUE,"holding a finger pauses automatic hiding");
        check(!chrome.hideIfDue(20000),"seek and long press cannot lose controls mid-touch");
        chrome.touchUp(20000);
        check(chrome.remaining(20000)==4000,"release gets a full countdown");
        check(!chrome.hideIfDue(23999),"release deadline remains exclusive");
        check(chrome.hideIfDue(24000),"release deadline eventually hides controls");
        chrome.enter(30000,true);
        chrome.focus(false,31000);
        check(chrome.remaining(50000)==Long.MAX_VALUE,"dialog or app background suspends hiding");
        check(!chrome.hideIfDue(50000),"dialog does not hide underlying controls");
        chrome.focus(true,50000);
        check(chrome.remaining(50000)==4000,"return from dialog resets countdown");
        check(chrome.hideIfDue(54000),"resumed reader hides after a full idle interval");
        chrome.enter(60000,true);chrome.toggle(60100);
        chrome.touchDown(60200);chrome.touchUp(60300);
        check(!chrome.isVisible(),"ordinary interactions do not force hidden overlays open");
        chrome.toggle(60400);check(chrome.isVisible(),"explicit center tap shows overlays");
        chrome.leave();
        check(!chrome.isVisible()&&chrome.remaining(90000)==Long.MAX_VALUE,"leave reading cancels state");
        chrome.enter(100000,false);
        check(chrome.isVisible()&&chrome.remaining(100000)==Long.MAX_VALUE,"loading window can start without focus");
        chrome.focus(true,110000);
        check(chrome.remaining(110000)==4000,"focus acquisition starts countdown");
        chrome.touchDown(111000);chrome.focus(false,112000);chrome.focus(true,120000);
        check(chrome.remaining(120000)==4000,"window loss clears an interrupted touch");
        check(CoverGeometry.sampleSize(1200,800,1600)==1,"small images keep resolution");
        check(CoverGeometry.sampleSize(6000,4000,1600)==4,"camera photos are downsampled");
        check(CoverGeometry.sampleSize(800,20000,1600)==16,"very tall images remain bounded");
        check(CoverGeometry.sampleSize(1601,1600,1600)==2,"edge just above limit is downsampled");
        for(int width:new int[]{1,120,480,1200,40000}) {
            for(int height:new int[]{1,180,720,2400,40000}) {
                int sample=CoverGeometry.sampleSize(width,height,1600);
                check((sample&(sample-1))==0&&sample>0,"sample factor is a positive power of two");
                check(width/(double)sample<=1600&&height/(double)sample<=1600,"decoded dimensions stay bounded");
                float[] crop=CoverGeometry.centerCrop(width,height,480,720);
                check(crop[0]>=-.01f&&crop[1]>=-.01f&&crop[2]<=width+.01f&&crop[3]<=height+.01f,"crop stays within source");
                check(crop[2]>crop[0]&&crop[3]>crop[1],"crop remains nonempty");
                check(Math.abs((crop[0]+crop[2])/2-width/2f)<.02f&&Math.abs((crop[1]+crop[3])/2-height/2f)<.02f,"crop preserves source center");
                check(Math.abs((crop[2]-crop[0])/(crop[3]-crop[1])-2f/3f)<.003f,"portrait crop preserves target aspect");
            }
        }
        float[] wide=CoverGeometry.centerCrop(1600,900,480,720);
        check(wide[0]==500&&wide[2]==1100&&wide[1]==0&&wide[3]==900,"wide image gets centered portrait crop");
        try {CoverGeometry.sampleSize(0,300,1600);throw new AssertionError("invalid image size accepted");}
        catch(IllegalArgumentException expected){checks++;}
        try {CoverGeometry.centerCrop(300,400,0,720);throw new AssertionError("invalid target accepted");}
        catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS: "+checks+" checks; reader toolbar timing, focus, gestures and cover geometry.");
    }
}
