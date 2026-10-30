package com.snowread.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.*;
import android.speech.tts.*;
import java.util.*;

/** Engine callbacks are marshalled to the service main thread and scoped by initialization generation. */
final class TtsEngine {
    interface Callback {void ready();void start(String id);void range(String id,int start,int end);void done(String id);void error(String id,int code);void unavailable(String message,boolean network);}
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());private final Callback callback;
    private TextToSpeech tts;private int generation;private String activeId,cachedVoice="语音尚未就绪";private boolean ready;
    private final ArrayList<Voice> voices=new ArrayList<>();
    private Runnable timeout;
    private final SpeechPrecache precache;
    private Voice currentVoice;private boolean cacheConfigured;
    TtsEngine(Context context,Callback callback){this.context=context.getApplicationContext();this.callback=callback;precache=new SpeechPrecache(context);}
    void init(){
        close();final int token=++generation;android.content.SharedPreferences prefs=context.getSharedPreferences("listen",Context.MODE_PRIVATE);String engine=prefs.getString("engine","");
        timeout=()->{if(token==generation&&!ready){close();callback.unavailable("语音引擎初始化超时，请重试或更换引擎",false);}};main.postDelayed(timeout,8000);
        try{tts=new TextToSpeech(context,status->main.post(()->{
            if(token!=generation)return;main.removeCallbacks(timeout);
            if(status!=TextToSpeech.SUCCESS||tts==null){callback.unavailable("系统语音引擎初始化失败，请安装或设置语音引擎",false);return;}
            try{
                if(tts.getEngines()==null||tts.getEngines().isEmpty()){callback.unavailable("未安装系统语音引擎",false);return;}
                tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                Set<Voice> available=tts.getVoices();voices.clear();if(available!=null)for(Voice voice:available)if(!voice.isNetworkConnectionRequired()&&installed(voice))voices.add(voice);
                Collections.sort(voices,(a,b)->{int c=Boolean.compare(!a.getLocale().getLanguage().equals("zh"),!b.getLocale().getLanguage().equals("zh"));if(c==0)c=Boolean.compare(a.isNetworkConnectionRequired(),b.isNetworkConnectionRequired());return c==0?a.getName().compareTo(b.getName()):c;});
                String selected=prefs.getString("voice","");Voice chosen=null;for(Voice voice:voices)if(voice.getName().equals(selected))chosen=voice;
                if(chosen==null){for(Voice voice:voices)if(voice.getLocale().getLanguage().equals("zh")&&!voice.isNetworkConnectionRequired()&&installed(voice)){chosen=voice;break;}}
                if(chosen==null){Voice current=tts.getVoice();if(current!=null&&!current.isNetworkConnectionRequired()&&installed(current)&&current.getLocale().getLanguage().equals("zh"))chosen=current;}

                if(chosen==null){callback.unavailable("未找到可用语音，请安装系统语音数据",false);return;}
                if(!installed(chosen)){callback.unavailable("所选语音包尚未安装，请先安装系统语音数据",false);return;}
                if(chosen.isNetworkConnectionRequired()){callback.unavailable("本版仅使用离线语音，请安装离线语音包。",false);return;}
                if(tts.setVoice(chosen)!=TextToSpeech.SUCCESS){callback.unavailable("该语音尚不可用，请安装其语音数据或更换语音",false);return;}
                currentVoice=chosen;cachedVoice=chosen.getName();if(prefs.getBoolean("precache",true)){precache.configure(engine,chosen);cacheConfigured=true;}
                tts.setPitch(1f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    private void deliver(String id,Runnable action){main.post(()->{if(token==generation&&id!=null&&id.equals(activeId))action.run();});}
                    public void onStart(String id){deliver(id,()->callback.start(id));}
                    public void onDone(String id){deliver(id,()->callback.done(id));}
                    public void onError(String id){onError(id,TextToSpeech.ERROR);}
                    public void onError(String id,int code){deliver(id,()->callback.error(id,code));}
                    public void onRangeStart(String id,int start,int end,int frame){deliver(id,()->callback.range(id,start,end));}
                });ready=true;callback.ready();
            }catch(Exception e){callback.unavailable("语音引擎不可用："+e.getMessage(),false);}
        }),engine.isEmpty()?null:engine);}catch(Exception e){main.removeCallbacks(timeout);callback.unavailable("无法启动系统语音引擎："+e.getMessage(),false);}
    }
    boolean ready(){return ready&&tts!=null;}
    private boolean installed(Voice voice){return voice.getFeatures()==null||!voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);}
    int speak(TextChunker.SpeechChunk chunk,float rate,String id){if(!ready())return TextToSpeech.ERROR;activeId=id;final int token=generation;float speed=Math.max(.5f,Math.min(2f,rate));if(context.getSharedPreferences("listen",0).getBoolean("precache",true)&&precache.play(chunk,speed,new SpeechPrecache.Playback(){private boolean valid(){return token==generation&&id.equals(activeId);}public void started(){if(valid())callback.start(id);}public void finished(){if(valid())callback.done(id);}public void failed(){if(valid()&&speakNormal(chunk,speed,id)!=TextToSpeech.SUCCESS)callback.error(id,TextToSpeech.ERROR);}}))return TextToSpeech.SUCCESS;return speakNormal(chunk,speed,id);}
    private int speakNormal(TextChunker.SpeechChunk chunk,float rate,String id){try{tts.setSpeechRate(rate);return tts.speak(chunk.text,TextToSpeech.QUEUE_FLUSH,new Bundle(),id);}catch(Exception e){return TextToSpeech.ERROR;}}
    void prefetch(TextChunker.SpeechChunk chunk,float rate){if(context.getSharedPreferences("listen",0).getBoolean("precache",true)){if(!cacheConfigured&&currentVoice!=null&&ready()){precache.configure(context.getSharedPreferences("listen",0).getString("engine",""),currentVoice);cacheConfigured=true;}precache.prefetch(chunk,Math.max(.5f,Math.min(2f,rate)));}}
    void stop(){activeId=null;precache.stop();if(tts!=null)try{tts.stop();}catch(Exception ignored){}}
    String voiceName(){return cachedVoice;}
    ArrayList<Voice> voices(){return new ArrayList<>(voices);}
    List<TextToSpeech.EngineInfo> engines(){try{return tts==null?Collections.emptyList():tts.getEngines();}catch(Exception e){return Collections.emptyList();}}
    void close(){generation++;ready=false;activeId=null;precache.close();cacheConfigured=false;currentVoice=null;if(timeout!=null)main.removeCallbacks(timeout);if(tts!=null){try{tts.stop();tts.shutdown();}catch(Exception ignored){}tts=null;}}
}
