package com.snowread.app;

import android.graphics.Color;
import android.os.Build;
import android.view.*;

/** Shared window policy. Immersive reading reserves stable insets to avoid re-pagination. */
final class EdgeToEdge {
    interface InsetsListener {void changed(int left,int top,int right,int navigation,int keyboard);}
    static void apply(Window window,boolean dark) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS|WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        // Android 6/7 cannot display dark navigation icons: use our teal for readable white keys.
        window.setNavigationBarColor(Build.VERSION.SDK_INT<26?SnowTheme.ACCENT_DARK:Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=28){window.setNavigationBarDividerColor(Color.TRANSPARENT);WindowManager.LayoutParams attrs=window.getAttributes();attrs.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;window.setAttributes(attrs);}
        if(Build.VERSION.SDK_INT>=29){window.setNavigationBarContrastEnforced(false);window.setStatusBarContrastEnforced(false);}
        int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        window.getDecorView().setSystemUiVisibility(flags);
        if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false);
        appearance(window,dark,dark);
    }
    static void appearance(Window window,boolean statusDark,boolean navigationDark) {
        int flags=window.getDecorView().getSystemUiVisibility()&~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(!statusDark)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;if(!navigationDark&&Build.VERSION.SDK_INT>=26)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;window.getDecorView().setSystemUiVisibility(flags);
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=window.getInsetsController();if(controller!=null){int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,value=(!statusDark?WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS:0)|(!navigationDark?WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS:0);controller.setSystemBarsAppearance(value,mask);}}
    }
    static void listen(View root,InsetsListener listener) {
        listen(root,false,listener);
    }
    static void immersive(Window window,boolean hide){
        // PhoneWindow.getInsetsController() dereferences its decor without installing it.
        // getDecorView() creates it; the controller can still be null before attachment.
        View decor=window.getDecorView();
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=decor.getWindowInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(hide)c.hide(WindowInsets.Type.systemBars());else c.show(WindowInsets.Type.systemBars());}}
        else {int flags=decor.getSystemUiVisibility();int mask=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;decor.setSystemUiVisibility(hide?flags|mask:flags&~mask);}
    }
    static void listen(View root,boolean stable,InsetsListener listener) {
        root.setFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets safe=stable?insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()):insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int keyboard=insets.getInsets(WindowInsets.Type.ime()).bottom;listener.changed(safe.left,safe.top,safe.right,safe.bottom,keyboard);return WindowInsets.CONSUMED;}
            int left=insets.getSystemWindowInsetLeft(),top=insets.getSystemWindowInsetTop(),right=insets.getSystemWindowInsetRight();
            if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){DisplayCutout cutout=insets.getDisplayCutout();left=Math.max(left,cutout.getSafeInsetLeft());right=Math.max(right,cutout.getSafeInsetRight());top=Math.max(top,cutout.getSafeInsetTop());}
            int bottom=insets.getSystemWindowInsetBottom(),stableBottom=insets.getStableInsetBottom();int keyboard=bottom>Math.max(stableBottom,root.getResources().getDisplayMetrics().density*120)?bottom:0;
            if(stable){top=Math.max(top,insets.getStableInsetTop());bottom=Math.max(bottom,stableBottom);left=Math.max(left,insets.getStableInsetLeft());right=Math.max(right,insets.getStableInsetRight());}
            listener.changed(left,top,right,keyboard>0?stableBottom:bottom,keyboard);return insets.consumeSystemWindowInsets();
        });root.requestApplyInsets();
    }
}
