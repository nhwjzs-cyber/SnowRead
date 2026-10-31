package com.snowread.app;

public final class PageCurlGeometryTest {
    public static void main(String[] args){int checks=0;
        for(float w:new float[]{1,320,1080,2400})for(float h:new float[]{1,640,1920}){
            float previous=w;
            for(int i=0;i<=1000;i++){
                PageCurlGeometry g=new PageCurlGeometry(i/1000f,w,h);
                if(g.crease<0||g.crease>w||g.crease>previous||g.tip>g.crease||Float.isNaN(g.bulge)||g.bulge<0||g.lift<0)throw new AssertionError("Invalid fold");
                previous=g.crease;checks++;
            }
            PageCurlGeometry start=new PageCurlGeometry(0,w,h),end=new PageCurlGeometry(1,w,h);
            if(start.crease!=w||start.tip!=w||end.crease!=0||end.tip!=-w)throw new AssertionError("Endpoint snap");checks++;
        }
        if(new PageCurlGeometry(Float.NaN,300,600).progress!=0)throw new AssertionError();
        System.out.println("PageCurlGeometryTest: "+(checks+1)+" checks passed; endpoints, bounded folds and monotonic travel");
    }
}
