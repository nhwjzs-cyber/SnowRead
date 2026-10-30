package com.snowread.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.tts.*;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Presentation and runtime permission flow only. Playback remains in ListenService. */
final class ListenUi {
    private static final int NOTIFY_PERMISSION=81;
    private final MainActivity host;
    private ListenService service;
    private ListenService.Snapshot snapshot;
    private boolean bound,visible,seeking,destroyed;
    private String pendingAction;
    private Bundle pendingExtras;
    private AppSheet panel;
    private TextView title,chapter,preview,status,rateLabel,timerLabel,miniTitle;
    private Button play,miniPlay;
    private SeekBar position;
    private LinearLayout mini;private ListeningBars miniWave;
    private final ListenService.Listener listener;
    private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){if(!bound||!visible||destroyed)return;service=((ListenService.LocalBinder)binder).service();service.uiVisible(visible);service.addListener(listener);}public void onServiceDisconnected(ComponentName name){service=null;}};
    ListenUi(MainActivity host,Bundle saved){this.host=host;listener=value->{snapshot=value;refresh();host.onListeningChanged(value);};if(saved!=null){pendingAction=saved.getString("listenPendingAction");pendingExtras=saved.getBundle("listenPendingExtras");}}
    void start(){visible=true;if(!bound)bound=host.bindService(new Intent(host,ListenService.class),connection,Context.BIND_AUTO_CREATE);if(service!=null)service.uiVisible(true);}
    void stop(){if(miniWave!=null)miniWave.playing(false);visible=false;if(service!=null){service.uiVisible(false);service.removeListener(listener);}if(bound){host.unbindService(connection);bound=false;}service=null;}
    void destroy(){destroyed=true;if(panel!=null){panel.dismissNow();panel=null;}stop();}
    void save(Bundle out){out.putString("listenPendingAction",pendingAction);out.putBundle("listenPendingExtras",pendingExtras);}
    ListenService.Snapshot snapshot(){return snapshot;}
    void refreshState(){if(service!=null)service.refreshNow();}
    private int dp(float value){return Math.round(value*host.getResources().getDisplayMetrics().density);}
    private int ink(){return host.isNight()?SnowTheme.NIGHT_INK:SnowTheme.INK;}
    private int accent(){return host.isNight()?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK;}
    private int muted(){return host.isNight()?SnowTheme.NIGHT_MUTED:SnowTheme.MUTED;}
    private LinearLayout column(){LinearLayout view=new LinearLayout(host);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(host);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private TextView text(String label,int size,int color){TextView view=new TextView(host);view.setText(label);view.setTextSize(size);view.setTextColor(color);return view;}
    private Button button(String name,Runnable click){Button button=new Button(host);button.setText(name);button.setTextColor(accent());button.setTextSize(13);button.setAllCaps(false);button.setMinimumWidth(0);button.setMinWidth(0);button.setPadding(dp(8),0,dp(8),0);button.setMinHeight(dp(48));button.setBackgroundTintList(null);button.setBackground(SnowTheme.ripple(Color.TRANSPARENT,dp(14),0x20248FA9));button.setOnClickListener(v->click.run());return button;}
    private AppSheet.Builder sheet(){int style=host.getResources().getIdentifier(host.isNight()?"SnowDialogDark":"SnowDialogLight","style",host.getPackageName());return new AppSheet.Builder(new ContextThemeWrapper(host,style),host.isNight());}
    View miniBar(){if(miniWave!=null)miniWave.playing(false);mini=column();mini.setVisibility(View.GONE);mini.setPadding(dp(10),dp(6),dp(6),dp(6));android.graphics.drawable.GradientDrawable tint=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,new int[]{host.isNight()?SnowTheme.NIGHT_SURFACE:SnowTheme.SURFACE,host.isNight()?0xFF203F4A:0xFFE6F2F2});tint.setCornerRadius(dp(16));mini.setBackground(tint);LinearLayout controls=row();miniWave=new ListeningBars(host,accent());miniWave.setTag("listen-wave");LinearLayout.LayoutParams waveParams=new LinearLayout.LayoutParams(dp(22),dp(26));waveParams.rightMargin=dp(8);controls.addView(miniWave,waveParams);miniTitle=text("",12,ink());miniTitle.setMaxLines(2);miniTitle.setEllipsize(TextUtils.TruncateAt.END);miniTitle.setOnClickListener(v->openBook());controls.addView(miniTitle,new LinearLayout.LayoutParams(0,-2,1));miniPlay=button("▶",()->toggle());miniPlay.setContentDescription("播放或暂停听书");controls.addView(miniPlay,new LinearLayout.LayoutParams(dp(44),dp(48)));Button stop=button("×",()->stopPlayback());stop.setContentDescription("停止听书");controls.addView(stop,new LinearLayout.LayoutParams(dp(44),dp(48)));Button open=button("打开",()->openBook());controls.addView(open,new LinearLayout.LayoutParams(dp(52),dp(48)));mini.addView(controls);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);mini.setLayoutParams(p);refresh();return mini;}
    void open(){
        if(host.listenBook()==null){openBook();return;}if(panel!=null&&panel.isShowing())return;
        LinearLayout body=column();title=text(host.listenBook().title,17,ink());title.setMaxLines(2);body.addView(title);chapter=text("从当前阅读位置开始",12,muted());chapter.setPadding(0,dp(6),0,dp(6));body.addView(chapter);
        preview=text("点击播放，使用系统语音逐句朗读。",15,ink());preview.setMaxLines(2);preview.setEllipsize(TextUtils.TruncateAt.END);preview.setPadding(dp(12),dp(12),dp(12),dp(12));preview.setBackground(SnowTheme.rounded(host.isNight()?SnowTheme.NIGHT_BACKGROUND:SnowTheme.PAPER,dp(14),0,0));body.addView(preview);
        status=text("听书使用系统语音；优先离线语音，锁屏播放需要后台播放支持。",12,muted());status.setPadding(0,dp(8),0,dp(4));body.addView(status);
        position=new SeekBar(host);position.setMax(1000);body.addView(position,new LinearLayout.LayoutParams(-1,dp(44)));position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){seeking=true;}public void onStopTrackingTouch(SeekBar bar){seeking=false;if(snapshot!=null)command(ListenService.SEEK,new BundleBuilder().integer("offset",(int)((snapshot.length-1)*bar.getProgress()/1000f)).build());}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)chapter.setText(String.format(Locale.CHINA,"跳到全书 %.1f%%",value/10f));}});
        LinearLayout primary=row();primary.addView(button("上一句",()->command(ListenService.PREVIOUS,null)),new LinearLayout.LayoutParams(0,dp(50),1));play=button("开始听书",()->toggle());play.setTypeface(Typeface.DEFAULT,Typeface.BOLD);primary.addView(play,new LinearLayout.LayoutParams(0,dp(50),1));primary.addView(button("下一句",()->command(ListenService.NEXT,null)),new LinearLayout.LayoutParams(0,dp(50),1));body.addView(primary);
        LinearLayout secondary=row();secondary.addView(button("上一章",()->command(ListenService.PREV_CHAPTER,null)),new LinearLayout.LayoutParams(0,dp(48),1));secondary.addView(button("停止",()->stopPlayback()),new LinearLayout.LayoutParams(0,dp(48),1));secondary.addView(button("下一章",()->command(ListenService.NEXT_CHAPTER,null)),new LinearLayout.LayoutParams(0,dp(48),1));body.addView(secondary);
        rateLabel=text("语速 · 1.0×",13,ink());rateLabel.setPadding(0,dp(12),0,dp(6));body.addView(rateLabel);LinearLayout rates=row();for(float speed:new float[]{.8f,1,1.2f,1.5f,2})rates.addView(button(String.format(Locale.CHINA,"%.1f×",speed),()->command(ListenService.RATE,new BundleBuilder().decimal("rate",speed).build())),new LinearLayout.LayoutParams(0,dp(44),1));body.addView(rates);
        body.addView(button("语音 / 引擎 / 试听",()->voices()));timerLabel=text("睡眠定时 · 未开启",13,accent());timerLabel.setPadding(dp(8),dp(12),dp(8),dp(12));timerLabel.setOnClickListener(v->timer());body.addView(timerLabel);
        body.addView(button("范围：当前页 / 本章 / 全书",()->scope()));body.addView(button("跟随朗读 / 连播 / 隐私 / 语速",()->options()));body.addView(button("从当前阅读位置重新开始",()->startCurrent()));body.addView(button("离线语音设置 / 修复",()->repair()));
        panel=sheet().setTitle("听书").setView(body).setOnDismissListener(d->{panel=null;title=chapter=preview=status=rateLabel=timerLabel=null;play=null;position=null;}).show();refresh();
    }
    private boolean currentOwned(){return snapshot!=null&&host.listenBook()!=null&&snapshot.owns(host.listenBook().id);}
    private void toggle(){if(snapshot!=null&&snapshot.playing()){send(ListenService.PAUSE,null);return;}if(currentOwned()||host.listenBook()==null&&snapshot!=null&&!snapshot.bookId.isEmpty())startWithPermission(snapshot!=null&&snapshot.state==SpeechQueue.State.ERROR?ListenService.RETRY:ListenService.PLAY,null);else startCurrent();}
    void startAt(int offset){LibraryStore.Book book=host.listenBook();if(book==null)return;host.listenSaveReading();Bundle extras=new Bundle();extras.putString("book",book.id);extras.putInt("offset",Math.max(0,offset));startWithPermission(ListenService.LOAD,extras);}
    private void startCurrent(){LibraryStore.Book book=host.listenBook();if(book==null){host.listenToast("请先打开一本书");return;}host.listenSaveReading();Bundle extras=new Bundle();extras.putString("book",book.id);extras.putInt("offset",host.listenOffset());startWithPermission(ListenService.LOAD,extras);}
    private void command(String action,Bundle extras){if(!currentOwned()&&host.listenBook()!=null&&!action.equals(ListenService.RATE)){startCurrent();return;}if(action.equals(ListenService.RATE)&&!currentOwned()){host.getSharedPreferences("listen",0).edit().putFloat("rate",extras.getFloat("rate")).apply();refresh();return;}send(action,extras);}
    private void send(String action,Bundle extras){if(destroyed)return;try{ListenService.send(host,action,extras);}catch(Exception e){host.listenToast("无法启动听书："+e.getMessage());}}
    private void startWithPermission(String action,Bundle extras){
        if(Build.VERSION.SDK_INT>=33&&host.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&host.getSharedPreferences("listen",0).getBoolean("notificationAsked",false)&&!host.getSharedPreferences("listen",0).getBoolean("background",true)){Bundle data=extras==null?new Bundle():new Bundle(extras);data.putBoolean("background",false);send(action,data);return;}
        if(Build.VERSION.SDK_INT>=33&&host.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){pendingAction=action;pendingExtras=extras;if(!host.getSharedPreferences("listen",0).getBoolean("notificationAsked",false)){host.getSharedPreferences("listen",0).edit().putBoolean("notificationAsked",true).apply();host.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY_PERMISSION);}else permissionChoice();return;}
        Bundle data=extras==null?new Bundle():new Bundle(extras);data.putBoolean("background",true);send(action,data);
    }
    boolean permission(int request,int[] grants){if(request!=NOTIFY_PERMISSION)return false;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){Bundle data=pendingExtras==null?new Bundle():new Bundle(pendingExtras);data.putBoolean("background",true);send(pendingAction==null?ListenService.PLAY:pendingAction,data);pendingAction=null;pendingExtras=null;}else permissionChoice();return true;}
    private void permissionChoice(){sheet().setTitle("通知权限未开启").setMessage("可以仅在应用前台听书。媒体通知可能受系统豁免或设置影响，锁屏控制请在手机确认；也可去设置开启通知。").setItems(new String[]{"仅前台听书","打开通知设置"},(d,w)->{if(w==0){Bundle data=pendingExtras==null?new Bundle():new Bundle(pendingExtras);data.putBoolean("background",false);send(pendingAction==null?ListenService.PLAY:pendingAction,data);pendingAction=null;pendingExtras=null;}else{Intent settings=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,host.getPackageName());launch(settings);}}).show();}
    void stopPlayback(){if(service!=null)service.stopNow();else send(ListenService.STOP,null);host.clearListeningHighlight();}
    void stopIfBook(String id){if(snapshot!=null&&snapshot.owns(id))stopPlayback();}
    private void openBook(){if(snapshot==null||snapshot.bookId.isEmpty()){host.listenToast("请先在阅读页点击耳机图标");return;}host.listenOpenBook(snapshot.bookId);}
    private void refresh(){
        if(mini!=null){boolean has=snapshot!=null&&!snapshot.bookId.isEmpty()&&snapshot.state!=SpeechQueue.State.IDLE;Motion.visible(mini,has);if(miniWave!=null)miniWave.playing(has&&snapshot.playing());if(has){miniTitle.setText((snapshot.playing()?"正在听":"已暂停")+"《"+snapshot.title+"》 · "+snapshot.chapter);miniPlay.setText(snapshot.playing()?"Ⅱ":"▶");}}
        if(panel==null||!panel.isShowing()||title==null)return;
        boolean own=currentOwned();if(!own){play.setText(snapshot!=null&&snapshot.playing()?"停止试听":"开始听书");if(snapshot!=null&&!snapshot.message.isEmpty())status.setText(snapshot.message);rateLabel.setText(String.format(Locale.CHINA,"语速 · %.1f×",host.getSharedPreferences("listen",0).getFloat("rate",1)));return;}
        title.setText(snapshot.title);if(!seeking){chapter.setText(snapshot.chapter+String.format(Locale.CHINA," · %.1f%%",100.0*snapshot.offset/Math.max(1,snapshot.length-1)));position.setProgress((int)(1000.0*snapshot.offset/Math.max(1,snapshot.length-1)));}
        SpannableString line=new SpannableString(snapshot.preview);int a=Math.max(0,snapshot.start-snapshot.offset),b=Math.min(line.length(),snapshot.end-snapshot.offset);if(b>a&&a<line.length())line.setSpan(new BackgroundColorSpan(host.isNight()?0xFF51664A:0xFFDCEBE0),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);preview.setText(line);status.setText(snapshot.message+(snapshot.background?"":" · 仅前台"));play.setText(snapshot.playing()?"暂停":snapshot.state==SpeechQueue.State.PREPARING?"准备中…":snapshot.state==SpeechQueue.State.ERROR?"重试":"播放");rateLabel.setText(String.format(Locale.CHINA,"语速 · %.1f×",snapshot.rate));timerLabel.setText("睡眠定时 · "+snapshot.timer);
    }
    private void timer(){
        boolean extendable=snapshot!=null&&snapshot.timer!=null&&snapshot.timer.contains("分钟后");
        ArrayList<String> labels=new ArrayList<>();if(extendable)labels.add("延长 10 分钟");
        labels.addAll(java.util.Arrays.asList("关闭定时","15 分钟","30 分钟","60 分钟","本章结束","当前句结束"));
        sheet().setTitle("睡眠定时").setMessage("到点后在当前句结束时暂停。"+(extendable?"当前定时可以在剩余时间上再加 10 分钟。":"")).setItems(labels.toArray(new String[0]),(d,w)->{
            if(extendable&&w==0){long now=System.currentTimeMillis();long end=Math.max(host.getSharedPreferences("listen",0).getLong("timerEnd",0),now)+10*60000L;host.getSharedPreferences("listen",0).edit().putLong("timerEnd",end).apply();Bundle data=new Bundle();data.putInt("minutes",10);data.putBoolean("extend",true);if(currentOwned())send(ListenService.TIMER,data);if(timerLabel!=null)timerLabel.setText("睡眠定时 · "+Math.max(1,(end-now+59999)/60000)+" 分钟后");return;}
            int index=extendable?w-1:w;Bundle data=new Bundle();int minutes=index>=1&&index<=3?new int[]{15,30,60}[index-1]:0;int mode=index==4?SleepTimer.CHAPTER:index==5?SleepTimer.SENTENCE:minutes>0?SleepTimer.TIME:SleepTimer.NONE;if(minutes>0)data.putInt("minutes",minutes);else data.putInt("mode",mode);host.getSharedPreferences("listen",0).edit().putInt("timerMode",mode).putLong("timerEnd",minutes>0?System.currentTimeMillis()+minutes*60000L:0).apply();if(currentOwned())send(ListenService.TIMER,data);else if(timerLabel!=null)timerLabel.setText("睡眠定时 · "+labels.get(w));
        }).show();
    }
    private void scope(){if(!currentOwned()){host.listenToast("请先开始听书，再调整朗读范围");return;}sheet().setTitle("朗读范围").setItems(new String[]{"当前阅读页","当前章","全书"},(d,w)->{TextDocument doc=host.listenDocument();if(doc==null)return;int from=w==0?host.listenOffset():snapshot==null?host.listenOffset():snapshot.offset;int end=w==0?host.listenWindowEnd():w==1?doc.sectionEnd(doc.chapterAt(from)):doc.text.length();send(ListenService.SCOPE,new BundleBuilder().integer("offset",from).integer("end",end).build());}).show();}
    private void options(){android.content.SharedPreferences prefs=host.getSharedPreferences("listen",0);LinearLayout body=column();ToggleRow follow=new ToggleRow(host,"自动跟随朗读",prefs.getBoolean("follow",true),host.isNight()),auto=new ToggleRow(host,"自动连续播放下一章",prefs.getBoolean("autoNext",true),host.isNight()),titles=new ToggleRow(host,"朗读章节标题",prefs.getBoolean("readTitles",true),host.isNight()),privacy=new ToggleRow(host,"锁屏隐藏书名",prefs.getBoolean("privacy",true),host.isNight());body.addView(follow);body.addView(auto);body.addView(titles);body.addView(privacy);ToggleRow numbers=new ToggleRow(host,"跳过独立页码行",prefs.getBoolean("skipPageNumbers",false),host.isNight()),urls=new ToggleRow(host,"不朗读常见网址",prefs.getBoolean("skipUrls",false),host.isNight());body.addView(numbers);body.addView(urls);ToggleRow ahead=new ToggleRow(host,"预备下一句，减少句间停顿",prefs.getBoolean("precache",true),host.isNight());body.addView(ahead);TextView speed=text("语速 0.5–2.0，步进 0.1",13,muted());body.addView(speed);SeekBar rate=new SeekBar(host);rate.setMax(15);rate.setProgress(Math.round((prefs.getFloat("rate",1)-.5f)*10));body.addView(rate);sheet().setSnowflakes(true).setTitle("听书偏好").setView(body).setOnDismissListener(d->{prefs.edit().putBoolean("precache",ahead.checked()).putBoolean("skipPageNumbers",numbers.checked()).putBoolean("skipUrls",urls.checked()).putBoolean("follow",follow.checked()).putBoolean("autoNext",auto.checked()).putBoolean("readTitles",titles.checked()).putBoolean("privacy",privacy.checked()).putFloat("rate",.5f+rate.getProgress()/10f).apply();if(!destroyed){if(follow.checked())host.listenRestoreFollow();else host.listenDisableFollow();}if(!destroyed&&currentOwned()){send(ListenService.RATE,new BundleBuilder().decimal("rate",.5f+rate.getProgress()/10f).build());send(ListenService.OPTIONS,null);}}).show();}
    private void voices(){if(service==null){host.listenToast("语音服务尚未就绪，请稍候");return;}sheet().setTitle("语音与引擎").setItems(new String[]{"选择离线语音","选择系统 TTS 引擎","试听当前语音","系统语音设置","安装语音数据"},(d,w)->{if(w==0)voiceList();else if(w==1)engineList();else if(w==2)startWithPermission(ListenService.PREVIEW,null);else if(w==3)settings();else install();}).show();}
    private void voiceList(){if(service==null)return;ArrayList<Voice> voices=service.voices();if(voices.isEmpty()){host.listenToast("请先初始化语音，或在系统中安装离线语音包");settings();return;}String[] labels=new String[voices.size()];for(int i=0;i<labels.length;i++){Voice v=voices.get(i);labels[i]=v.getLocale().getDisplayName(Locale.CHINA)+" · 离线\n"+v.getName();}sheet().setTitle("选择离线语音").setItems(labels,(d,w)->command(ListenService.VOICE,new BundleBuilder().string("name",voices.get(w).getName()).build())).show();}

    private void engineList(){if(service==null)return;List<TextToSpeech.EngineInfo> engines=service.engines();if(engines.isEmpty()){settings();return;}String[] names=new String[engines.size()+1];names[0]="跟随系统默认引擎";for(int i=0;i<engines.size();i++)names[i+1]=engines.get(i).label;sheet().setTitle("系统语音引擎").setItems(names,(d,w)->send(ListenService.ENGINE,new BundleBuilder().string("name",w==0?"":engines.get(w-1).name).build())).show();}
    private void repair(){sheet().setTitle("离线听书设置与修复").setMessage("正文交给你选择的系统语音引擎处理，仅接受标记为离线的语音。引擎是独立应用，其行为取决于该引擎；建议使用可信的离线引擎。安装语音包时系统可能需要联网。").setItems(new String[]{"重新初始化并重试","打开系统语音设置","安装离线语音数据","申请通知 / 开启后台播放"},(d,w)->{if(w==0)command(ListenService.RETRY,null);else if(w==1)settings();else if(w==2)install();else{host.getSharedPreferences("listen",0).edit().putBoolean("notificationAsked",false).apply();if(currentOwned())startWithPermission(ListenService.PLAY,null);else startCurrent();}}).show();}

    private void settings(){launch(new Intent("com.android.settings.TTS_SETTINGS"));}
    private void install(){Intent intent=new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);String name=host.getSharedPreferences("listen",0).getString("engine","");if(!name.isEmpty())intent.setPackage(name);launch(intent);}
    private void launch(Intent intent){try{host.startActivity(intent);}catch(Exception e){try{host.startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception ignored){host.listenToast("当前系统未提供语音设置入口");}}}
    private static final class BundleBuilder {final Bundle data=new Bundle();BundleBuilder integer(String k,int v){data.putInt(k,v);return this;}BundleBuilder decimal(String k,float v){data.putFloat(k,v);return this;}BundleBuilder string(String k,String v){data.putString(k,v);return this;}Bundle build(){return data;}}
}
