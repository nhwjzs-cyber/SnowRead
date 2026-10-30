package com.snowread.app;

import android.app.Dialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Brand-owned bottom panel: no platform alert template, list rows, radios or button bar. */
final class AppSheet extends Dialog {
    static final int BUTTON_POSITIVE=-1,BUTTON_NEGATIVE=-2,BUTTON_NEUTRAL=-3;
    private final Builder spec;
    private final HashMap<Integer,Button> buttons=new HashMap<>();
    private ListView list;
    private SheetDragLayout dragSurface;
    private SnowCrystalView snow;
    private SpringSproutView sprout;
    private int decorToken;
    private boolean decorEmitted;
    private boolean dismissing,closed;
    private Runnable afterClose;
    private DialogInterface.OnDismissListener clientDismiss;
    private android.window.OnBackInvokedCallback taskBack;
    private static final Set<AppSheet> shown=Collections.newSetFromMap(new WeakHashMap<AppSheet,Boolean>());
    static void finishFor(android.app.Activity activity){for(AppSheet sheet:new ArrayList<>(shown))if(sheet.getOwnerActivity()==activity&&sheet.dragSurface!=null){Motion.finishTree(sheet.getWindow().getDecorView());Motion.finishWindow(sheet.getWindow());if(sheet.isShowing())sheet.dragSurface.snap();}}
    static void destroyFor(android.app.Activity activity){for(AppSheet sheet:new ArrayList<>(shown))if(sheet.getOwnerActivity()==activity)sheet.dismissNow();}
    void dismissNow(){afterClose=null;decorToken++;if(snow!=null)snow.stop();if(sprout!=null)sprout.stop();dismissing=true;if(dragSurface!=null)Motion.cancel(dragSurface);dismissDirect();}
    private final int paper,ink,muted,accent,soft;
    private int dp(float v){return Math.round(v*getContext().getResources().getDisplayMetrics().density);}
    AppSheet(Builder builder) {
        super(builder.context,builder.context.getResources().getIdentifier("SnowSheetWindow","style",builder.context.getPackageName()));spec=builder;requestWindowFeature(Window.FEATURE_NO_TITLE);
        paper=builder.dark?SnowTheme.NIGHT_SURFACE:SnowTheme.SURFACE;
        ink=builder.dark?SnowTheme.NIGHT_INK:SnowTheme.INK;
        muted=builder.dark?SnowTheme.NIGHT_MUTED:SnowTheme.MUTED;
        accent=builder.dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK;
        soft=builder.dark?SnowTheme.NIGHT_BACKGROUND:SnowTheme.ACCENT_SOFT;
        setCancelable(builder.cancelable);setCanceledOnTouchOutside(builder.cancelable);
        Context owner=builder.context;while(owner instanceof ContextWrapper&&!(owner instanceof android.app.Activity))owner=((ContextWrapper)owner).getBaseContext();if(owner instanceof android.app.Activity)setOwnerActivity((android.app.Activity)owner);
        setOnDismissListener(builder.dismiss);
    }
    private TextView text(String value,int size,int color) {
        TextView view=new TextView(getContext());view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SheetPanel panel=new SheetPanel(getContext());panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20),dp(spec.snowflakes?20:10),dp(20),dp(18));
        GradientDrawable surface=SnowTheme.rounded(paper,dp(26),spec.dark?0xFF294853:SnowTheme.BORDER,dp(1));surface.setCornerRadii(new float[]{dp(26),dp(26),dp(26),dp(26),0,0,0,0});panel.setBackground(surface);panel.setClickable(true);
        View handle=new View(getContext());handle.setBackground(SnowTheme.rounded(spec.dark?0xFF42616B:0xFFD0DFDF,dp(2),0,0));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(34),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(14);if(spec.cancelable){panel.addView(handle,hp);handle.setContentDescription("下滑关闭面板");}
        TextView eyebrow=text("融雪阅读",11,accent);eyebrow.setLetterSpacing(.12f);panel.addView(eyebrow);
        LinearLayout heading=new LinearLayout(getContext());heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(spec.title,21,ink);title.setTypeface(Typeface.create("sans-serif-medium",0));title.setMaxLines(3);title.setEllipsize(TextUtils.TruncateAt.END);
        if(android.os.Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(spec.header!=null)heading.addView(spec.header,new LinearLayout.LayoutParams(-2,dp(48)));
        if(spec.cancelable) {
            Button close=makeButton("×",BUTTON_NEGATIVE,false);close.setTextSize(26);close.setContentDescription("关闭面板");close.setOnClickListener(v->closeWithMotion());heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(-1,-2);headingParams.bottomMargin=dp(12);panel.addView(heading,headingParams);
        if(spec.message!=null) {
            TextView message=text(spec.message,14,muted);message.setLineSpacing(dp(5),1);message.setPadding(0,dp(2),0,dp(16));panel.addView(message);
        }
        int screen=getContext().getResources().getDisplayMetrics().heightPixels;
        if(spec.items!=null) {
            list=new ListView(getContext());list.setDivider(null);list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));list.setClipToPadding(false);
            list.setAdapter(new BaseAdapter() {
                public int getCount(){return spec.items.length;}
                public Object getItem(int p){return spec.items[p];}
                public long getItemId(int p){return p;}
                public View getView(int p,View reuse,ViewGroup parent) {
                    LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(58));row.setPadding(dp(14),dp(12),dp(12),dp(12));
                    boolean selected=p==spec.checked;
                    row.setBackground(SnowTheme.ripple(selected?soft:Color.TRANSPARENT,dp(14),0x20248FA9));
                    String hint=spec.itemHints!=null&&p<spec.itemHints.length?spec.itemHints[p]:null;
                    LinearLayout words=new LinearLayout(getContext());words.setOrientation(LinearLayout.VERTICAL);
                    TextView label=text(spec.items[p],15,selected?accent:ink);label.setMaxLines(3);label.setEllipsize(TextUtils.TruncateAt.END);words.addView(label,new LinearLayout.LayoutParams(-2,-2));
                    if(hint!=null&&!hint.isEmpty()){TextView sub=text(hint,11,muted);sub.setMaxLines(2);sub.setEllipsize(TextUtils.TruncateAt.END);words.addView(sub,new LinearLayout.LayoutParams(-2,-2));}
                    row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                    TextView arrow=text(selected?"✓":"›",19,accent);arrow.setGravity(Gravity.CENTER);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow,new LinearLayout.LayoutParams(dp(28),-2));
                    row.setContentDescription(spec.items[p]+(hint!=null&&!hint.isEmpty()?"，"+hint:"")+(selected?"，已选择":""));return row;
                }
            });
            // Measure wrapped titles instead of assuming every row is one line.
            int width=Math.min(getContext().getResources().getDisplayMetrics().widthPixels,dp(640))-dp(64),height=0,cap=(int)(screen*.48f);
            for(int i=0;i<spec.items.length&&height<cap;i++) {
                View row=list.getAdapter().getView(i,null,list);row.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(150),width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));height+=row.getMeasuredHeight();
            }
            panel.addView(list,new LinearLayout.LayoutParams(-1,Math.min(cap,height)));
            panel.body=list;panel.bodyHeight=Math.min(cap,height);
            list.setOnItemClickListener((parent,view,position,id)->dismissThen(()->{if(spec.itemClick!=null)spec.itemClick.onClick(this,position);}));
        }
        if(spec.view!=null) {
            styleInputs(spec.view);
            View content=spec.view;
            if(spec.fixedView)content.setLayoutParams(new LinearLayout.LayoutParams(-1,(int)(screen*.48f)));
            else if(!(content instanceof ScrollView)) {BoundedScroll scroll=new BoundedScroll(getContext(),(int)(screen*spec.contentFraction));scroll.setFillViewport(false);scroll.addView(content);content=scroll;}
            else { // The settings panel itself is scrollable and gets a bounded viewport.
                content.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(150),getContext().getResources().getDisplayMetrics().widthPixels-dp(64)),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                content.setLayoutParams(new LinearLayout.LayoutParams(-1,Math.min(content.getMeasuredHeight(),(int)(screen*.48f))));
            }
            panel.addView(content);
            panel.body=content;panel.bodyHeight=content.getLayoutParams().height;
        }
        if(spec.positive!=null) {Button action=makeButton(spec.positive,BUTTON_POSITIVE,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.topMargin=dp(16);panel.addView(action,p);}
        if(spec.negative!=null||spec.neutral!=null) {
            LinearLayout footer=new LinearLayout(getContext());
            if(spec.neutral!=null)footer.addView(makeButton(spec.neutral,BUTTON_NEUTRAL,false),new LinearLayout.LayoutParams(0,dp(48),1));
            if(spec.negative!=null)footer.addView(makeButton(spec.negative,BUTTON_NEGATIVE,false),new LinearLayout.LayoutParams(0,dp(48),1));
            panel.addView(footer);
        }
        FrameLayout outer=new FrameLayout(getContext());
        View scrim=new View(getContext());scrim.setBackgroundColor(Color.TRANSPARENT);scrim.setOnClickListener(v->{if(spec.cancelable)closeWithMotion();});outer.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        View navigationSurface=new View(getContext());navigationSurface.setBackgroundColor(paper);FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,0,Gravity.BOTTOM);outer.addView(navigationSurface,np);
        LinearLayout column=new LinearLayout(getContext());column.setOrientation(LinearLayout.VERTICAL);
        if(spec.snowflakes){
            // Sheets always carry the brand flurry and sprouts; weather ambience only decorates the shelf.
            FrameLayout decorated=new FrameLayout(getContext());decorated.addView(panel,new FrameLayout.LayoutParams(-1,-2));
            snow=new SnowCrystalView(getContext(),spec.dark);snow.setTag("sheet-snow");decorated.addView(snow,new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP));
            sprout=new SpringSproutView(getContext(),spec.dark);sprout.setTag("sheet-sprout");decorated.addView(sprout,new FrameLayout.LayoutParams(-1,dp(64),Gravity.BOTTOM));
            column.addView(decorated,new LinearLayout.LayoutParams(-1,-2));
        }else column.addView(panel,new LinearLayout.LayoutParams(-1,-2));
        View navigationInset=new View(getContext());navigationInset.setBackgroundColor(paper);column.addView(navigationInset,new LinearLayout.LayoutParams(-1,0));
        dragSurface=new SheetDragLayout(getContext(),scrim,getWindow(),spec.cancelable,()->dismissDirect());dragSurface.onClosing(()->{if(snow!=null)snow.disperse();if(sprout!=null)sprout.fadeOutNow();});dragSurface.addView(column,new FrameLayout.LayoutParams(-1,-2));
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(Math.min(getContext().getResources().getDisplayMetrics().widthPixels,dp(640)),-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);outer.addView(dragSurface,position);setContentView(outer);
        Window window=getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0);window.setWindowAnimations(0);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        EdgeToEdge.apply(window,spec.dark);
        EdgeToEdge.listen(outer,(left,top,right,navigation,keyboard)->{
            boolean typing=keyboard>navigation;FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)dragSurface.getLayoutParams();p.leftMargin=left;p.rightMargin=right;p.topMargin=top+dp(12);p.bottomMargin=typing?keyboard:0;p.width=Math.min(getContext().getResources().getDisplayMetrics().widthPixels-left-right,dp(640));dragSurface.setLayoutParams(p);
            navigationInset.getLayoutParams().height=typing?0:navigation;navigationInset.requestLayout();navigationSurface.getLayoutParams().height=typing?0:navigation;navigationSurface.requestLayout();
        });
    }
    @Override public void show(){if(isShowing())return;closed=false;dismissing=false;decorToken++;decorEmitted=false;super.show();shown.add(this);getWindow().setLayout(-1,-1);dragSurface.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(dragSurface.getHeight()==0)return true;dragSurface.getViewTreeObserver().removeOnPreDrawListener(this);if(isShowing()&&!dismissing){final int token=decorToken;dragSurface.enter(()->{if(token==decorToken&&!Motion.forcedCompletion())emitDecor();});}return true;}});}
    /** Decoration starts only when the enter (or post-drag settle) animation completes naturally;
     *  drags, dismissal and forced teardown clear the pending end action or bump the token. */
    private void emitDecor(){
        if(Motion.forcedCompletion()||decorEmitted||dismissing||closed||!isShowing()||!getWindow().getDecorView().hasWindowFocus()||!Motion.enabled(getContext()))return;
        decorEmitted=true;
        if(snow!=null)snow.play();
        if(sprout!=null)sprout.playAfter(130);
    }
    @Override public void dismiss(){if(!isShowing()||dragSurface==null){super.dismiss();return;}closeWithMotion();}
    private void dismissDirect(){if(closed)return;closed=true;decorToken++;if(snow!=null)snow.stop();if(sprout!=null)sprout.stop();shown.remove(this);Motion.finishWindow(getWindow());super.dismiss();}
    /** Run a menu action after the old window is gone, before its dismissal cleanup. */
    void dismissThen(Runnable action){if(dismissing||closed||!isShowing())return;afterClose=action;dismiss();}
    @Override public void setOnDismissListener(DialogInterface.OnDismissListener listener){
        clientDismiss=listener;
        super.setOnDismissListener(d->{
            Runnable action=afterClose;afterClose=null;DialogInterface.OnDismissListener cleanup=clientDismiss;
            android.app.Activity owner=getOwnerActivity();
            try{if(action!=null&&(owner==null||(!owner.isFinishing()&&!owner.isDestroyed())))action.run();}
            finally{if(cleanup!=null)cleanup.onDismiss(this);}
        });
    }
    boolean isDarkSurface(){return spec.dark;}
    @Override public void onBackPressed(){if(spec.back!=null)spec.back.run();else if(spec.cancelable)closeWithMotion();}
    @Override protected void onStart(){super.onStart();if(android.os.Build.VERSION.SDK_INT>=33&&spec.back!=null){taskBack=()->onBackPressed();getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,taskBack);}}
    @Override protected void onStop(){decorToken++;if(snow!=null)snow.stop();if(sprout!=null)sprout.stop();if(android.os.Build.VERSION.SDK_INT>=33&&taskBack!=null){getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(taskBack);taskBack=null;}super.onStop();}
    private void closeWithMotion(){if(dismissing)return;dismissing=true;if(dragSurface!=null)dragSurface.close();else dismissDirect();}
    Button getButton(int which){return buttons.get(which);}
    ListView getListView(){return list;}
    private Button makeButton(String label,int which,boolean primary) {
        Button button=new Button(getContext());button.setText(label);button.setAllCaps(false);button.setTextSize(14);button.setTextColor(primary?(spec.dark?SnowTheme.NIGHT_BACKGROUND:Color.WHITE):accent);
        button.setTypeface(Typeface.create("sans-serif-medium",0));button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(dp(8),0,dp(8),0);button.setBackgroundTintList(null);
        button.setBackground(SnowTheme.ripple(primary?accent:Color.TRANSPARENT,dp(16),0x30248FA9));
        button.setOnClickListener(v->dismissThen(()->{DialogInterface.OnClickListener callback=which==BUTTON_POSITIVE?spec.positiveClick:which==BUTTON_NEGATIVE?spec.negativeClick:spec.neutralClick;if(callback!=null)callback.onClick(this,which);}));
        buttons.put(which,button);return button;
    }
    private void styleInputs(View view) {
        if(view instanceof EditText) {
            EditText input=(EditText)view;input.setTextColor(ink);input.setHintTextColor(muted);input.setTextSize(15);input.setPadding(dp(14),dp(12),dp(14),dp(12));input.setMinimumHeight(dp(50));
            input.setBackgroundTintList(null);input.setBackground(SnowTheme.rounded(soft,dp(14),0,0));
        }
        if(view instanceof SeekBar) {
            SeekBar slider=(SeekBar)view;slider.setProgressTintList(ColorStateList.valueOf(accent));slider.setProgressBackgroundTintList(ColorStateList.valueOf(soft));
            GradientDrawable thumb=SnowTheme.rounded(accent,dp(12),paper,dp(3));thumb.setSize(dp(24),dp(24));slider.setThumb(thumb);slider.setThumbOffset(dp(12));slider.setSplitTrack(false);slider.setPadding(dp(12),0,dp(12),0);
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)styleInputs(((ViewGroup)view).getChildAt(i));
    }
    private static final class BoundedScroll extends ScrollView {
        final int cap;
        BoundedScroll(Context context,int cap){super(context);this.cap=cap;}
        @Override protected void onMeasure(int w,int h){int mode=View.MeasureSpec.getMode(h),limit=mode==View.MeasureSpec.UNSPECIFIED?cap:Math.min(cap,View.MeasureSpec.getSize(h));super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(limit,mode==View.MeasureSpec.EXACTLY?View.MeasureSpec.EXACTLY:View.MeasureSpec.AT_MOST));}
    }
    /** Reserve room for actions when the keyboard or landscape reduces available height. */
    private static final class SheetPanel extends LinearLayout {
        View body;int bodyHeight;
        SheetPanel(Context context){super(context);}
        @Override protected void onMeasure(int w,int h) {
            int cap=(int)(getResources().getDisplayMetrics().heightPixels*.85f);
            if(View.MeasureSpec.getMode(h)!=View.MeasureSpec.UNSPECIFIED)cap=Math.min(cap,View.MeasureSpec.getSize(h));
            if(body!=null)body.getLayoutParams().height=bodyHeight;
            super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST));
            if(body!=null) {
                int other=getPaddingTop()+getPaddingBottom();
                for(int i=0;i<getChildCount();i++)if(getChildAt(i)!=body){View child=getChildAt(i);LayoutParams p=(LayoutParams)child.getLayoutParams();other+=child.getMeasuredHeight()+p.topMargin+p.bottomMargin;}
                int fit=Math.max(0,cap-other);
                if(body.getMeasuredHeight()>fit){body.getLayoutParams().height=fit;super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST));}
            }
        }
    }
    static final class Builder {
        final Context context;final boolean dark;
        String title="",message,positive,negative,neutral;
        String[] items,itemHints;int checked=-1;View view,header;boolean cancelable=true,fixedView,snowflakes;
        float contentFraction=.48f;
        DialogInterface.OnClickListener positiveClick,negativeClick,neutralClick,itemClick;
        DialogInterface.OnDismissListener dismiss;
        Runnable back;
        Builder setOnBack(Runnable value){back=value;return this;}
        Builder(Context context,boolean dark){this.context=context;this.dark=dark;}
        Builder setSnowflakes(boolean value){snowflakes=value;return this;}
        Builder setTitle(String value){title=value;return this;}
        Builder setMessage(String value){message=value;return this;}
        Builder setView(View value){view=value;return this;}
        Builder setContentFraction(float value){contentFraction=Math.max(.3f,Math.min(.65f,value));return this;}
        Builder setFixedView(View value){view=value;fixedView=true;return this;}
        Builder setHeaderView(View value){header=value;return this;}
        Builder setPositiveButton(String value,DialogInterface.OnClickListener click){positive=click==null&&(value.equals("完成")||value.equals("关闭")||value.equals("知道了")||value.equals("回到书架"))?null:value;positiveClick=click;return this;}
        Builder setNegativeButton(String value,DialogInterface.OnClickListener click){negative=click==null?null:value;negativeClick=click;return this;}
        Builder setNeutralButton(String value,DialogInterface.OnClickListener click){neutral=value;neutralClick=click;return this;}
        Builder setItems(String[] value,DialogInterface.OnClickListener click){items=value;itemClick=click;return this;}
        /** Optional one-line explanations shown under each item so menus read at a glance. */
        Builder setItemHints(String[] value){itemHints=value;return this;}
        Builder setSingleChoiceItems(String[] value,int selected,DialogInterface.OnClickListener click){items=value;checked=selected;itemClick=click;return this;}
        Builder setCancelable(boolean value){cancelable=value;return this;}
        Builder setOnDismissListener(DialogInterface.OnDismissListener value){dismiss=value;return this;}
        AppSheet create(){return new AppSheet(this);}
        AppSheet show(){AppSheet sheet=create();sheet.show();return sheet;}
    }
}
