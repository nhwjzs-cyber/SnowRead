package com.snowread.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** Small custom controls shared by the paper-and-cyan settings panel. */
final class ChoiceStrip extends LinearLayout {
    private int selected;
    private Runnable changed;
    void onChanged(Runnable action){changed=action;}
    private final int accent,soft,ink;
    private final Paint pillPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill=new RectF(),target=new RectF();
    private void movePill(boolean animate){
        if(getChildCount()==0||getWidth()==0)return;
        View child=getChildAt(selected);target.set(child.getLeft(),child.getTop(),child.getRight(),child.getBottom());
        float left=pill.left,top=pill.top,right=pill.right,bottom=pill.bottom;
        float endLeft=target.left,endTop=target.top,endRight=target.right,endBottom=target.bottom;
        if(!animate||pill.isEmpty()){Motion.cancel(this);pill.set(target);invalidate();return;}
        Motion.interpolate(this,0,1,Motion.BASE,value->{pill.set(left+(endLeft-left)*value,top+(endTop-top)*value,right+(endRight-right)*value,bottom+(endBottom-bottom)*value);invalidate();});
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);View child=getChildAt(selected);if(target.left!=child.getLeft()||target.top!=child.getTop()||target.right!=child.getRight()||target.bottom!=child.getBottom())movePill(false);}
    @Override protected void dispatchDraw(Canvas canvas){pillPaint.setColor(accent==SnowTheme.NIGHT_ACCENT?SnowTheme.NIGHT_SURFACE:SnowTheme.SURFACE);canvas.drawRoundRect(pill,dp(12),dp(12),pillPaint);super.dispatchDraw(canvas);}
    @Override protected void onDetachedFromWindow(){Motion.cancel(this);pill.set(target);super.onDetachedFromWindow();}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    ChoiceStrip(Context context,String[] labels,int current,boolean dark) {
        super(context);selected=current;accent=dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK;soft=dark?SnowTheme.NIGHT_BACKGROUND:SnowTheme.BACKGROUND;ink=dark?SnowTheme.NIGHT_INK:SnowTheme.INK;
        boolean compact=getResources().getConfiguration().screenWidthDp<360||getResources().getConfiguration().fontScale>1.15f;
        setOrientation(compact?VERTICAL:HORIZONTAL);setPadding(dp(4),dp(4),dp(4),dp(4));setBackground(SnowTheme.rounded(soft,dp(16),0,0));
        for(int i=0;i<labels.length;i++) {
            final int index=i;TextView button=new TextView(context);button.setText(labels[i]);button.setTextSize(14);button.setGravity(Gravity.CENTER);button.setPadding(dp(8),dp(12),dp(8),dp(12));button.setMinimumHeight(dp(48));button.setFocusable(true);
            LayoutParams p=new LayoutParams(compact?-1:0,-2,compact?0:1);addView(button,p);button.setOnClickListener(v->{selected=index;update();movePill(true);if(changed!=null)changed.run();});
        }
        update();
    }
    int selected(){return selected;}
    void select(int index){selected=Math.max(0,Math.min(getChildCount()-1,index));update();movePill(true);}
    private void update(){for(int i=0;i<getChildCount();i++){TextView b=(TextView)getChildAt(i);boolean on=i==selected;b.setTextColor(on?accent:ink);b.setSelected(on);b.setContentDescription(b.getText()+(on?"，已选择":""));b.setBackground(SnowTheme.ripple(Color.TRANSPARENT,dp(12),0x20248FA9));}}
}

final class ToggleRow extends LinearLayout {
    private Runnable changed;
    void onChanged(Runnable action){changed=action;}
    private boolean checked;private float position;
    private final String label;
    private final int accent,soft;
    private final View toggle;
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    ToggleRow(Context context,String label,boolean checked,boolean dark) {
        super(context);this.label=label;this.checked=checked;position=checked?1:0;accent=dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK;soft=dark?0xFF42616B:0xFFD0DEDE;
        setGravity(Gravity.CENTER_VERTICAL);setMinimumHeight(dp(60));setPadding(0,dp(8),0,dp(8));setFocusable(true);setClickable(true);setBackground(SnowTheme.ripple(Color.TRANSPARENT,dp(14),0x20248FA9));
        TextView title=new TextView(context);title.setText(label);title.setTextSize(14);title.setTextColor(dark?SnowTheme.NIGHT_INK:SnowTheme.INK);title.setPadding(0,0,dp(12),0);title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(title,new LayoutParams(0,-2,1));
        toggle=new View(context) {
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){paint.setColor(Color.rgb(Math.round(Color.red(soft)+(Color.red(accent)-Color.red(soft))*position),Math.round(Color.green(soft)+(Color.green(accent)-Color.green(soft))*position),Math.round(Color.blue(soft)+(Color.blue(accent)-Color.blue(soft))*position)));c.drawRoundRect(0,0,getWidth(),getHeight(),dp(14),dp(14),paint);paint.setColor(dark?SnowTheme.NIGHT_SURFACE:Color.WHITE);c.drawCircle(dp(13)+(getWidth()-dp(26))*position,getHeight()/2f,dp(9),paint);}
        };
        toggle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(toggle,new LayoutParams(dp(46),dp(26)));setContentDescription(label);setOnClickListener(v->{this.checked=!this.checked;Motion.interpolate(toggle,position,this.checked?1:0,Motion.FAST,value->{position=value;toggle.invalidate();});if(changed!=null)changed.run();sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);});
    }
    boolean checked(){return checked;}
    void checked(boolean value){checked=value;Motion.cancel(toggle);position=value?1:0;toggle.invalidate();}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Switch");info.setCheckable(true);info.setChecked(checked);}
}

final class ThemeSwatches extends LinearLayout {
    interface Changed {void select(int index);}
    private int selected;
    void select(int index){selected=index;refresh();}
    private final int accent,ink;
    private final String[] names={"原纸","暖纸","夜读"};private final float[] pulses={1,1,1};
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    ThemeSwatches(Context context,int current,boolean dark,Changed changed){
        super(context);selected=current;accent=dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK;ink=dark?SnowTheme.NIGHT_INK:SnowTheme.INK;setOrientation(HORIZONTAL);
        int[] colors={SnowTheme.PAPER,SnowTheme.WARM_PAPER,SnowTheme.NIGHT_BACKGROUND};
        for(int i=0;i<3;i++){final int index=i;LinearLayout cell=new LinearLayout(context);cell.setOrientation(VERTICAL);cell.setGravity(Gravity.CENTER);cell.setPadding(dp(4),dp(8),dp(4),dp(8));cell.setFocusable(true);cell.setBackground(SnowTheme.ripple(Color.TRANSPARENT,dp(16),0x20248FA9));
            View circle=new View(context){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);@Override protected void onDraw(Canvas canvas){float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-dp(7);paint.setStyle(Paint.Style.FILL);paint.setColor(colors[index]);canvas.drawCircle(cx,cy,r,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(selected==index?3:1));paint.setColor(selected==index?accent:0xFF9EB2B5);canvas.drawCircle(cx,cy,r,paint);if(pulses[index]<1){paint.setStrokeWidth(dp(2));paint.setColor((Math.round((1-pulses[index])*130)<<24)|(accent&0xFFFFFF));canvas.drawCircle(cx,cy,r+dp(5)*pulses[index],paint);}paint.setStyle(Paint.Style.FILL);paint.setColor(index==2?SnowTheme.NIGHT_INK:SnowTheme.INK);paint.setTextSize(dp(17));paint.setTypeface(Typeface.SERIF);String value=selected==index?"✓":"Aa";canvas.drawText(value,cx-paint.measureText(value)/2,cy-(paint.ascent()+paint.descent())/2,paint);}};
            circle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);cell.addView(circle,new LayoutParams(dp(58),dp(58)));TextView name=new TextView(context);name.setText(names[i]);name.setTextSize(12);name.setTextColor(ink);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(6),0,0);name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);cell.addView(name);addView(cell,new LayoutParams(0,-2,1));
            cell.setOnClickListener(v->{boolean different=selected!=index;selected=index;refresh();if(different){Motion.interpolate(circle,0,1,240,value->{pulses[index]=value;circle.invalidate();});for(int j=0;j<getChildCount();j++)SnowBurst.stop(((ViewGroup)getChildAt(j)).getChildAt(0));SnowBurst.play(circle,dark||index==2);}changed.select(index);});
        }refresh();
    }
    private void refresh(){for(int i=0;i<getChildCount();i++){View cell=getChildAt(i);cell.setSelected(i==selected);cell.setContentDescription(names[i]+(i==selected?"，已选择":""));((ViewGroup)cell).getChildAt(0).invalidate();}}
}
