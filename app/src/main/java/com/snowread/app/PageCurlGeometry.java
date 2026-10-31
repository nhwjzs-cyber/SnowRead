package com.snowread.app;

/** A horizontal paper fold; endpoints exactly match the stationary pages. */
final class PageCurlGeometry {
    final float progress,crease,tip,fold,bulge,lift;
    PageCurlGeometry(float fraction,float width,float height){
        progress=Float.isNaN(fraction)?0:Math.max(0,Math.min(1,fraction));
        crease=width*(1-progress);fold=width*progress;tip=crease-fold;
        float curve=(float)Math.sin(Math.PI*progress);
        bulge=Math.min(width*.055f,fold*.22f)*curve;lift=Math.min(height*.035f,fold*.12f)*curve;
    }
}
