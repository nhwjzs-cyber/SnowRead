package com.snowread.app;

import android.app.Activity;
import android.content.ClipData;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** App-local book drag. A session freezes IDs; only explicit category chips accept drops. */
final class ShelfCategoryDrag {
    interface Host {boolean drop(List<String> ids,String category);}
    private static final String MIME="application/vnd.snowread.books";
    private static final class Session {
        final List<String> ids;
        boolean submitted;
        Session(Collection<String> ids){this.ids=Collections.unmodifiableList(new ArrayList<>(ids));}
    }
    private static final class Target {
        final Button view;final Drawable background,foreground;final CharSequence description;final SoftGlowDrawable glow;
        Target(Button view,int color){this.view=view;background=view.getBackground();foreground=view.getForeground();description=view.getContentDescription();glow=new SoftGlowDrawable(color,view.getResources().getDisplayMetrics().density,14);}
        void reset(){Motion.cancel(view);view.setBackground(background);view.setForeground(foreground);view.setScaleX(1);view.setScaleY(1);view.setContentDescription(description);glow.strength(0);}
    }
    private final Activity activity;
    private final View root;
    private final HorizontalScrollView strip;
    private final Host host;
    private final boolean night;
    private final ArrayList<Target> targets=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView hint;private CharSequence restingHint;private int restingVisibility;
    private Session session;
    private View source;
    private int scrollDirection;
    private boolean disposed;
    private final Runnable scrollTick=new Runnable(){public void run(){
        if(session==null||disposed||scrollDirection==0)return;
        if(strip.canScrollHorizontally(scrollDirection)){strip.scrollBy(scrollDirection*dp(10),0);handler.postDelayed(this,24);}
    }};

    ShelfCategoryDrag(Activity activity,View root,HorizontalScrollView strip,boolean night,Host host){
        this.activity=activity;this.root=root;this.strip=strip;this.night=night;this.host=host;
        View.OnDragListener monitor=(view,event)->{
            if(!ours(event))return false;
            if(event.getAction()==DragEvent.ACTION_DRAG_LOCATION)edgeScroll(view,event);
            else if(event.getAction()==DragEvent.ACTION_DRAG_ENDED)finish();
            // Empty space tracks the drag and edge-scroll, but is never a drop destination.
            return event.getAction()!=DragEvent.ACTION_DROP;
        };
        root.setOnDragListener(monitor);strip.setOnDragListener(monitor);
    }
    void hint(TextView text){hint=text;restingHint=text.getText();restingVisibility=text.getVisibility();}
    void category(Button chip,String id,String name){
        Target target=new Target(chip,night?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT);targets.add(target);
        chip.setOnDragListener((view,event)->{
            if(!ours(event)){if(event.getAction()==DragEvent.ACTION_DRAG_ENDED)target.reset();return false;}
            switch(event.getAction()){
                case DragEvent.ACTION_DRAG_STARTED:return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    chip.setBackground(SnowTheme.rounded(night?SnowTheme.NIGHT_SURFACE:SnowTheme.ACCENT_SOFT,dp(22),night?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK,dp(2)));
                    chip.setForeground(target.glow);Motion.interpolate(chip,0,1,Motion.FAST,value->{target.glow.strength(value);chip.setScaleX(1+.025f*value);chip.setScaleY(1+.025f*value);});
                    chip.setContentDescription("松手加入分类“"+name+"”");
                    if(hint!=null)hint.setText("松手将 "+session.ids.size()+" 本书加入「"+name+"」");
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:edgeScroll(chip,event);return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    target.reset();if(hint!=null)hint.setText("拖到顶部分类上松手 · 保留原有分类");return true;
                case DragEvent.ACTION_DROP:
                    if(session.submitted)return false;
                    boolean accepted=host.drop(session.ids,id);session.submitted=accepted;return accepted;
                case DragEvent.ACTION_DRAG_ENDED:target.reset();finish();return true;
                default:return true;
            }
        });
    }
    boolean start(View view,Collection<String> ids){
        if(disposed||session!=null||ids.isEmpty()||targets.isEmpty())return false;
        session=new Session(ids);source=view;
        ClipData data=new ClipData("融雪阅读书籍",new String[]{MIME},new ClipData.Item(Integer.toString(ids.size())));
        View.DragShadowBuilder shadow=new BookShadow(ids.size());
        boolean started;
        try{started=Build.VERSION.SDK_INT>=24?view.startDragAndDrop(data,shadow,session,0):view.startDrag(data,shadow,session,0);}
        catch(RuntimeException error){started=false;}
        if(!started){finish();return false;}
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        activity.getSharedPreferences("reader",0).edit().putBoolean("shelfDragTipSeen",true).apply();
        if(hint!=null){hint.setVisibility(View.VISIBLE);hint.setText("拖动 "+ids.size()+" 本书到顶部分类 · 两侧停留可滚动");}
        view.announceForAccessibility("正在拖动 "+ids.size()+" 本书，拖到分类上松手即可加入");
        return true;
    }
    private boolean ours(DragEvent event){return !disposed&&session!=null&&event.getLocalState()==session&&(event.getAction()!=DragEvent.ACTION_DRAG_STARTED||event.getClipDescription()!=null&&event.getClipDescription().hasMimeType(MIME));}
    private void edgeScroll(View from,DragEvent event){
        int[] a=new int[2],b=new int[2];from.getLocationInWindow(a);strip.getLocationInWindow(b);
        float x=a[0]+event.getX()-b[0],y=a[1]+event.getY()-b[1];
        int direction=y<0||y>strip.getHeight()?0:x<dp(48)?-1:x>strip.getWidth()-dp(48)?1:0;
        if(direction!=scrollDirection){handler.removeCallbacks(scrollTick);scrollDirection=direction;if(direction!=0)handler.post(scrollTick);}
    }
    private void finish(){
        handler.removeCallbacks(scrollTick);scrollDirection=0;session=null;source=null;
        for(Target target:targets)target.reset();
        if(hint!=null){hint.setText(restingHint);hint.setVisibility(restingVisibility);}
    }
    void dispose(){
        if(disposed)return;
        cancel();disposed=true;
        root.setOnDragListener(null);strip.setOnDragListener(null);
    }
    void cancel(){View dragging=source;finish();if(dragging!=null&&Build.VERSION.SDK_INT>=24)dragging.cancelDragAndDrop();}
    private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private final class BookShadow extends View.DragShadowBuilder {
        private final int count;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        BookShadow(int count){this.count=count;}
        @Override public void onProvideShadowMetrics(Point size,Point touch){size.set(dp(164),dp(96));touch.set(size.x/2,size.y-dp(2));}
        @Override public void onDrawShadow(Canvas canvas){
            paint.setColor(night?0xFF294853:0xFFBED7DB);canvas.drawRoundRect(dp(8),dp(8),dp(160),dp(92),dp(16),dp(16),paint);
            paint.setColor(night?SnowTheme.NIGHT_SURFACE:SnowTheme.PAPER);canvas.drawRoundRect(0,0,dp(152),dp(84),dp(16),dp(16),paint);
            paint.setColor(night?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(22));paint.setTypeface(Typeface.create("sans-serif-medium",0));canvas.drawText(count+" 本书",dp(76),dp(37),paint);
            paint.setTextSize(dp(11));canvas.drawText("拖到分类上松手",dp(76),dp(61),paint);
        }
    }
}
