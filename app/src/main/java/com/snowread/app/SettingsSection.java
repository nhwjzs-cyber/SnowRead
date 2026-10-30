package com.snowread.app;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** A settings category with one accessible header; hidden controls retain their current values. */
final class SettingsSection extends LinearLayout {
    final String key;
    final LinearLayout body;
    final LinearLayout header;
    private final TextView summary,arrow,chevron;
    private final String title;
    private boolean expanded=true;
    private Runnable toggle;private final android.graphics.Paint activeLine=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    SettingsSection(Context context,String key,String title,String hint,boolean dark){
        super(context);this.key=key;this.title=title;activeLine.setColor(dark?0x608CD6E5:0x70248FA9);setTag("settings-"+key);setOrientation(VERTICAL);
        setBackground(SnowTheme.rounded(dark?SnowTheme.NIGHT_BACKGROUND:SnowTheme.BACKGROUND,dp(18),dark?0xFF294853:SnowTheme.BORDER,dp(1)));
        header=new LinearLayout(context){
            @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.Button");
                info.addAction(expanded?AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE:AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
            }
            @Override public boolean performAccessibilityAction(int action,Bundle args){
                if(action==AccessibilityNodeInfo.ACTION_EXPAND||action==AccessibilityNodeInfo.ACTION_COLLAPSE){
                    if((action==AccessibilityNodeInfo.ACTION_EXPAND)!=expanded)performClick();return true;
                }
                return super.performAccessibilityAction(action,args);
            }
        };
        header.setGravity(Gravity.CENTER_VERTICAL);header.setMinimumHeight(dp(64));header.setPadding(dp(14),dp(10),dp(12),dp(10));header.setFocusable(true);header.setClickable(true);
        header.setBackground(SnowTheme.ripple(0,dp(18),dark?0x208CD6E5:0x18248FA9));
        LinearLayout labels=new LinearLayout(context);labels.setOrientation(VERTICAL);labels.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        TextView label=new TextView(context);label.setText(title);label.setTextSize(15);label.setTextColor(dark?SnowTheme.NIGHT_INK:SnowTheme.INK);label.setTypeface(Typeface.create("sans-serif-medium",0));labels.addView(label);
        summary=new TextView(context);summary.setText(hint);summary.setTextSize(12);summary.setTextColor(dark?SnowTheme.NIGHT_MUTED:SnowTheme.MUTED);summary.setPadding(0,dp(4),0,0);labels.addView(summary);
        header.addView(labels,new LayoutParams(0,-2,1));
        arrow=new TextView(context);arrow.setTextSize(12);arrow.setTextColor(dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK);arrow.setPadding(dp(12),0,0,0);arrow.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);header.addView(arrow);
        chevron=new TextView(context);chevron.setText("▾");chevron.setTextSize(15);chevron.setGravity(Gravity.CENTER);chevron.setTextColor(dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK);chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);header.addView(chevron,new LayoutParams(dp(22),dp(32)));
        addView(header,new LayoutParams(-1,-2));
        body=new LinearLayout(context);body.setOrientation(VERTICAL);body.setPadding(dp(14),0,dp(14),dp(12));addView(body,new LayoutParams(-1,-2));
        header.setOnClickListener(v->{if(toggle!=null)toggle.run();else expanded(!expanded);});expanded(false);
    }
    void onToggle(Runnable action){toggle=action;}
    boolean expanded(){return expanded;}
    void expanded(boolean value){
        boolean changed=expanded!=value;expanded=value;updateHeader();if(!changed)return;
        Motion.cancel(body);Motion.cancel(chevron);
        if(!isAttachedToWindow()||body.getWidth()==0||!Motion.enabled(getContext())){finishBody();chevron.setRotation(value?180:0);return;}
        int from=body.getVisibility()==GONE?0:body.getHeight();body.setVisibility(VISIBLE);
        body.setImportantForAccessibility(value?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int target=0;if(value){body.measure(MeasureSpec.makeMeasureSpec(getWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));target=body.getMeasuredHeight();}
        final int end=target;body.getLayoutParams().height=from;
        Motion.interpolate(chevron,chevron.getRotation(),value?180:0,Motion.BASE,angle->chevron.setRotation(angle));
        Motion.interpolate(body,from,end,Motion.BASE,height->{body.getLayoutParams().height=Math.max(0,Math.round(height));body.requestLayout();if(Math.abs(height-end)<.5f)finishBody();});
    }
    private void finishBody(){body.getLayoutParams().height=LayoutParams.WRAP_CONTENT;body.setVisibility(expanded?VISIBLE:GONE);body.setImportantForAccessibility(expanded?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);body.requestLayout();}
    private void updateHeader(){arrow.setText(expanded?"收起":"展开");header.setContentDescription(title+"，"+summary.getText()+"，"+(expanded?"已展开，点按收起":"已收起，点按展开"));invalidate();}
    void summary(String value){summary.setText(value);updateHeader();}
    void reveal(){header.post(()->{if(expanded)requestRectangleOnScreen(new Rect(0,0,getWidth(),header.getHeight()+dp(96)),false);});}
    @Override protected void dispatchDraw(android.graphics.Canvas canvas){super.dispatchDraw(canvas);if(expanded){canvas.drawRoundRect(dp(1),dp(16),dp(3),Math.max(dp(17),getHeight()-dp(16)),dp(1),dp(1),activeLine);}}
    @Override protected void onDetachedFromWindow(){Motion.cancel(body);Motion.cancel(chevron);finishBody();super.onDetachedFromWindow();}
}
