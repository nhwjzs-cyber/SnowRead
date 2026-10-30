package com.snowread.app;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.io.*;

/** One silent synthesis client, one upcoming file, one playing file. Never owns reading progress. */
final class SpeechPrecache {
    interface Playback {void started();void finished();void failed();}
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private TextToSpeech synth;private MediaPlayer player;private File playing;
    private int generation,sequence;private boolean ready,disabled;
    private String engine="",voice="";private Job job;private Runnable initTimeout,jobTimeout;
    private static final class Job {final String id,text;final float rate;File file;boolean complete;Job(String id,String text,float rate){this.id=id;this.text=text;this.rate=rate;}}
    SpeechPrecache(Context context){this.context=context.getApplicationContext();}
    void configure(String engine,Voice voice){close();if(voice==null||voice.isNetworkConnectionRequired())return;this.engine=engine;this.voice=voice.getName();int token=++generation;
        File home=new File(context.getCacheDir(),"listen-ahead");if(!home.isDirectory()&&!home.mkdirs()){disabled=true;return;}File[] stale=home.listFiles();if(stale!=null)for(File file:stale)if(file.isFile()&&file.getName().startsWith("ahead-"))file.delete();
        initTimeout=()->{if(token==generation&&!ready)disable();};main.postDelayed(initTimeout,8000);
        try{synth=new TextToSpeech(context,status->main.post(()->{if(token!=generation)return;main.removeCallbacks(initTimeout);try{if(status!=TextToSpeech.SUCCESS||synth==null||synth.setVoice(voice)!=TextToSpeech.SUCCESS){disable();return;}synth.setPitch(1f);synth.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){main.post(()->{if(token!=generation||job==null||!job.id.equals(id))return;if(jobTimeout!=null)main.removeCallbacks(jobTimeout);if(job.file==null||job.file.length()<44||job.file.length()>8*1024*1024){disable();return;}job.complete=true;});}public void onError(String id){onError(id,-1);}public void onError(String id,int error){main.post(()->{if(token==generation&&job!=null&&job.id.equals(id))disable();});}});ready=true;if(job!=null)render(job);}catch(Exception e){disable();}}),engine.isEmpty()?null:engine);}catch(Exception e){disable();}
    }
    private boolean matches(Job j,String text,float rate){return j!=null&&j.text.equals(text)&&Float.floatToIntBits(j.rate)==Float.floatToIntBits(rate);}
    void prefetch(TextChunker.SpeechChunk chunk,float rate){if(disabled||chunk==null||matches(job,chunk.text,rate))return;cancelJob();job=new Job("ahead:"+(++sequence),chunk.text,rate);if(ready)render(job);}
    private void render(Job wanted){if(synth==null||job!=wanted||disabled)return;try{wanted.file=File.createTempFile("ahead-",".wav",new File(context.getCacheDir(),"listen-ahead"));synth.setSpeechRate(wanted.rate);if(synth.synthesizeToFile(wanted.text,new Bundle(),wanted.file,wanted.id)!=TextToSpeech.SUCCESS){disable();return;}jobTimeout=()->{if(job==wanted)disable();};main.postDelayed(jobTimeout,15000);}catch(Exception e){disable();}}
    boolean play(TextChunker.SpeechChunk chunk,float rate,Playback callbacks){Job cached=job;if(disabled||cached==null||!cached.complete||!matches(cached,chunk.text,rate))return false;job=null;if(jobTimeout!=null)main.removeCallbacks(jobTimeout);stopPlayer();MediaPlayer media=new MediaPlayer();player=media;playing=cached.file;
        try{media.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());media.setDataSource(playing.getAbsolutePath());media.setOnPreparedListener(p->{if(player!=p)return;try{p.start();callbacks.started();}catch(Exception e){stopPlayer();disable();callbacks.failed();}});media.setOnCompletionListener(p->{if(player!=p)return;stopPlayer();callbacks.finished();});media.setOnErrorListener((p,what,extra)->{if(player!=p)return true;stopPlayer();disable();callbacks.failed();return true;});media.prepareAsync();return true;}catch(Exception e){stopPlayer();disable();return false;}
    }
    private void cancelJob(){if(jobTimeout!=null)main.removeCallbacks(jobTimeout);Job old=job;job=null;if(synth!=null&&ready)try{synth.stop();}catch(Exception ignored){}if(old!=null&&old.file!=null)old.file.delete();}
    private void stopPlayer(){MediaPlayer old=player;player=null;if(old!=null)try{old.release();}catch(Exception ignored){}if(playing!=null){playing.delete();playing=null;}}
    private void disable(){disabled=true;cancelJob();}
    void stop(){cancelJob();stopPlayer();}
    void close(){generation++;ready=false;disabled=false;if(initTimeout!=null)main.removeCallbacks(initTimeout);stop();if(synth!=null){try{synth.stop();synth.shutdown();}catch(Exception ignored){}synth=null;}}
}
