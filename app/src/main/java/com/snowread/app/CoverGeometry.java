package com.snowread.app;

/** Geometry shared by cover-image decoding and portrait cropping. */
public final class CoverGeometry {
    public static int sampleSize(int width,int height,int maxEdge) {
        if(width<=0||height<=0||maxEdge<=0) throw new IllegalArgumentException("Invalid image dimensions");
        int sample=1;
        while(width/(double)sample>maxEdge||height/(double)sample>maxEdge) sample*=2;
        return sample;
    }
    public static float[] centerCrop(int width,int height,int targetWidth,int targetHeight) {
        if(width<=0||height<=0||targetWidth<=0||targetHeight<=0) throw new IllegalArgumentException("Invalid crop dimensions");
        float target=targetWidth/(float)targetHeight,ratio=width/(float)height;
        if(ratio>target) {float crop=height*target;return new float[]{(width-crop)/2,0,(width+crop)/2,height};}
        float crop=width/target;return new float[]{0,(height-crop)/2,width,(height+crop)/2};
    }
}
