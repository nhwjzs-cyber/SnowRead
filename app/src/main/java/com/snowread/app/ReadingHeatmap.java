package com.snowread.app;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Monday-first local heatmap; paired with an accessible ordinary date selector. */
final class ReadingHeatmap extends View {
    interface Selected {void select(String day);}
    private final ReadingStats stats;private final List<String> days;private final boolean dark;private final Selected selected;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF rect=new RectF();private String day;
    private final float density;private float left,top,step,cell;
    ReadingHeatmap(Context c,ReadingStats stats,List<String> days,String day,boolean dark,Selected selected){super(c);this.stats=stats;this.days=days;this.day=day;this.dark=dark;this.selected=selected;density=c.getResources().getDisplayMetrics().density;setMinimumHeight(Math.round(192*density));setContentDescription("近16周阅读日历，颜色越深表示时长越多。可使用下方日期按钮选择日期。");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    void select(String value){day=value;invalidate();}
    protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),Math.round(196*density));}
    protected void onDraw(Canvas canvas){super.onDraw(canvas);left=25*density;top=25*density;step=Math.min((getWidth()-left-2*density)/16f,19*density);cell=Math.max(1,step-3*density);
        paint.setTextSize(10*density);paint.setColor(dark?SnowTheme.NIGHT_MUTED:SnowTheme.MUTED);String lastMonth="";
        for(int col=0;col<16;col++){String month=days.get(col*7).substring(5,7);if(!month.equals(lastMonth)){canvas.drawText(Integer.parseInt(month)+"月",left+col*step,14*density,paint);lastMonth=month;}}
        String[] weekdays={"一","三","五","日"};for(int i=0;i<4;i++)canvas.drawText(weekdays[i],0,top+step*(i*2)+cell*.8f,paint);
        String today=ReadingStatsData.day(System.currentTimeMillis());int[] colors=dark?new int[]{0xFF273039,0xFF294C47,0xFF397360,0xFF58A487,0xFF82C5A7}:new int[]{0xFFE2E7DF,0xFFC4DDD0,0xFF8BBCA5,0xFF54947C,0xFF286E5E};
        for(int i=0;i<days.size();i++){String date=days.get(i);rect.set(left+(i/7)*step,top+(i%7)*step,left+(i/7)*step+cell,top+(i%7)*step+cell);paint.setStyle(Paint.Style.FILL);paint.setColor(colors[ReadingStatsData.level(stats.day(date))]);paint.setAlpha(date.compareTo(today)>0?55:255);canvas.drawRoundRect(rect,3*density,3*density,paint);paint.setAlpha(255);if(date.equals(day)){paint.setStyle(Paint.Style.STROKE);paint.setColor(dark?SnowTheme.NIGHT_INK:SnowTheme.ACCENT_DARK);paint.setStrokeWidth(1.4f*density);canvas.drawRoundRect(rect,3*density,3*density,paint);}}
        paint.setStyle(Paint.Style.FILL);paint.setColor(dark?SnowTheme.NIGHT_MUTED:SnowTheme.MUTED);paint.setTextSize(10*density);canvas.drawText("少      5分      20分      1小时+",left,top+7*step+18*density,paint);
    }
    public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN)return true;if(e.getAction()==MotionEvent.ACTION_UP){performClick();int col=(int)((e.getX()-left)/step),row=(int)((e.getY()-top)/step);if(e.getX()>=left&&e.getY()>=top&&col>=0&&col<16&&row>=0&&row<7){String picked=days.get(col*7+row);if(picked.compareTo(ReadingStatsData.day(System.currentTimeMillis()))<=0){day=picked;invalidate();selected.select(day);}}return true;}return super.onTouchEvent(e);}
    public boolean performClick(){super.performClick();return true;}
}
