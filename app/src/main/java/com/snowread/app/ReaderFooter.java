package com.snowread.app;

import android.content.*;
import android.os.*;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.*;
import java.util.Date;

/** Quiet, paper-coloured status row, independent of the transient action toolbars. */
final class ReaderFooter extends LinearLayout {
    private final TextView progress,clock,battery;
    private final LinearLayout meta;
    private final Context receiverContext;
    private boolean resumed,registered,stacked;
    private int percent=-1;private boolean charging;
    private final Runnable fallbackTick=new Runnable(){public void run(){if(!resumed||!isAttachedToWindow())return;refreshClock();postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    private final BroadcastReceiver events=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
        if(Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction()))updateBattery(intent);
        refreshClock();
    }};
    ReaderFooter(Context context,int paper,int textColor,int margin){
        super(context);receiverContext=context.getApplicationContext();setTag("reader-page-footer");setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setBackgroundColor(paper);
        int side=dp(Math.min(24,Math.max(12,margin)));setPadding(side,dp(5),side,dp(5));setMinimumHeight(dp(28));
        progress=label(textColor,"reader-footer-progress");progress.setEllipsize(TextUtils.TruncateAt.END);progress.setText("0.0%");addView(progress,new LayoutParams(0,-2,1));
        meta=new LinearLayout(context);meta.setOrientation(HORIZONTAL);meta.setGravity(Gravity.CENTER_VERTICAL);
        clock=label(textColor,"reader-footer-clock");battery=label(textColor,"reader-footer-battery");battery.setPadding(dp(12),0,0,0);
        meta.addView(clock,new LayoutParams(-2,-2));meta.addView(battery,new LayoutParams(-2,-2));LayoutParams info=new LayoutParams(-2,-2);info.leftMargin=dp(12);addView(meta,info);
        refreshClock();readBattery();
    }
    private TextView label(int color,String tag){TextView v=new TextView(getContext());v.setTextSize(11);v.setTextColor(color);v.setIncludeFontPadding(false);v.setSingleLine(true);v.setTag(tag);return v;}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void progress(String value){if(!value.contentEquals(progress.getText()))progress.setText(value);progress.setContentDescription("阅读进度 "+value);}
    void resumed(boolean value){resumed=value;if(value){refreshClock();readBattery();start();}else stop();}
    private void refreshClock(){
        String value=DateFormat.getTimeFormat(getContext()).format(new Date());clock.setText(value);clock.setContentDescription("时间 "+value);
        // Reserve digit widths, so a minute/battery change does not move the text viewport.
        String reserve=DateFormat.is24HourFormat(getContext())?"23:59":"12:59 PM";
        clock.setMinWidth(Math.max(clock.getMinWidth(),Math.round(Math.max(clock.getPaint().measureText(reserve),clock.getPaint().measureText(value)))));
    }
    private void readBattery(){try{Intent sticky=receiverContext.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(sticky!=null)updateBattery(sticky);else showBattery();}catch(RuntimeException ignored){showBattery();}}
    private void updateBattery(Intent intent){
        percent=ReaderStatusText.batteryPercent(intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),intent.getIntExtra(BatteryManager.EXTRA_SCALE,0));
        charging=intent.getIntExtra(BatteryManager.EXTRA_STATUS,-1)==BatteryManager.BATTERY_STATUS_CHARGING;showBattery();
    }
    private void showBattery(){battery.setText(ReaderStatusText.battery(percent));battery.setContentDescription(ReaderStatusText.battery(percent)+(charging?"，正在充电":""));battery.setMinWidth(Math.round(battery.getPaint().measureText("电量 100%"))+battery.getPaddingLeft());}
    private void start(){
        if(!resumed||!isAttachedToWindow()||registered)return;
        IntentFilter filter=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);filter.addAction(Intent.ACTION_TIME_TICK);filter.addAction(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        try{Intent sticky=Build.VERSION.SDK_INT>=33?receiverContext.registerReceiver(events,filter,Context.RECEIVER_NOT_EXPORTED):receiverContext.registerReceiver(events,filter);registered=true;if(sticky!=null)updateBattery(sticky);}
        catch(RuntimeException ignored){removeCallbacks(fallbackTick);postDelayed(fallbackTick,60000-System.currentTimeMillis()%60000);}
    }
    private void stop(){removeCallbacks(fallbackTick);if(registered){try{receiverContext.unregisterReceiver(events);}catch(RuntimeException ignored){}registered=false;}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();start();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);float minimumProgress=progress.getPaint().measureText("100.0%");
        boolean narrow=MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED&&width-getPaddingLeft()-getPaddingRight()<minimumProgress+clock.getMinWidth()+battery.getMinWidth()+dp(20);
        if(narrow!=stacked){stacked=narrow;setOrientation(narrow?VERTICAL:HORIZONTAL);
            progress.setLayoutParams(new LayoutParams(narrow?-1:0,-2,narrow?0:1));
            LayoutParams p=new LayoutParams(narrow?-1:-2,-2);p.leftMargin=narrow?0:dp(12);p.topMargin=narrow?dp(3):0;meta.setLayoutParams(p);
            clock.setLayoutParams(new LayoutParams(narrow?0:-2,-2,narrow?1:0));battery.setGravity(narrow?Gravity.RIGHT:Gravity.LEFT);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
