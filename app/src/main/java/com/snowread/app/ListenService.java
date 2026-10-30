package com.snowread.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.speech.tts.*;
import java.util.*;

/** Sole owner of playback. Activity binds for immutable state and sends explicit commands. */
public final class ListenService extends Service {
    static final String LOAD="load",PLAY="play",PAUSE="pause",STOP="stop",PREVIOUS="previous",NEXT="next",PREV_CHAPTER="prevChapter",NEXT_CHAPTER="nextChapter",SEEK="seek",RATE="rate",TIMER="timer",VOICE="voice",ENGINE="engine",PREVIEW="preview",OPTIONS="options",RETRY="retry",SCOPE="scope";
    private static final int NOTIFICATION=701;
    interface Listener {void changed(Snapshot state);}
    static final class Snapshot {
        final String bookId,title,chapter,preview,message,voice,timer;
        final SpeechQueue.State state;
        final int offset,start,end,length;
        final float rate;
        final boolean background,networkRequired,follow,autoNext,privacy;
        Snapshot(ListenService service){LibraryStore.Book b=service.book;SpeechQueue q=service.queue;bookId=b==null?"":b.id;title=b==null?"":b.title;chapter=q==null?"":q.document.readingTitle(q.position());preview=q==null||q.current==null?"":q.document.text.substring(q.current.start,q.current.end).replace('\n',' ');message=service.message;voice=service.engine==null?"语音尚未就绪":service.engine.voiceName();timer=service.sleep.label(System.currentTimeMillis());state=service.state;offset=q==null?service.savedOffset:q.position();start=service.rangeStart;end=service.rangeEnd;length=b==null?0:b.length;rate=service.rate;background=service.background;networkRequired=service.networkRequired;follow=service.prefs.getBoolean("follow",true);autoNext=service.prefs.getBoolean("autoNext",true);privacy=service.prefs.getBoolean("privacy",true);}
        boolean owns(String id){return !bookId.isEmpty()&&bookId.equals(id)&&state!=SpeechQueue.State.IDLE;}
        boolean playing(){return state==SpeechQueue.State.PLAYING;}
    }
    public final class LocalBinder extends Binder {ListenService service(){return ListenService.this;}}
    private final LocalBinder binder=new LocalBinder();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinkedHashSet<Listener> listeners=new LinkedHashSet<>();
    private LibraryRepository repository;
    private android.content.SharedPreferences prefs;
    private LibraryStore.Book book;
    private SpeechQueue queue;
    private SpeechQueue.State state=SpeechQueue.State.IDLE;
    private TtsEngine engine;
    private final SleepTimer sleep=new SleepTimer();
    private String message="",utterance="";
    private int loadingGeneration,savedOffset,rangeStart=-1,rangeEnd=-1,retries;
    private float rate=1;
    private boolean background=true,uiVisible,wantPlay,foreground,resumeOnFocus,networkRequired,previewPending,previewing,destroyed;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wake;
    private Runnable speechTimeout;
    private final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context c,Intent i){pause(true,"耳机已断开，已暂停");}};
    private final AudioManager.OnAudioFocusChangeListener focusListener=change->main.post(()->{
        if(destroyed)return;
        if(change==AudioManager.AUDIOFOCUS_GAIN){boolean resume=resumeOnFocus;resumeOnFocus=false;if(resume&&(background||uiVisible))resume();}
        else if(change==AudioManager.AUDIOFOCUS_LOSS){resumeOnFocus=false;pause(true,"其他音频正在使用，已暂停");}
        else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){boolean playing=state==SpeechQueue.State.PLAYING||resumeOnFocus;pause(false,"音频被暂时打断，已暂停");resumeOnFocus=playing;}
    });
    static void send(Context context,String action,Bundle extras){
        Intent intent=new Intent(context,ListenService.class).setAction(action).putExtra("fromUi",true);if(extras!=null)intent.putExtras(extras);
        boolean bg=extras!=null&&extras.containsKey("background")?extras.getBoolean("background"):context.getSharedPreferences("listen",MODE_PRIVATE).getBoolean("background",true);
        if(Build.VERSION.SDK_INT>=26&&bg&&!action.equals(STOP))context.startForegroundService(intent);else context.startService(intent);
    }
    @Override public void onCreate(){
        super.onCreate();prefs=getSharedPreferences("listen",MODE_PRIVATE);prefs.edit().remove("allowNetworkVoice").apply();rate=Math.max(.5f,Math.min(2f,prefs.getFloat("rate",1)));background=prefs.getBoolean("background",true);sleep.restore(prefs.getInt("timerMode",0),prefs.getLong("timerEnd",0),System.currentTimeMillis());
        try{repository=LibraryRepository.get(this);book=repository.store().find(prefs.getString("book",""));if(book!=null){savedOffset=prefs.getInt("offset",book.offset);if(prefs.getBoolean("restorePending",true)){repository.progress(book,savedOffset,true,e->storageError(e));prefs.edit().putBoolean("restorePending",false).apply();}state=SpeechQueue.State.PAUSED;message="已恢复听书断点，点击播放继续";}}catch(Exception e){state=SpeechQueue.State.ERROR;message="无法读取书库："+e.getMessage();}
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"snowread:listen");wake.setReferenceCounted(false);
        session=new MediaSession(this,"SnowReadListen");session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback(){public void onPlay(){startFromMedia(PLAY);}public void onPause(){pause(true,"已暂停");}public void onStop(){stopListening();}public void onSkipToPrevious(){startFromMedia(PREV_CHAPTER);}public void onSkipToNext(){startFromMedia(NEXT_CHAPTER);}},main);
        if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel("listen","听书播放",NotificationManager.IMPORTANCE_LOW);channel.setSound(null,null);channel.enableVibration(false);channel.setShowBadge(false);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);}
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        engine=new TtsEngine(this,new TtsEngine.Callback(){
            public void ready(){networkRequired=false;if(previewPending){previewPending=false;preview();}else if(wantPlay)speak();else{state=SpeechQueue.State.PAUSED;message="语音就绪，点击播放";publish(true);}}
            public void start(String id){if(previewing&&id.equals(utterance)){message="正在试听语音";publish(true);return;}if(queue==null||!queue.accepts(id))return;rangeStart=queue.current.start;rangeEnd=queue.current.end;save(false);publish(true);}
            public void range(String id,int start,int end){if(previewing||queue==null||!queue.accepts(id)||queue.current==null||end<=start)return;rangeStart=queue.current.absoluteStart(start);rangeEnd=queue.current.absoluteEnd(end);publish(false);}
            public void done(String id){if(previewing&&id.equals(utterance)){previewing=false;pause(true,"试听结束，点击播放继续听书");if(book==null){stopForeground(true);foreground=false;stopSelf();}return;}if(queue==null||!queue.accepts(id))return;cancelTimeout();if(advanceChunk())speak();}
            public void error(String id,int code){if(!id.equals(utterance))return;cancelTimeout();if(!previewing&&queue!=null&&queue.accepts(id)&&retries++<1){engine.stop();speak();}else failure("语音引擎朗读失败（"+code+"），可重试或更换语音");}
            public void unavailable(String error,boolean network){networkRequired=network;failure(error);}
        });publish(true);
    }
    @Override public IBinder onBind(Intent intent){return binder;}
    Snapshot snapshot(){return new Snapshot(this);}
    void refreshNow(){publish(false);}
    void addListener(Listener listener){listeners.add(listener);listener.changed(snapshot());}
    void removeListener(Listener listener){listeners.remove(listener);}
    ArrayList<Voice> voices(){return engine.voices();}
    List<TextToSpeech.EngineInfo> engines(){return engine.engines();}
    void uiVisible(boolean visible){uiVisible=visible;if(!visible&&!background){pause(true,"仅前台听书：离开界面后已暂停");stopSelf();}}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){if(book!=null&&background){if(!promote())return START_NOT_STICKY;state=SpeechQueue.State.PAUSED;message="播放服务已恢复，点击继续";publish(true);return START_STICKY;}stopSelf();return START_NOT_STICKY;}
        String action=intent.getAction();if(action==null)action=PLAY;if(intent.getBooleanExtra("fromUi",false))uiVisible=true;
        if(intent.hasExtra("background")){background=intent.getBooleanExtra("background",true);prefs.edit().putBoolean("background",background).apply();}
        if(action.equals(STOP)){stopListening();return START_NOT_STICKY;}
        if(background&&!foreground&&!promote())return START_NOT_STICKY;
        if(action.equals(LOAD)){rate=Math.max(.5f,Math.min(2f,prefs.getFloat("rate",1)));sleep.restore(prefs.getInt("timerMode",0),prefs.getLong("timerEnd",0),System.currentTimeMillis());load(intent.getStringExtra("book"),intent.getIntExtra("offset",0),true);}
        else if(action.equals(PLAY))resume();
        else if(action.equals(RETRY)){engine.close();resume();}
        else if(action.equals(PAUSE))pause(true,"已暂停");
        else if(action.equals(PREVIOUS))sentence(-1);
        else if(action.equals(NEXT))sentence(1);
        else if(action.equals(PREV_CHAPTER))chapter(-1);
        else if(action.equals(NEXT_CHAPTER))chapter(1);
        else if(action.equals(SEEK))seek(intent.getIntExtra("offset",0));
        else if(action.equals(RATE)){rate=Math.max(.5f,Math.min(2,intent.getFloatExtra("rate",1)));prefs.edit().putFloat("rate",rate).apply();restartCurrent();}
        else if(action.equals(TIMER)){int minutes=intent.getIntExtra("minutes",0);if(intent.getBooleanExtra("extend",false)&&minutes>0&&sleep.mode==SleepTimer.TIME)sleep.deadline=Math.max(sleep.deadline,System.currentTimeMillis())+minutes*60000L;else if(minutes>0)sleep.minutes(minutes,System.currentTimeMillis());else sleep.setMode(intent.getIntExtra("mode",SleepTimer.NONE));rememberTimer();publish(true);}
        else if(action.equals(VOICE)||action.equals(ENGINE)){String key=action.equals(VOICE)?"voice":"engine";prefs.edit().putString(key,intent.getStringExtra("name")==null?"":intent.getStringExtra("name")).apply();if(action.equals(ENGINE))prefs.edit().remove("voice").apply();boolean playing=state==SpeechQueue.State.PLAYING;pause(true,"正在切换语音…");wantPlay=playing;state=SpeechQueue.State.PREPARING;engine.init();publish(true);}
        else if(action.equals(PREVIEW))preview();
        else if(action.equals(SCOPE)&&queue!=null){engine.stop();queue.pause();int from=intent.getIntExtra("offset",queue.position()),end=intent.getIntExtra("end",queue.document.text.length());queue.scopeEnd=queue.document.text.length();queue.seek(from);queue.setScope(end);speak();}
        else if(action.equals(OPTIONS)){prefs.edit().remove("allowNetworkVoice").apply();publish(true);}
        return background?START_STICKY:START_NOT_STICKY;
    }
    private void startFromMedia(String action){try{Intent intent=new Intent(this,ListenService.class).setAction(action);if(Build.VERSION.SDK_INT>=26&&background)startForegroundService(intent);else startService(intent);}catch(Exception e){failure("无法恢复后台播放，请回到应用重试");}}
    private boolean promote(){try{if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(NOTIFICATION,notification());foreground=true;return true;}catch(Exception e){message="无法启动后台播放："+e.getMessage();state=SpeechQueue.State.ERROR;wantPlay=false;release(true);publish(false);stopSelf();return false;}}
    private void load(String id,int offset,boolean play){
        if(repository==null){failure("书库暂不可用，请重新打开应用");return;}LibraryStore.Book selected=repository.store().find(id);if(selected==null){failure("这本书已不在书架中");return;}
        save(true);int token=++loadingGeneration;cancelTimeout();engine.stop();release(true);book=selected;repository.listenOwner=book.id;queue=null;savedOffset=Math.max(0,Math.min(offset,Math.max(0,book.length-1)));rangeStart=rangeEnd=-1;state=SpeechQueue.State.PREPARING;wantPlay=play;message="正在准备语音…";remember();publish(true);
        repository.io.execute(()->{try{TextDocument doc=repository.load(selected);main.post(()->{if(destroyed||token!=loadingGeneration)return;queue=new SpeechQueue(doc,savedOffset,Math.min(300,TextToSpeech.getMaxSpeechInputLength()));queue.autoNext=prefs.getBoolean("autoNext",true);retries=0;if(engine.ready()){if(wantPlay)speak();else{state=SpeechQueue.State.PAUSED;publish(true);}}else engine.init();});}catch(Exception e){main.post(()->{if(token==loadingGeneration&&!destroyed)failure("无法读取小说："+e.getMessage());});}});
    }
    private void resume(){
        resumeOnFocus=false;if(sleep.mode==SleepTimer.TIME&&sleep.deadline<=System.currentTimeMillis()){sleep.clear();rememberTimer();}if(book==null){String id=prefs.getString("book","");if(repository!=null)book=repository.store().find(id);if(book==null){failure("请先打开一本书，再从当前文字位置开始听书");return;}}
        if(background&&!foreground&&!promote())return;
        if(!background&&!uiVisible){pause(true,"仅前台听书，请回到应用继续");return;}
        wantPlay=true;if(queue==null){load(book.id,savedOffset,true);return;}if(!engine.ready()){state=SpeechQueue.State.PREPARING;message="正在初始化系统语音…";engine.init();publish(true);return;}retries=0;speak();
    }
    private void speak(){
        if(queue==null||!engine.ready())return;cancelTimeout();
        int skipped=0;while(queue.current!=null){boolean omit=!prefs.getBoolean("readTitles",true)&&queue.title()||prefs.getBoolean("skipPageNumbers",false)&&TextChunker.pageNumber(queue.current);TextChunker.SpeechChunk cleaned=prefs.getBoolean("skipUrls",false)?TextChunker.withoutUrls(queue.current):queue.current;if(!omit&&cleaned!=null){queue.current=cleaned;break;}if(!advanceChunk())return;if(++skipped>=100){main.post(this::speak);return;}}
        if(queue.current==null){completed();return;}
        if(!requestFocus()){failure("暂时无法取得音频播放权限，请结束其他通话或音频后重试");return;}
        previewing=false;wantPlay=true;state=SpeechQueue.State.PLAYING;message="正在朗读";utterance=queue.issue();savedOffset=queue.current.start;rangeStart=queue.current.start;rangeEnd=queue.current.end;if(!hold())return;remember();publish(true);
        int status=engine.speak(queue.current,rate,utterance);if(status!=TextToSpeech.SUCCESS){onSpeakFailure();return;}
        TextChunker.SpeechChunk ahead=queue.next;if(ahead!=null){boolean skip=!prefs.getBoolean("readTitles",true)&&isHeading(ahead)||prefs.getBoolean("skipPageNumbers",false)&&TextChunker.pageNumber(ahead);if(!skip&&(!queue.atChapterEnd()||prefs.getBoolean("autoNext",true)))engine.prefetch(prefs.getBoolean("skipUrls",false)?TextChunker.withoutUrls(ahead):ahead,rate);}
        final String id=utterance;speechTimeout=()->{if(id.equals(utterance)&&state==SpeechQueue.State.PLAYING)onSpeakFailure();};main.postDelayed(speechTimeout,Math.min(300000,Math.max(30000,(long)(queue.current.text.length()*600/rate))));
    }
    private boolean advanceChunk(){boolean boundary=queue.atChapterEnd();SpeechQueue.Completion completion=queue.finish(prefs.getBoolean("autoNext",true),sleep.due(System.currentTimeMillis(),boundary));retries=0;if(completion==SpeechQueue.Completion.TIMER){sleep.clear();rememberTimer();pause(true,"睡眠定时已结束");return false;}if(completion==SpeechQueue.Completion.CHAPTER_END){pause(true,"本章已听完");return false;}if(completion==SpeechQueue.Completion.END){completed();return false;}return true;}
    private boolean isHeading(TextChunker.SpeechChunk chunk){TextDocument.Chapter heading=queue.document.chapters.get(queue.document.chapterAt(chunk.start));if(heading.title.equals("开始阅读"))return false;int end=queue.document.text.indexOf('\n',heading.offset);return chunk.start>=heading.offset&&chunk.start<(end<0?queue.document.text.length():end);}
    private void onSpeakFailure(){if(retries++<1){engine.stop();speak();}else failure("语音引擎未能完成当前句，请重试或更换语音");}
    private void pause(boolean abandon,String reason){wantPlay=false;previewPending=false;previewing=false;cancelTimeout();if(engine!=null)engine.stop();if(queue!=null){queue.pause();savedOffset=queue.position();rangeStart=queue.current==null?-1:queue.current.start;rangeEnd=queue.current==null?-1:queue.current.end;}state=book!=null?SpeechQueue.State.PAUSED:SpeechQueue.State.IDLE;message=reason;if(abandon)resumeOnFocus=false;release(abandon);save(true);publish(true);if(book==null){stopForeground(true);foreground=false;stopSelf();}}
    private void completed(){pause(true,"本次朗读已结束");sleep.clear();rememberTimer();if(book!=null&&queue!=null){savedOffset=Math.max(0,queue.scopeEnd-1);repository.progress(book,savedOffset,true,e->storageError(e));remember();}}
    private void failure(String reason){wantPlay=false;resumeOnFocus=false;previewing=false;previewPending=false;cancelTimeout();if(engine!=null)engine.stop();if(queue!=null)queue.error();state=SpeechQueue.State.ERROR;message=reason;release(true);save(true);publish(true);}
    private void sentence(int direction){if(queue==null){resume();return;}engine.stop();queue.pause();if(direction<0)queue.previous(prefs.getBoolean("readTitles",true),prefs.getBoolean("skipPageNumbers",false),prefs.getBoolean("skipUrls",false));else queue.advance();retries=0;wantPlay=true;if(engine.ready())speak();else resume();}
    private void chapter(int direction){if(queue==null){resume();return;}engine.stop();queue.pause();queue.chapter(direction);retries=0;wantPlay=true;if(engine.ready())speak();else resume();}
    private void seek(int offset){if(queue==null){if(book!=null)load(book.id,offset,true);return;}engine.stop();queue.pause();if(offset>=queue.scopeEnd)queue.scopeEnd=queue.document.text.length();queue.seek(offset);retries=0;wantPlay=true;if(engine.ready())speak();else resume();}
    private void restartCurrent(){boolean playing=state==SpeechQueue.State.PLAYING;if(playing){engine.stop();queue.pause();retries=0;speak();}else publish(true);}
    private void preview(){if(book!=null)pause(true,"正在准备试听…");else{cancelTimeout();engine.stop();release(true);}if(!engine.ready()){previewPending=true;state=SpeechQueue.State.PREPARING;engine.init();publish(true);return;}if(!requestFocus()){failure("其他音频正在使用，无法试听");return;}previewing=true;state=SpeechQueue.State.PLAYING;utterance="preview:"+System.nanoTime();if(!hold())return;message="正在试听语音";TextChunker.SpeechChunk chunk=TextChunker.next("融雪阅读，愿每个故事都被温柔听见。",0,19,300);if(engine.speak(chunk,rate,utterance)!=TextToSpeech.SUCCESS){failure("无法试听当前语音");return;}final String id=utterance;speechTimeout=()->{if(previewing&&id.equals(utterance))failure("试听超时，请重试或更换语音");};main.postDelayed(speechTimeout,20000);publish(true);}
    private void save(boolean force){if(book==null||repository==null||!repository.owns(book.id))return;if(queue!=null)savedOffset=queue.position();remember();repository.progress(book,savedOffset,force,e->storageError(e));}
    private void storageError(Exception e){message="进度暂未保存，将在下一句重试："+e.getMessage();publish(false);}
    private void remember(){if(book!=null)prefs.edit().putString("book",book.id).putInt("offset",savedOffset).putBoolean("restorePending",true).apply();}
    private void rememberTimer(){prefs.edit().putInt("timerMode",sleep.mode).putLong("timerEnd",sleep.deadline).apply();}
    private boolean requestFocus(){if(Build.VERSION.SDK_INT>=26){if(focus==null)focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(focusListener,main).build();return audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}return audio.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
    private boolean hold(){try{wake.acquire(10*60*1000L);return true;}catch(Exception e){failure("无法保持后台播放："+e.getMessage());return false;}}
    private void release(boolean abandon){if(wake!=null&&wake.isHeld())try{wake.release();}catch(Exception ignored){}if(abandon&&audio!=null){if(Build.VERSION.SDK_INT>=26&&focus!=null)audio.abandonAudioFocusRequest(focus);else audio.abandonAudioFocus(focusListener);}}
    private void cancelTimeout(){if(speechTimeout!=null){main.removeCallbacks(speechTimeout);speechTimeout=null;}}
    void stopNow(){stopListening();}
    private void stopListening(){++loadingGeneration;save(true);if(repository!=null)repository.listenOwner="";wantPlay=false;resumeOnFocus=false;cancelTimeout();if(engine!=null)engine.close();if(queue!=null)queue.stop();queue=null;book=null;rangeStart=rangeEnd=-1;state=SpeechQueue.State.IDLE;message="已停止听书";sleep.clear();rememberTimer();prefs.edit().remove("book").remove("offset").apply();release(true);if(session!=null)session.setActive(false);stopForeground(true);foreground=false;publish(false);stopSelf();}
    private PendingIntent command(String action){Intent intent=new Intent(this,ListenService.class).setAction(action);int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;return Build.VERSION.SDK_INT>=26&&background?PendingIntent.getForegroundService(this,action.hashCode(),intent,flags):PendingIntent.getService(this,action.hashCode(),intent,flags);}
    private Notification notification(){
        String title=book==null?"融雪阅读听书":book.title,chapter=queue==null?"正在准备":queue.document.readingTitle(queue.position());
        Intent open=new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("listenBook",book==null?"":book.id).putExtra("showListen",true);
        PendingIntent content=PendingIntent.getActivity(this,701,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"listen"):new Notification.Builder(this);
        int icon=getResources().getIdentifier("ic_listen_notification","drawable",getPackageName());
        builder.setSmallIcon(icon).setContentTitle(title).setContentText(message.isEmpty()?chapter:message+" · "+chapter).setContentIntent(content).setOnlyAlertOnce(true).setOngoing(state==SpeechQueue.State.PLAYING||state==SpeechQueue.State.PREPARING).setVisibility(prefs.getBoolean("privacy",true)?Notification.VISIBILITY_PRIVATE:Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_TRANSPORT).setShowWhen(false);
        builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"上一章",command(PREV_CHAPTER)).build());builder.addAction(new Notification.Action.Builder(state==SpeechQueue.State.PLAYING?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,state==SpeechQueue.State.PLAYING?"暂停":"播放",command(state==SpeechQueue.State.PLAYING?PAUSE:PLAY)).build());builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"下一章",command(NEXT_CHAPTER)).build());builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"停止",command(STOP)).build());
        builder.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1,2));
        Notification.Builder generic=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"listen"):new Notification.Builder(this);generic.setSmallIcon(icon).setContentTitle("正在听书").setContentText("融雪阅读").setContentIntent(content);builder.setPublicVersion(generic.build());return builder.build();
    }
    private void publish(boolean notify){
        if(destroyed)return;if(session!=null&&notify){boolean hidden=prefs.getBoolean("privacy",true);session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,hidden?"正在听书":book==null?"融雪阅读":book.title).putString(MediaMetadata.METADATA_KEY_ARTIST,hidden?"融雪阅读":queue==null?"":queue.document.readingTitle(queue.position())).build());long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS;int code=state==SpeechQueue.State.PLAYING?PlaybackState.STATE_PLAYING:state==SpeechQueue.State.PREPARING?PlaybackState.STATE_BUFFERING:state==SpeechQueue.State.IDLE?PlaybackState.STATE_STOPPED:PlaybackState.STATE_PAUSED;session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(code,PlaybackState.PLAYBACK_POSITION_UNKNOWN,rate).build());session.setActive(book!=null);}
        if(notify&&foreground)try{((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION,notification());}catch(Exception ignored){}
        Snapshot snapshot=snapshot();for(Listener listener:new ArrayList<>(listeners))listener.changed(snapshot);
    }
    @Override public void onTaskRemoved(Intent root){if(!background){pause(true,"仅前台听书已暂停");stopSelf();}super.onTaskRemoved(root);}
    @Override public void onDestroy(){destroyed=true;++loadingGeneration;cancelTimeout();main.removeCallbacksAndMessages(null);save(true);if(repository!=null)repository.listenOwner="";if(engine!=null)engine.close();queue=null;book=null;release(true);try{unregisterReceiver(noisy);}catch(Exception ignored){}if(session!=null){session.setActive(false);session.release();}stopForeground(true);listeners.clear();super.onDestroy();}
}
