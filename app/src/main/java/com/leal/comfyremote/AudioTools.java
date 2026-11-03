package com.leal.comfyremote;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.Handler;
import android.widget.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.*;

/** Foreground-only capture and vault-backed previews shared by Fragments and Lipsync. */
final class AudioTools {
    static final int PICK=701,MIC=702;
    final Activity activity;final ReferencePreset.Store store;final Executor worker;final BooleanSupplier privateMode;
    Consumer<LipsyncAudio> importing;Runnable permission;Capture capture;MediaPlayer player;MediaDataSource playbackSource;Runnable playbackStopped=()->{};boolean closed;
    AudioTools(Activity a,ReferencePreset.Store store,Executor worker,BooleanSupplier privateMode){activity=a;this.store=store;this.worker=worker;this.privateMode=privateMode;}
    void choose(boolean library,Consumer<LipsyncAudio> result){
        stopPreview();FlowSheet s=new FlowSheet(activity,UiLanguage.t("Adicionar áudio"),"wave");
        s.note(UiLanguage.t("Importe um arquivo ou grave até 30 segundos. O áudio fica no cofre do app."));
        s.action(UiLanguage.t("Importar áudio"),"upload",()->{s.dialog.dismiss();importFile(result);});
        s.action(UiLanguage.t("Gravar com microfone"),"mic",()->{s.dialog.dismiss();record(result);});
        if(library)s.action(UiLanguage.t("Fragmentos de áudio"),"layers",()->{s.dialog.dismiss();library(result);});s.show();
    }
    void importFile(Consumer<LipsyncAudio> result){
        importing=result;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE);
        try{activity.startActivityForResult(intent,PICK);}catch(Exception e){importing=null;toast(UiLanguage.t("Nenhum seletor de áudio disponível."));}
    }
    boolean result(int request,int result,Intent data){
        if(request!=PICK)return false;Consumer<LipsyncAudio> callback=importing;importing=null;
        if(callback!=null&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null)worker.execute(()->{
            try{LipsyncAudio audio=LipsyncAudio.importFile(activity,data.getData());activity.runOnUiThread(()->{if(closed)LipsyncAudio.discard(audio);else callback.accept(audio);});}
            catch(Exception e){activity.runOnUiThread(()->{if(!closed)toast(e.getMessage());});}
        });return true;
    }
    void permission(int request,int[] grants){if(request!=MIC)return;Runnable pending=permission;permission=null;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){if(pending!=null&&!closed)pending.run();}else toast(UiLanguage.t("Microfone não autorizado. Você ainda pode importar um áudio."));}
    void record(Consumer<LipsyncAudio> result){if(capture!=null)capture.sheet.dialog.dismiss();stopPreview();capture=new Capture(result);capture.sheet.show();}
    void library(Consumer<LipsyncAudio> result){
        FlowSheet s=new FlowSheet(activity,UiLanguage.t("Fragmentos de áudio"),"wave");int count=0;
        for(ReferencePreset p:store.all())if(p.audio()&&(!p.hidden()||privateMode.getAsBoolean())&&!p.audioFile.isEmpty()){
            count++;s.action(p.name+" · "+seconds(p.audioMillis),"wave",()->{s.dialog.dismiss();copyFragment(p,result);});
        }
        if(count==0)s.note(UiLanguage.t("Salve áudios em Fragmentos para reutilizá-los aqui."));s.show();
    }
    void copyFragment(ReferencePreset p,Consumer<LipsyncAudio> callback){
        if(p.hidden()&&!privateMode.getAsBoolean())return;
        worker.execute(()->{LipsyncAudio audio=null;try{
            File file=LipsyncAudio.file(activity.getFilesDir(),"draft_lipsync_"+UUID.randomUUID());audio=new LipsyncAudio(file,p.audioMillis,p.name);
            LipsyncAudio.copy(store.file(p.audioFile),file);LipsyncAudio checked=LipsyncAudio.read(file,p.name);
            activity.runOnUiThread(()->{if(closed)LipsyncAudio.discard(checked);else callback.accept(checked);});
        }catch(Exception e){LipsyncAudio.discard(audio);activity.runOnUiThread(()->{if(!closed)toast(UiLanguage.t("Áudio indisponível. Importe novamente."));});}});
    }
    void preview(LipsyncAudio audio,Runnable stopped){
        stopPreview();playbackStopped=stopped;
        try{playbackSource=VaultFiles.media(audio.file);player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());player.setDataSource(playbackSource);player.setOnPreparedListener(p->{if(p==player)p.start();});player.setOnCompletionListener(p->stopPreview());player.setOnErrorListener((p,w,e)->{stopPreview();toast(UiLanguage.t("Não foi possível reproduzir o áudio."));return true;});player.prepareAsync();}
        catch(Exception e){stopPreview();toast(UiLanguage.t("Não foi possível reproduzir o áudio."));}
    }
    void stopPreview(){if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}VaultFiles.close(playbackSource);playbackSource=null;Runnable stopped=playbackStopped;playbackStopped=()->{};stopped.run();}
    void pause(){stopPreview();if(capture!=null&&capture.started)capture.sheet.dialog.dismiss();}
    void close(){closed=true;importing=null;permission=null;if(capture!=null)capture.sheet.dialog.dismiss();stopPreview();}
    void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
    static String seconds(long millis){return String.format(Locale.getDefault(),"%.1f s",millis/1000.0);}
    static LipsyncAudio savePcm(Activity a,byte[] pcm)throws Exception{
        if(pcm.length<6400||pcm.length>960000||(pcm.length&1)!=0)throw new IOException(UiLanguage.t("Escolha um áudio de 0,2 a 30 segundos."));
        File file=LipsyncAudio.file(a.getFilesDir(),"draft_lipsync_"+UUID.randomUUID());
        ByteBuffer header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);header.put("RIFF".getBytes("US-ASCII")).putInt(pcm.length+36).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(pcm.length);
        try{try(OutputStream out=VaultFiles.output(file)){out.write(header.array());out.write(pcm);}return LipsyncAudio.read(file,UiLanguage.t("Gravação de áudio"));}catch(Exception error){VaultFiles.delete(file);throw error;}
    }
    final class Capture {
        final FlowSheet sheet;final TextView timer;final ProgressBar level;Button record,play,use;final Consumer<LipsyncAudio> callback;
        volatile boolean running,discard;boolean started,disposed,transferred;AudioRecord recorder;LipsyncAudio audio;
        Capture(Consumer<LipsyncAudio> callback){
            this.callback=callback;sheet=new FlowSheet(activity,UiLanguage.t("Gravar áudio"),"mic");sheet.note(UiLanguage.t("Até 30 segundos. A gravação para ao sair desta tela ou do app."));
            timer=sheet.text("0.0 / 30 s",32,true);timer.setGravity(17);sheet.body.addView(timer);sheet.gap(sheet.body,18);
            level=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);level.setMax(100);sheet.body.addView(level,new LinearLayout.LayoutParams(-1,sheet.dp(4)));sheet.gap(sheet.body,20);
            record=sheet.action(UiLanguage.t("Começar gravação"),"mic",()->{if(running){finish();return;}if(started)return;
                if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)start();
                else{permission=()->{if(!disposed&&sheet.dialog.isShowing())start();};activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);}
            });
            play=sheet.action(UiLanguage.t("Ouvir gravação"),"play",()->{if(player!=null){stopPreview();return;}if(audio!=null){play.setText(UiLanguage.t("Parar áudio"));preview(audio,()->play.setText(UiLanguage.t("Ouvir gravação")));}});play.setEnabled(false);play.setAlpha(.4f);
            use=sheet.action(UiLanguage.t("Usar gravação"),"check",()->{if(audio==null)return;transferred=true;stopPreview();sheet.dialog.dismiss();callback.accept(audio);});use.setEnabled(false);use.setAlpha(.4f);
            sheet.dialog.setOnDismissListener(d->{disposed=true;discard=true;running=false;permission=null;stopRecorder();stopPreview();if(!transferred)LipsyncAudio.discard(audio);if(capture==this)capture=null;});
        }
        void start(){
            stopPreview();LipsyncAudio.discard(audio);audio=null;discard=false;
            try{int buffer=Math.max(4096,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer);
                if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException();recorder.startRecording();if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException();
                started=running=true;record.setText(UiLanguage.t("Parar gravação"));FlowDesign.labelIcon(record,"stop");play.setEnabled(false);use.setEnabled(false);play.setAlpha(.4f);use.setAlpha(.4f);new Thread(this::read,"bwdacc-microphone").start();
            }catch(Exception e){stopRecorder();if(recorder!=null){recorder.release();recorder=null;}started=false;toast(UiLanguage.t("Microfone indisponível. Confira a permissão e tente novamente."));}
        }
        void finish(){running=false;stopRecorder();record.setEnabled(false);}
        void stopRecorder(){try{if(recorder!=null)recorder.stop();}catch(Exception ignored){}}
        void read(){
            ByteArrayOutputStream pcm=new ByteArrayOutputStream(960000);byte[] buffer=new byte[2048];LipsyncAudio saved=null;Exception failure=null;
            try{while(running&&pcm.size()<960000){int n=recorder.read(buffer,0,Math.min(buffer.length,960000-pcm.size()));if(n<0){if(running)throw new IOException();break;}if(n==0)continue;pcm.write(buffer,0,n);int peak=0;for(int i=0;i+1<n;i+=2)peak=Math.max(peak,Math.abs((short)((buffer[i]&255)|(buffer[i+1]<<8))));final int meter=Math.min(100,peak*100/12000),length=pcm.size();activity.runOnUiThread(()->{if(!disposed){timer.setText(seconds(length/32)+UiLanguage.t(" / 30 s"));level.setProgress(meter);}});}
                if(!discard){byte[] bytes=pcm.toByteArray();try{saved=savePcm(activity,bytes);}finally{Arrays.fill(bytes,(byte)0);}}
            }catch(Exception e){failure=e;}finally{running=false;stopRecorder();if(recorder!=null){recorder.release();recorder=null;}Arrays.fill(buffer,(byte)0);pcm.reset();}
            final LipsyncAudio result=saved;final Exception error=failure;activity.runOnUiThread(()->{started=false;if(disposed||closed){LipsyncAudio.discard(result);return;}audio=result;record.setEnabled(true);record.setText(UiLanguage.t("Gravar novamente"));FlowDesign.labelIcon(record,"mic");level.setProgress(0);play.setEnabled(result!=null);use.setEnabled(result!=null);play.setAlpha(result==null?.4f:1);use.setAlpha(result==null?.4f:1);if(error!=null)toast(error.getMessage()==null?UiLanguage.t("Não foi possível gravar o áudio."):error.getMessage());});
        }
    }
}
