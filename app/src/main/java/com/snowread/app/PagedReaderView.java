package com.snowread.app;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.view.*;
import android.view.accessibility.*;
import android.view.animation.DecelerateInterpolator;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual font pagination with an optional two-page slide; offsets commit only when a page is ready. */
public final class PagedReaderView extends View {
    public interface Listener {void changed();void notice(String message);void toggleControls();void userTurn();void copyAt(int offset);void bookmarkAt(int offset);}
    private final TextDocument doc;
    private final String displayText;
    private final boolean curlMode,snowDust;
    private final PageCurlRenderer curl=new PageCurlRenderer();
    private final PageTurnDust dust=new PageTurnDust();
    private ValueAnimator dustAnimator;private float dustFraction=1;private int dustDirection;
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeShadow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeLight=new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient shadowLeft,shadowRight;private float shadowDepth=-1;
    private final float lineSpacing;
    private final ReaderTypography typography;
    private final boolean leftHand,doubleBookmark,verticalPages,rightSwipeNext;
    private final int highlightColor,pageBackground;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    /** Neighbour pre-layout runs on its own thread so it never queues behind a page build. */
    private final ExecutorService neighbourWorker=Executors.newSingleThreadExecutor();
    private final AtomicInteger ticket=new AtomicInteger();
    private final LinkedHashMap<Integer,PageIndex> cache=new LinkedHashMap<Integer,PageIndex>(8,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer,PageIndex> entry){return size()>6;}
    };
    private Listener listener;
    private StaticLayout layout,outgoingLayout;
    private PageIndex currentIndex;
    private int offset=0,endOffset=0,requestedOffset=0,page=0,highlight=-1,slideDirection=1;
    private String term="";
    private int listenStart=-1,listenEnd=-1;
    private boolean indexing=false,disposed=false,motionEnabled=true;
    private float slideFraction=1;
    /**
     * Three independent animation channels. They used to share one field, so a boundary nudge
     * could silently kill a running page slide and leave the page stuck halfway.
     */
    private ValueAnimator animator;
    private ValueAnimator spring;
    private ValueAnimator nudge;
    // Finger tracking: the page follows the finger and settles afterwards.
    /** Raw undamped finger displacement, negative when the page is dragged to the left. */
    private float dragRaw=0;
    /** The damped displacement that is actually drawn. */
    private float dragOffset=0;
    private boolean dragging=false;
    private boolean verticalTracking;private float verticalStartY;
    private StaticLayout dragNeighbour;
    private int neighbourDirection,neighbourRequest;
    /** Offset used by the "no further page" nudge. */
    private float nudgeValue=0;
    private float density;
    private VelocityTracker tracker;
    /**
     * Progress of a committed turn, in the range 0..1. A turn that started from a drag keeps the
     * fraction the finger had already travelled, so the page continues from where it was released
     * instead of snapping back to the start of the page.
     */
    private float pendingTurn=0f;
    private int pendingDirection=1;
    private final GestureDetector gestures;

    public PagedReaderView(Context context,TextDocument doc,int fontSize,float lineSpacing,int color,int background,int highlightColor,int margin,Typeset typeset){
        this(context,doc,fontSize,lineSpacing,color,background,highlightColor,margin,typeset,doc.text);
    }
    public PagedReaderView(Context context,TextDocument doc,int fontSize,float lineSpacing,int color,int background,int highlightColor,int margin,Typeset typeset,String displayText){
        super(context);this.doc=doc;this.displayText=displayText.length()==doc.text.length()?displayText:doc.text;this.lineSpacing=lineSpacing;this.highlightColor=highlightColor;pageBackground=background;
        curlMode=context.getSharedPreferences("reader",0).getInt("pageEffect",1)==1;snowDust=context.getSharedPreferences("reader",0).getBoolean("pageSnowDust",true);
        typography=new ReaderTypography(context,typeset);leftHand=context.getSharedPreferences("reader",0).getBoolean("leftHand",false);doubleBookmark=context.getSharedPreferences("reader",0).getBoolean("doubleBookmark",false);verticalPages=context.getSharedPreferences("reader",0).getBoolean("verticalPages",false);rightSwipeNext=context.getSharedPreferences("reader",0).getBoolean("rightSwipeNext",false);
        paint.setTextSize(fontSize*getResources().getDisplayMetrics().scaledDensity);paint.setColor(color);paint.setTypeface(typography.face);
        edgeShadow.setColor(Color.argb(26,30,70,80));
        setBackgroundColor(background);setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        density=getResources().getDisplayMetrics().density;
        int side=Math.round(margin*getResources().getDisplayMetrics().density),vertical=Math.round(Math.max(4,Math.min(40,context.getSharedPreferences("reader",0).getInt("verticalMargin",4)))*getResources().getDisplayMetrics().density);
        setPadding(side,vertical,side,vertical);
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent event){verticalTracking=false;verticalStartY=event.getY();return true;}
            @Override public boolean onSingleTapUp(MotionEvent event){
                if(doubleBookmark&&event.getX()>getWidth()*.33f&&event.getX()<getWidth()*.67f)return true;performClick();float x=event.getX();
                if(x<getWidth()*.33f){if(leftHand)next();else previous();}else if(x>getWidth()*.67f){if(leftHand)previous();else next();}else if(listener!=null)listener.toggleControls();
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent event){if(doubleBookmark&&event.getX()>getWidth()*.33f&&event.getX()<getWidth()*.67f){if(listener!=null)listener.toggleControls();return true;}return false;}
            @Override public boolean onDoubleTap(MotionEvent event){if(doubleBookmark&&event.getX()>getWidth()*.33f&&event.getX()<getWidth()*.67f){if(listener!=null)listener.bookmarkAt(offset);return true;}return false;}
            @Override public void onLongPress(MotionEvent event){if(layout!=null&&listener!=null){int line=layout.getLineForVertical(Math.max(0,(int)event.getY()-getPaddingTop()));int local=layout.getOffsetForHorizontal(line,event.getX()-getPaddingLeft());listener.copyAt(Math.min(endOffset-1,offset+local));}}
            @Override public boolean onScroll(MotionEvent first,MotionEvent event,float distanceX,float distanceY){
                if(first==null||event==null)return false;
                // Vertical paged reading keeps the swipe-to-turn gesture.
                if(verticalTracking)return true;
                if(!dragging&&verticalPages&&Math.abs(event.getY()-first.getY())>Math.abs(event.getX()-first.getX())){verticalTracking=true;return true;}
                if(Math.abs(distanceX)<1f)return false;
                // GestureDetector reports the distance since the last callback, positive when the
                // finger moved left. The page displacement is the opposite of that.
                beginDrag(-distanceX);
                return true;
            }
            @Override public boolean onFling(MotionEvent first,MotionEvent last,float vx,float vy){
                if(first==null||last==null)return false;
                float dx=last.getX()-first.getX(),dy=last.getY()-first.getY();
                if(verticalTracking)return true;
                // A drag is already in progress: the release decides whether to turn.
                if(dragging)return true;
                if(Math.abs(dx)>48*getResources().getDisplayMetrics().density&&Math.abs(dx)>Math.abs(dy)){
                    if(logicalDirection(dx)>0)next();else previous();return true;
                }
                if(verticalPages&&Math.abs(dy)>48*getResources().getDisplayMetrics().density&&Math.abs(dy)>Math.abs(dx)){if(dy<0)next();else previous();return true;}return false;
            }
        });
    }
    public void setListener(Listener listener){this.listener=listener;}
    public void setAnimationEnabled(boolean enabled){motionEnabled=enabled;if(!enabled)finishTransition();}
    public int position(){return offset;}
    public int pageEnd(){return endOffset;}
    public boolean containsOffset(int at){return at>=offset&&at<endOffset;}
    public void setListenHighlight(int start,int end){if(start==listenStart&&end==listenEnd)return;listenStart=start;listenEnd=end;if(currentIndex!=null)render();}
    public boolean isIndexing(){return indexing;}
    public String pageLabel(){
        if(indexing)return "正在分页";
        if(currentIndex==null)return "分页阅读";
        return currentIndex.complete()?String.format(Locale.CHINA,"本节 %d/%d 页",page+1,currentIndex.pageCount()):String.format(Locale.CHINA,"本节第 %d 页",page+1);
    }
    private int contentWidth(){return getWidth()-getPaddingLeft()-getPaddingRight();}
    private int contentHeight(){return getHeight()-getPaddingTop()-getPaddingBottom();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        if(w!=oldw||h!=oldh){finishTransition();ticket.incrementAndGet();cache.clear();currentIndex=null;goTo(requestedOffset);}
    }
    public void goTo(int requested){navigate(requested,0);}
    private void navigate(int requested,int direction){
        if(disposed)return;
        // A turn that continues a drag must keep the finger position, so the page does not jump
        // back to the start while the next page is being measured.
        if(pendingTurn>0f&&direction!=0)cancelAnimation();
        else finishTransition();
        int target=Math.max(0,Math.min(requested,doc.text.length()-1));requestedOffset=target;
        if(contentWidth()<=0||contentHeight()<=0){if(layout==null)offset=target;return;}
        int section=doc.chapterAt(target),token=ticket.incrementAndGet();
        PageIndex known=cache.get(section);
        StaticLayout previous=layout;
        if(known!=null&&known.contains(target)){indexing=false;apply(known,target,previous,direction);return;}
        // Keep the current page visible while measuring; do not save an unseen destination.
        indexing=true;invalidate();changed();
        final int requestedSection=section,start=doc.chapters.get(section).offset,sectionEnd=doc.sectionEnd(section);
        final int width=contentWidth(),height=contentHeight();
        final TextPaint measurePaint=new TextPaint(paint);
        worker.execute(()->{
            try{
                PageIndex index=PageIndex.extend(known,start,sectionEnd,target,
                    (at,limit)->fitPage(displayText,at,limit,measurePaint,width,height,lineSpacing),
                    ()->disposed||ticket.get()!=token||Thread.currentThread().isInterrupted());
                if(index==null)return;
                post(()->{
                    if(disposed||ticket.get()!=token)return;
                    cache.put(requestedSection,index);indexing=false;apply(index,target,previous,direction);
                });
            }catch(Exception error){
                post(()->{
                    if(disposed||ticket.get()!=token)return;
                    indexing=false;finishTransition();changed();notice("分页失败，请切换滚动阅读后重试");
                });
            }
        });
    }
    private int fitPage(String text,int start,int limit,TextPaint paint,int width,int height,float spacing){
        int windowEnd=Math.min(limit,start+8192);
        if(windowEnd<text.length()&&windowEnd>start&&Character.isHighSurrogate(text.charAt(windowEnd-1)))windowEnd--;
        StaticLayout measure=typography.layout(typography.style(text,start,windowEnd,paint.getTextSize(),getResources().getDisplayMetrics().density),paint,width,spacing);
        int fitted=0;
        while(fitted<measure.getLineCount()&&measure.getLineTop(fitted+1)<=height)fitted++;
        if(fitted==0)fitted=1;
        int count=fitted>=measure.getLineCount()?windowEnd-start:measure.getLineStart(fitted);
        int end=Math.min(windowEnd,start+Math.max(1,count));
        if(end<limit&&Character.isHighSurrogate(text.charAt(end-1)))end--;
        if(end<=start)end=Math.min(limit,start+Character.charCount(text.codePointAt(start)));
        return end;
    }
    private void apply(PageIndex index,int target,StaticLayout previous,int direction){
        currentIndex=index;page=index.pageAt(target);offset=index.start(page);endOffset=index.end(page);requestedOffset=offset;
        render();
        if(previous!=null&&direction!=0&&animationsAllowed())startTransition(previous,direction);
        else clearDrag();
        changed();
    }
    private boolean animationsAllowed(){
        if(!motionEnabled)return false;
        // Reuse the shared decision so the animation path does not repeat the system query.
        return Motion.enabled(getContext());
    }
    /**
     * Slide from the current page to the next one. When the turn was committed from a drag the
     * slide starts at the fraction the finger had already travelled, so the motion is continuous
     * instead of snapping back to the beginning of the page.
     */
    private void startTransition(StaticLayout previous,int direction){
        outgoingLayout=previous;
        // Physical motion follows the finger; the selected reading direction chooses the content.
        // A logical next-page request must not reverse a rightward drag on release.
        slideDirection=pendingTurn>0f?pendingDirection:(rightSwipeNext?-1:1)*(direction>0?1:-1);
        float from=pendingTurn;
        clearDrag();
        slideFraction=from;
        ValueAnimator animation=ValueAnimator.ofFloat(from,1f);animator=animation;
        // The remaining distance drives the duration, so a turn that starts near the end of the
        // page finishes quickly instead of taking the full time for a page it barely travels.
        float travel=1f-from;
        animation.setDuration(Math.max(1L,Math.round(Motion.PAGE*travel)));
        animation.setInterpolator(new DecelerateInterpolator(1.4f));
        animation.addUpdateListener(value->{if(animator==animation){slideFraction=(Float)value.getAnimatedValue();invalidate();}});
        animation.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator value){
                if(animator==animation){animator=null;outgoingLayout=null;slideFraction=1;startDust(slideDirection);invalidate();}
            }
        });
        animation.start();
    }
    /** Stop a running animation without touching the page position. */
    private void cancelAnimation(){
        ValueAnimator running=animator;animator=null;
        if(running!=null)running.cancel();
        outgoingLayout=null;
    }
    /** Stop the spring-back and the boundary nudge; both are fire-and-forget effects. */
    private void cancelEffects(){
        ValueAnimator running=spring;spring=null;
        if(running!=null)running.cancel();
        cancelNudge();
    }
    private void finishTransition(){
        cancelAnimation();cancelEffects();stopDust();
        slideFraction=1;verticalTracking=false;clearDrag();invalidate();
    }
    public void finishMotion(){finishTransition();}
    private void stopDust(){ValueAnimator running=dustAnimator;dustAnimator=null;if(running!=null)running.cancel();dustFraction=1;}
    private void startDust(int direction){
        stopDust();if(!snowDust||!animationsAllowed()||!isAttachedToWindow()||getWindowVisibility()!=VISIBLE)return;
        dustDirection=direction;dustFraction=0;ValueAnimator animation=ValueAnimator.ofFloat(0,1);dustAnimator=animation;
        animation.setDuration(620);animation.setInterpolator(new android.view.animation.LinearInterpolator());
        animation.addUpdateListener(v->{if(dustAnimator==animation){dustFraction=(Float)v.getAnimatedValue();invalidate();}});
        animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator value){if(dustAnimator==animation){dustAnimator=null;dustFraction=1;invalidate();}}});animation.start();
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)finishTransition();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused)finishTransition();}
    private void clearDrag(){dragging=false;dragRaw=0;dragOffset=0;dragNeighbour=null;pendingTurn=0f;neighbourDirection=0;++neighbourRequest;}
    void setNotes(java.util.Collection<BookNote> notes){typography.notes=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(notes));}
    public void setHighlight(int highlight,String term){
        if(this.highlight==highlight&&this.term.equals(term==null?"":term))return;
        this.highlight=highlight;this.term=term==null?"":term;finishTransition();if(currentIndex!=null)render();
    }
    private void render(){
        SpannableString body=typography.style(displayText,offset,endOffset,paint.getTextSize(),getResources().getDisplayMetrics().density);
        if(highlight>=offset-term.length()&&highlight<endOffset&&!term.isEmpty()){
            int a=Math.max(0,highlight-offset),b=Math.min(body.length(),highlight-offset+term.length());
            if(b>a)body.setSpan(new BackgroundColorSpan(highlightColor),a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if(listenStart>=0&&listenEnd>listenStart){int a=Math.max(0,listenStart-offset),b=Math.min(body.length(),listenEnd-offset);if(b>a)body.setSpan(new BackgroundColorSpan(0x606AAE86),a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        layout=typography.layout(body,paint,contentWidth(),lineSpacing);invalidate();
    }
    public void next(){
        if(listener!=null)listener.userTurn();
        if(indexing){notice("正在定位页面，请稍候");return;}
        if(currentIndex==null)return;
        if(endOffset>=doc.text.length()){refuseTurn();return;}
        navigate(endOffset,1);
    }
    public void previous(){
        if(listener!=null)listener.userTurn();
        if(indexing){notice("正在定位页面，请稍候");return;}
        if(currentIndex==null)return;
        if(page>0)navigate(currentIndex.start(page-1),-1);
        else if(currentIndex.sectionStart>0)navigate(currentIndex.sectionStart-1,-1);
        else refuseTurn();
    }
    /**
     * At a boundary the page nudges and ticks instead of showing a toast: the toast is a
     * full-screen overlay that covered the text and broke the reading rhythm.
     */
    private void refuseTurn(){
        boundaryTick();
        if(!animationsAllowed())return;
        final float from=-12f*density;
        cancelNudge();
        ValueAnimator animation=ValueAnimator.ofFloat(0,1);nudge=animation;
        animation.setDuration(Motion.FAST);
        animation.addUpdateListener(value->{if(nudge==animation){nudgeValue=from*(1f-(Float)value.getAnimatedValue());invalidate();}});
        animation.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator value){if(nudge==animation){nudge=null;nudgeValue=0;invalidate();}}
        });
        animation.start();
    }
    private void cancelNudge(){
        ValueAnimator running=nudge;nudge=null;
        if(running!=null)running.cancel();
        nudgeValue=0;
    }
    private void changed(){if(listener!=null)listener.changed();}
    private void notice(String message){if(listener!=null)listener.notice(message);}

    // ---- finger-following page drag ----
    /** Start or continue a horizontal drag. The page follows the finger 1:1, with damping at the ends. */
    private void beginDrag(float distanceX){
        if(layout==null||indexing||disposed)return;
        float width=contentWidth();
        if(width<=0)return;
        if(!dragging){
            cancelAnimation();cancelEffects();
            dragging=true;dragRaw=0;dragOffset=0;pendingTurn=0f;
        }
        dragRaw+=distanceX;
        dragOffset=damped(dragRaw,width);
        int direction=logicalDirection(dragOffset);
        if(neighbourDirection!=direction){dragNeighbour=null;neighbourDirection=direction;requestDragNeighbour(direction);}
        invalidate();
    }
    /**
     * Rubber-band the displacement. Only a pull that has no page behind it is resisted; as long as
     * the turn is possible the page follows the finger exactly, one to one. Damping every pull in
     * one direction made half of all swipes feel sticky.
     */
    private float damped(float raw,float width){
        if(raw>0){
            // No page before this one: the pull is resisted.
            if(!canTurn(logicalDirection(raw)))return rubberBand(raw,width*0.55f);
            // Only one neighbour exists, so a pull beyond a full page has nothing to reveal.
            return Math.min(raw,width);
        }
        if(!canTurn(logicalDirection(raw)))return -rubberBand(-raw,width*0.55f);
        return Math.max(raw,-width);
    }
    /** Diminishing returns for an overshoot: small pulls move almost freely, large ones resist. */
    private static float rubberBand(float over,float dimension){
        if(over<=0f)return 0f;
        return (1f-1f/(over/dimension+1f))*dimension;
    }
    /**
     * Called when the finger lifts. The page either completes the turn or springs back,
     * decided by the travelled distance and the release speed.
     */
    private void settleDrag(){
        if(!dragging)return;
        float width=contentWidth();
        if(width<=0){dragging=false;dragRaw=0;dragOffset=0;return;}
        float travelled=Math.abs(dragOffset);
        float velocity=releaseVelocity(),speed=Math.abs(velocity);
        // Distance (30% of the page) or a quick flick completes the turn.
        boolean fast=speed>900f*density;
        boolean commit=fast?Math.signum(velocity)==Math.signum(dragOffset):travelled>width*.30f;
        int direction=logicalDirection(dragOffset);
        if(commit&&canTurn(direction)){
            dragging=false;
            // Keep the finger position and hand the remaining travel to the slide animation.
            pendingTurn=Math.min(1f,travelled/width);
            pendingDirection=dragOffset<0?1:-1;
            if(direction>0)next();else previous();
            return;
        }
        if(!canTurn(direction))boundaryTick();
        bounceBack();
    }
    /** Velocity of the release, in pixels per second. */
    private float releaseVelocity(){
        if(tracker==null)return 0f;
        tracker.computeCurrentVelocity(1000);
        float v=tracker.getXVelocity();
        tracker.clear();
        return v;
    }
    /** +1/-1 selects the next/previous text, independently from visual movement. */
    private int logicalDirection(float displacement){int physical=displacement<0?1:-1;return rightSwipeNext?-physical:physical;}
    private boolean canTurn(int direction){
        if(currentIndex==null)return false;
        if(direction>0)return endOffset<doc.text.length();
        return page>0||currentIndex.sectionStart>0;
    }
    /**
     * Animate the page back to its resting position.
     *
     * The displacement is not cleared before the animation starts: clearing it made the very first
     * frame show the page already centred, which read as a one-frame snap back. The spring owns the
     * offset until it ends, and only then is the drag state released.
     */
    private void bounceBack(){
        final float from=dragOffset;
        if(Math.abs(from)<.5f||!animationsAllowed()){clearDrag();invalidate();return;}
        cancelSpring();
        ValueAnimator animation=ValueAnimator.ofFloat(from,0);spring=animation;
        animation.setDuration(Motion.BASE);
        animation.setInterpolator(new android.view.animation.OvershootInterpolator(1.06f));
        animation.addUpdateListener(value->{if(spring==animation){dragOffset=(Float)value.getAnimatedValue();invalidate();}});
        animation.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator value){
                if(spring!=animation)return;
                spring=null;clearDrag();invalidate();
            }
        });
        animation.start();
    }
    private void cancelSpring(){
        ValueAnimator running=spring;spring=null;
        if(running!=null)running.cancel();
    }
    /** A short haptic tick when a turn is refused at a boundary. */
    private void boundaryTick(){performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);}

    /**
     * Pre-measure the page that the drag is heading towards, so the neighbour is visible
     * while the finger moves instead of appearing only after the turn commits.
     */
    private void requestDragNeighbour(int direction){
        if(currentIndex==null)return;
        final int targetOffset;
        if(direction>0)targetOffset=endOffset<doc.text.length()?endOffset:-1;
        else targetOffset=page>0?currentIndex.start(page-1):currentIndex.sectionStart>0?currentIndex.sectionStart-1:-1;
        if(targetOffset<0)return;
        final int section=doc.chapterAt(targetOffset);
        final int start=doc.chapters.get(section).offset,limit=doc.sectionEnd(section);
        final int token=ticket.get(),request=++neighbourRequest;
        final PageIndex known=cache.get(section);
        final int width=contentWidth(),height=contentHeight();
        final TextPaint measurePaint=new TextPaint(paint);
        // A dedicated thread: sharing the page-building worker made the neighbour arrive late,
        // so the page under the finger stayed blank while the finger moved.
        neighbourWorker.execute(()->{
            try{
                PageIndex index=PageIndex.extend(known,start,limit,targetOffset,
                    (at,end)->fitPage(displayText,at,end,measurePaint,width,height,lineSpacing),
                    ()->disposed||Thread.currentThread().isInterrupted());
                if(index==null)return;
                int at=index.start(index.pageAt(targetOffset)),end=index.end(index.pageAt(targetOffset));
                final StaticLayout prepared=typography.layout(typography.style(displayText,at,end,measurePaint.getTextSize(),density),measurePaint,width,lineSpacing);
                post(()->{if(!disposed&&ticket.get()==token&&request==neighbourRequest&&dragging){cache.put(section,index);dragNeighbour=prepared;invalidate();}});
            }catch(Exception ignored){}
        });
    }
    private void drawPage(Canvas canvas,StaticLayout page,float x){
        canvas.save();canvas.translate(x,0);canvas.clipRect(0,0,contentWidth(),contentHeight());
        canvas.drawColor(pageBackground);page.draw(canvas);canvas.restore();
    }
    /** A soft shadow along the seam between two pages reads better than a hard 1px line. */
    private void drawBoundary(Canvas canvas,float x,int direction){
        float width=contentWidth(),height=contentHeight();
        float depth=Math.min(16f*density,Math.max(6f*density,width*0.05f));
        if(depth<=0||x<=0||x>=width)return;
        if(depth!=shadowDepth){
            shadowDepth=depth;
            shadowLeft=new LinearGradient(0,0,-depth,0,edgeShadow.getColor(),Color.argb(0,20,50,60),Shader.TileMode.CLAMP);
            shadowRight=new LinearGradient(0,0,depth,0,edgeShadow.getColor(),Color.argb(0,20,50,60),Shader.TileMode.CLAMP);
        }
        edgeShadow.setShader(direction<0?shadowLeft:shadowRight);
        canvas.save();canvas.translate(x,0);
        canvas.drawRect(direction<0?-depth:0,0,direction<0?0:depth,height,edgeShadow);
        edgeLight.setColor(Color.argb(20,255,255,255));edgeLight.setStrokeWidth(Math.max(1,density*.5f));
        canvas.drawLine(0,0,0,height,edgeLight);canvas.restore();edgeShadow.setShader(null);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(layout!=null){
            canvas.save();canvas.clipRect(getPaddingLeft(),getPaddingTop(),getWidth()-getPaddingRight(),getHeight()-getPaddingBottom());
            canvas.translate(getPaddingLeft(),getPaddingTop());
            float width=contentWidth();
            if(outgoingLayout!=null){
                // A committed turn: the old page leaves while the new one slides in.
                if(curlMode&&motionEnabled)curl.draw(canvas,outgoingLayout,layout,width,contentHeight(),slideFraction,slideDirection,pageBackground);
                else {
                float oldX=-slideDirection*width*slideFraction,newX=slideDirection*width*(1-slideFraction);
                drawPage(canvas,outgoingLayout,oldX);drawPage(canvas,layout,newX);
                float boundary=slideDirection>0?newX:oldX;
                drawBoundary(canvas,boundary,-slideDirection);
                }
            }else if((dragging||pendingTurn>0f)&&dragOffset!=0){
                // The neighbour waits on the far side while the current page follows the finger.
                if(curlMode&&motionEnabled){curl.draw(canvas,layout,dragNeighbour!=null?dragNeighbour:layout,width,contentHeight(),Math.min(1,Math.abs(dragOffset)/width),dragOffset<0?1:-1,pageBackground);}
                else {
                if(dragNeighbour!=null){
                    float neighbourX=dragOffset<0?width+dragOffset:dragOffset-width;
                    drawPage(canvas,dragNeighbour,neighbourX);
                }
                drawPage(canvas,layout,dragOffset);
                float boundary=dragOffset<0?dragOffset+width:dragOffset;
                drawBoundary(canvas,boundary,dragOffset<0?-1:1);
                }
            }else drawPage(canvas,layout,nudgeValue);
            canvas.restore();
        }else canvas.drawText(indexing?"正在定位页面…":"请选择阅读位置",getPaddingLeft(),getPaddingTop()-paint.ascent(),paint);
        if(dustFraction<1)dust.draw(canvas,dustFraction,dustDirection,getWidth(),getHeight(),getPaddingLeft(),getPaddingRight(),density,paint.getColor()!=0&&((pageBackground>>16)&255)<90);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(tracker==null)tracker=VelocityTracker.obtain();
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            tracker.clear();
            if(indexing&&pendingTurn>0f)return true;
            finishTransition();
        }
        if(indexing&&pendingTurn>0f)return true;
        tracker.addMovement(event);
        boolean handled=gestures.onTouchEvent(event);
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_CANCEL){finishTransition();return true;}
        if(action==MotionEvent.ACTION_UP){
            if(verticalTracking){verticalTracking=false;float dy=event.getY()-verticalStartY;if(Math.abs(dy)>48*density){if(dy<0)next();else previous();}return true;}
            if(dragging)settleDrag();
        }
        return handled||dragging;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_PAGE_DOWN){next();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_PAGE_UP){previous();return true;}
        return super.onKeyDown(key,event);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.TextView");info.setText(layout==null?"正在定位页面":layout.getText());info.setScrollable(true);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK,"复制当前句"));info.setLongClickable(true);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
    @Override public boolean performAccessibilityAction(int action,Bundle args){
        if(action==AccessibilityNodeInfo.ACTION_LONG_CLICK){if(listener!=null)listener.copyAt(offset);return true;}
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){next();return true;}
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){previous();return true;}
        return super.performAccessibilityAction(action,args);
    }
    @Override protected void onDetachedFromWindow(){
        disposed=true;finishTransition();ticket.incrementAndGet();worker.shutdownNow();neighbourWorker.shutdownNow();
        if(tracker!=null){tracker.recycle();tracker=null;}
        super.onDetachedFromWindow();
    }
}
